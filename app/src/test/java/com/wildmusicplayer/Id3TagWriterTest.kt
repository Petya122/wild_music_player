package com.wildmusicplayer

import com.wildmusicplayer.data.Id3TagWriter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class Id3TagWriterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val audio = ByteArray(5000) { (it * 31 + 7).toByte() }
    private val newTags = Id3TagWriter.Tags("Think About the Way", "Ice MC", "Ice'n'Green", null)

    // ---- tiny MP3 builders ----

    private fun int(v: Int) = byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())
    private fun syncsafe(v: Int) = byteArrayOf(((v shr 21) and 0x7F).toByte(), ((v shr 14) and 0x7F).toByte(), ((v shr 7) and 0x7F).toByte(), (v and 0x7F).toByte())

    private fun frame(id: String, payload: ByteArray, version: Int) =
        id.toByteArray() + (if (version == 4) syncsafe(payload.size) else int(payload.size)) + byteArrayOf(0, 0) + payload

    private fun latin1(id: String, text: String, version: Int = 3) = frame(id, byteArrayOf(0) + text.toByteArray(Charsets.ISO_8859_1), version)

    private fun mp3(version: Int, frames: ByteArray, flags: Int = 0, padding: Int = 64, v1: ByteArray? = null): ByteArray {
        val body = frames + ByteArray(padding)
        return "ID3".toByteArray() + byteArrayOf(version.toByte(), 0, flags.toByte()) + syncsafe(body.size) + body + audio + (v1 ?: ByteArray(0))
    }

    private fun v1Tag(title: String) = ByteArray(128).also { tag ->
        "TAG".toByteArray().copyInto(tag)
        title.toByteArray().copyInto(tag, 3)
    }

    private fun write(bytes: ByteArray): File = tmp.newFile().apply { writeBytes(bytes) }

    private fun edit(input: File, tags: Id3TagWriter.Tags = newTags): Pair<Boolean, File> {
        val out = tmp.newFile()
        return Id3TagWriter.writeTags(input, out, tags) to out
    }

    /** The bytes after the ID3v2 tag (and before an ID3v1 tag of [v1Size] bytes). */
    private fun audioOf(file: File, v1Size: Int = 0): ByteArray {
        val bytes = file.readBytes()
        val size = ((bytes[6].toInt() and 0x7F) shl 21) or ((bytes[7].toInt() and 0x7F) shl 14) or
            ((bytes[8].toInt() and 0x7F) shl 7) or (bytes[9].toInt() and 0x7F)
        return bytes.copyOfRange(10 + size, bytes.size - v1Size)
    }

    @Test
    fun replacesNamesKeepsOtherFramesAndTheAudio() {
        val original = latin1("TIT2", "Track 01") + latin1("TPE1", "Unknown") + latin1("TRCK", "3") +
            latin1("TYER", "1994") + frame("APIC", ByteArray(200) { 1 }, 3)
        val (ok, out) = edit(write(mp3(3, original)))
        assertTrue(ok)
        val frames = Id3TagWriter.readFrames(out)
        assertEquals("Think About the Way", frames["TIT2"])
        assertEquals("Ice MC", frames["TPE1"])
        assertEquals("Ice'n'Green", frames["TALB"])
        assertEquals("3", frames["TRCK"])
        assertEquals("1994", frames["TYER"])
        assertEquals("200 bytes", frames["APIC"])
        assertArrayEquals(audio, audioOf(out))
        assertEquals(3, out.readBytes()[3].toInt())
    }

    @Test
    fun newCoverReplacesTheOldOne() {
        val (ok, out) = edit(
            write(mp3(3, latin1("TIT2", "x") + frame("APIC", ByteArray(200), 3))),
            Id3TagWriter.Tags("a", "b", "c", ByteArray(500) { 9 })
        )
        assertTrue(ok)
        // 500 bytes of picture + encoding, "image/jpeg\0", type and empty description
        assertEquals("514 bytes", Id3TagWriter.readFrames(out)["APIC"])
    }

    @Test
    fun version24StaysVersion24WithUtf8() {
        val (ok, out) = edit(write(mp3(4, latin1("TIT2", "Old", 4) + latin1("TCON", "Dance", 4))), Id3TagWriter.Tags("Árvíztűrő", "Ice MC", "Album", null))
        assertTrue(ok)
        assertEquals(4, out.readBytes()[3].toInt())
        val frames = Id3TagWriter.readFrames(out)
        assertEquals("Árvíztűrő", frames["TIT2"])
        assertEquals("Dance", frames["TCON"])
        assertArrayEquals(audio, audioOf(out))
    }

    @Test
    fun fileWithoutTagGetsOne() {
        val (ok, out) = edit(write(audio))
        assertTrue(ok)
        assertEquals("Ice MC", Id3TagWriter.readFrames(out)["TPE1"])
        assertArrayEquals(audio, audioOf(out))
    }

    @Test
    fun id3v1AtTheEndIsUpdatedToo() {
        val (ok, out) = edit(write(mp3(3, latin1("TIT2", "Old"), v1 = v1Tag("Old title"))))
        assertTrue(ok)
        val bytes = out.readBytes()
        val v1 = bytes.copyOfRange(bytes.size - 128, bytes.size)
        assertEquals("TAG", String(v1, 0, 3))
        assertEquals("Think About the Way", String(v1, 3, 30).trimEnd('\u0000'))
        assertEquals("Ice MC", String(v1, 33, 30).trimEnd('\u0000'))
        assertArrayEquals(audio, audioOf(out, v1Size = 128))
    }

    @Test
    fun unsynchronisedTagIsLeftAlone() {
        val (ok, _) = edit(write(mp3(3, latin1("TIT2", "Old"), flags = 0x80)))
        assertFalse(ok)
    }

    @Test
    fun editingTwiceKeepsWorking() {
        val (_, first) = edit(write(mp3(3, latin1("TIT2", "Old") + latin1("TRCK", "7"))))
        val (ok, second) = edit(first, Id3TagWriter.Tags("Second", "Artist", "Album", null))
        assertTrue(ok)
        val frames = Id3TagWriter.readFrames(second)
        assertEquals("Second", frames["TIT2"])
        assertEquals("7", frames["TRCK"])
        assertArrayEquals(audio, audioOf(second))
    }
}
