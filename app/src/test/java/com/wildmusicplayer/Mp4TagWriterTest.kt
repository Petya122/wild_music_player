package com.wildmusicplayer

import com.wildmusicplayer.data.Mp4TagWriter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class Mp4TagWriterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val tags = Mp4TagWriter.Tags(
        title = "Bohemian Rhapsody",
        artist = "Queen",
        album = "Bohemian Rhapsody",
        coverJpeg = ByteArray(300) { it.toByte() }
    )

    // ---- tiny MP4 builders ----

    private fun int(v: Int) = byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())
    private fun box(type: String, payload: ByteArray) =
        int(8 + payload.size) + type.toByteArray(Charsets.ISO_8859_1) + payload

    private val ftyp = box("ftyp", "M4A ".toByteArray() + int(0) + "M4A isom".toByteArray())

    private fun stco(vararg offsets: Int) =
        box("stco", int(0) + int(offsets.size) + offsets.fold(ByteArray(0)) { acc, o -> acc + int(o) })

    private fun regularMoov(offset1: Int, offset2: Int) = box(
        "moov",
        box("mvhd", ByteArray(100)) +
            box("trak", box("mdia", box("minf", box("stbl", stco(offset1, offset2)))))
    )

    /** ftyp, moov (stco → "AAAA" and "BBBB" inside mdat), mdat. */
    private fun regularMp4(): ByteArray {
        val moovSize = regularMoov(0, 0).size
        val payloadStart = ftyp.size + moovSize + 8
        return ftyp + regularMoov(payloadStart, payloadStart + 4) + box("mdat", "AAAABBBB".toByteArray())
    }

    private fun fragmentedMp4(tfhdFlags: Int = 0x020000, withMfra: Boolean = false): ByteArray {
        val moov = box("moov", box("mvhd", ByteArray(100)) + box("mvex", box("trex", ByteArray(24))))
        val tfhdPayload = int(tfhdFlags) + int(1) +
            (if (tfhdFlags and 0x1 != 0) ByteArray(8) else ByteArray(0))
        val moof = box("moof", box("mfhd", ByteArray(8)) + box("traf", box("tfhd", tfhdPayload)))
        val mfra = if (withMfra) box("mfra", box("mfro", ByteArray(8))) else ByteArray(0)
        return ftyp + moov + box("sidx", ByteArray(32)) + moof + box("mdat", "CCCC".toByteArray()) + mfra
    }

    private fun write(bytes: ByteArray): File = tmp.newFile().apply { writeBytes(bytes) }

    private fun tag(input: File, t: Mp4TagWriter.Tags = tags): Pair<Boolean, File> {
        val out = tmp.newFile()
        return Mp4TagWriter.writeTags(input, out, t) to out
    }

    private fun findStco(bytes: ByteArray, start: Int = 0, end: Int = bytes.size): IntArray? {
        for (b in Mp4TagWriter.parseBoxes(bytes, start, end)) {
            if (b.type == "stco") {
                val c = b.contentStart.toInt()
                val count = readInt(bytes, c + 4)
                return IntArray(count) { readInt(bytes, c + 8 + it * 4) }
            }
            if (b.type in setOf("moov", "trak", "mdia", "minf", "stbl")) {
                findStco(bytes, b.contentStart.toInt(), b.end.toInt())?.let { return it }
            }
        }
        return null
    }

    private fun readInt(b: ByteArray, p: Int) =
        ((b[p].toInt() and 0xFF) shl 24) or ((b[p + 1].toInt() and 0xFF) shl 16) or
            ((b[p + 2].toInt() and 0xFF) shl 8) or (b[p + 3].toInt() and 0xFF)

    // ---- tests ----

    @Test
    fun regularMp4GetsTagsAndChunkOffsetsStillPointAtTheirData() {
        val (ok, out) = tag(write(regularMp4()))
        assertTrue(ok)

        val read = Mp4TagWriter.readTags(out)
        assertEquals("Bohemian Rhapsody", read["©nam"])
        assertEquals("Queen", read["©ART"])
        assertEquals("Bohemian Rhapsody", read["©alb"])
        assertEquals("300 bytes", read["covr"])

        val bytes = out.readBytes()
        val offsets = findStco(bytes)!!
        assertEquals("AAAA", String(bytes, offsets[0], 4))
        assertEquals("BBBB", String(bytes, offsets[1], 4))
    }

    @Test
    fun fragmentedMp4KeepsEverythingAfterMoovUnchanged() {
        val input = fragmentedMp4()
        val (ok, out) = tag(write(input))
        assertTrue(ok)
        assertEquals("Queen", Mp4TagWriter.readTags(out)["©ART"])

        val inBoxes = Mp4TagWriter.parseBoxes(input, 0, input.size)
        val outBytes = out.readBytes()
        val outBoxes = Mp4TagWriter.parseBoxes(outBytes, 0, outBytes.size)
        val inTail = input.copyOfRange(inBoxes.first { it.type == "moov" }.end.toInt(), input.size)
        val outTail = outBytes.copyOfRange(outBoxes.first { it.type == "moov" }.end.toInt(), outBytes.size)
        assertArrayEquals(inTail, outTail)
    }

    @Test
    fun fragmentWithAbsoluteOffsetsIsNotTouched() {
        val (ok, _) = tag(write(fragmentedMp4(tfhdFlags = 0x000001)))
        assertFalse(ok)
    }

    @Test
    fun mfraIsDroppedWhenMoovGrows() {
        val (ok, out) = tag(write(fragmentedMp4(withMfra = true)))
        assertTrue(ok)
        val bytes = out.readBytes()
        assertTrue(Mp4TagWriter.parseBoxes(bytes, 0, bytes.size).none { it.type == "mfra" })
    }

    @Test
    fun retaggingReplacesOldTagsAndKeepsOffsetsValid() {
        val (_, first) = tag(write(regularMp4()))
        val (ok, second) = tag(first, Mp4TagWriter.Tags("New Title", "New Artist", "New Album", null))
        assertTrue(ok)

        val read = Mp4TagWriter.readTags(second)
        assertEquals("New Title", read["©nam"])
        assertEquals("New Artist", read["©ART"])
        assertFalse(read.containsKey("covr"))

        val bytes = second.readBytes()
        val offsets = findStco(bytes)!!
        assertEquals("AAAA", String(bytes, offsets[0], 4))
    }

    @Test
    fun nonMp4IsRejected() {
        val (ok, _) = tag(write(ByteArray(500) { (it * 37).toByte() }))
        assertFalse(ok)
    }

    @Test
    fun unicodeTagsRoundTrip() {
        val (ok, out) = tag(write(fragmentedMp4()), Mp4TagWriter.Tags("Ébredés – Ősz", "Bëyoncé", "Ősz", null))
        assertTrue(ok)
        assertEquals("Ébredés – Ősz", Mp4TagWriter.readTags(out)["©nam"])
    }
}
