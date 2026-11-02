package com.wildmusicplayer.data

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile

/**
 * Writes iTunes-style tags (`moov/udta/meta/ilst`: title, artist, album, cover) into an MP4/M4A file,
 * the format Android's MediaStore, lock screen and Bluetooth metadata read.
 *
 * Works for YouTube's fragmented M4A (sample data in `moof`/`mdat` pairs addressed relative to each
 * `moof`, so growing `moov` is safe) and for regular MP4 (chunk offsets in `stco`/`co64` are patched).
 * Files it can't tag safely are left alone (returns false).
 */
object Mp4TagWriter {

    class Tags(
        val title: String,
        val artist: String,
        val album: String,
        val coverJpeg: ByteArray?
    )

    internal class Box(val type: String, val offset: Long, val size: Long, val headerSize: Int) {
        val end: Long get() = offset + size
        val contentStart: Long get() = offset + headerSize
    }

    private val chunkOffsetContainers = setOf("trak", "mdia", "minf", "stbl")

    /** Writes [input] with [tags] to [output]. Returns false (and writes nothing useful) if it can't. */
    fun writeTags(input: File, output: File, tags: Tags): Boolean = try {
        RandomAccessFile(input, "r").use { raf -> writeTagsInternal(raf, output, tags) }
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }

    private fun writeTagsInternal(raf: RandomAccessFile, output: File, tags: Tags): Boolean {
        val boxes = readTopLevelBoxes(raf) ?: return false
        val moov = boxes.firstOrNull { it.type == "moov" } ?: return false
        if (moov.size > Int.MAX_VALUE) return false

        // A fragment that addresses its data by absolute file offset would break if moov changes size
        if (boxes.any { it.type == "moof" && it.offset > moov.offset && hasAbsoluteBaseOffset(raf, it) }) {
            return false
        }

        val moovBytes = ByteArray(moov.size.toInt())
        raf.seek(moov.offset)
        raf.readFully(moovBytes)

        val newMoov = rebuildMoov(moovBytes, moov.headerSize, tags)
        val delta = newMoov.size - moov.size
        if (delta != 0L && !patchChunkOffsets(newMoov, 8, newMoov.size, moov.end, delta)) {
            return false
        }

        output.outputStream().buffered().use { out ->
            for (box in boxes) {
                when {
                    box === moov -> out.write(newMoov)
                    // mfra only holds absolute fragment positions for seeking and is optional;
                    // drop it rather than leave it pointing at the wrong place
                    box.type == "mfra" && delta != 0L -> Unit
                    else -> copyRange(raf, box.offset, box.size, out)
                }
            }
        }
        return true
    }

    /** Reads the text tags back (©nam, ©ART, ©alb) and whether a cover is present. Used by tests. */
    internal fun readTags(file: File): Map<String, String> {
        val bytes = file.readBytes()
        val result = mutableMapOf<String, String>()
        val moov = parseBoxes(bytes, 0, bytes.size).firstOrNull { it.type == "moov" } ?: return result
        val udta = parseBoxes(bytes, moov.contentStart.toInt(), moov.end.toInt()).firstOrNull { it.type == "udta" } ?: return result
        val meta = parseBoxes(bytes, udta.contentStart.toInt(), udta.end.toInt()).firstOrNull { it.type == "meta" } ?: return result
        val ilst = parseBoxes(bytes, meta.contentStart.toInt() + 4, meta.end.toInt()).firstOrNull { it.type == "ilst" } ?: return result
        for (item in parseBoxes(bytes, ilst.contentStart.toInt(), ilst.end.toInt())) {
            val data = parseBoxes(bytes, item.contentStart.toInt(), item.end.toInt()).firstOrNull { it.type == "data" } ?: continue
            val payloadStart = data.contentStart.toInt() + 8
            result[item.type] = if (item.type == "covr") {
                "${data.end.toInt() - payloadStart} bytes"
            } else {
                String(bytes, payloadStart, data.end.toInt() - payloadStart, Charsets.UTF_8)
            }
        }
        return result
    }

    // ---- moov rebuilding ----

    private fun rebuildMoov(moovBytes: ByteArray, headerSize: Int, tags: Tags): ByteArray {
        val children = parseBoxes(moovBytes, headerSize, moovBytes.size)
        val content = ByteArrayOutputStream()
        var udtaWritten = false
        for (child in children) {
            if (child.type == "udta") {
                // Keep other user data, replace any existing tags
                val udtaContent = ByteArrayOutputStream()
                parseBoxes(moovBytes, child.contentStart.toInt(), child.end.toInt())
                    .filter { it.type != "meta" }
                    .forEach { udtaContent.write(moovBytes, it.offset.toInt(), it.size.toInt()) }
                udtaContent.write(metaBox(tags))
                content.write(box("udta", udtaContent.toByteArray()))
                udtaWritten = true
            } else {
                content.write(moovBytes, child.offset.toInt(), child.size.toInt())
            }
        }
        if (!udtaWritten) {
            content.write(box("udta", metaBox(tags)))
        }
        return box("moov", content.toByteArray())
    }

    private fun metaBox(tags: Tags): ByteArray {
        val hdlr = box(
            "hdlr",
            ByteArray(8) + "mdir".toByteArray(Charsets.ISO_8859_1) +
                "appl".toByteArray(Charsets.ISO_8859_1) + ByteArray(8) + ByteArray(1)
        )
        val ilst = ByteArrayOutputStream().apply {
            write(textItem("©nam", tags.title))
            write(textItem("©ART", tags.artist))
            write(textItem("©alb", tags.album))
            tags.coverJpeg?.let { write(dataItem("covr", DATA_TYPE_JPEG, it)) }
        }
        // meta is a "full box": 4 bytes of version/flags before its children
        return box("meta", ByteArray(4) + hdlr + box("ilst", ilst.toByteArray()))
    }

    private fun textItem(type: String, value: String) =
        dataItem(type, DATA_TYPE_UTF8, value.toByteArray(Charsets.UTF_8))

    private fun dataItem(type: String, dataType: Int, payload: ByteArray) =
        box(type, box("data", intBytes(dataType) + intBytes(0) + payload))

    private fun box(type: String, payload: ByteArray): ByteArray =
        intBytes(8 + payload.size) + type.toByteArray(Charsets.ISO_8859_1) + payload

    // ---- offsets ----

    /** Adds [delta] to every stco/co64 chunk offset that pointed past the old moov. */
    private fun patchChunkOffsets(buf: ByteArray, start: Int, end: Int, oldMoovEnd: Long, delta: Long): Boolean {
        for (child in parseBoxes(buf, start, end)) {
            val content = child.contentStart.toInt()
            when (child.type) {
                in chunkOffsetContainers -> {
                    if (!patchChunkOffsets(buf, content, child.end.toInt(), oldMoovEnd, delta)) return false
                }
                "stco" -> {
                    val count = readInt(buf, content + 4)
                    for (i in 0 until count) {
                        val pos = content + 8 + i * 4
                        val value = readInt(buf, pos).toLong() and 0xFFFFFFFFL
                        if (value >= oldMoovEnd) {
                            val patched = value + delta
                            if (patched < 0 || patched > 0xFFFFFFFFL) return false
                            writeInt(buf, pos, patched.toInt())
                        }
                    }
                }
                "co64" -> {
                    val count = readInt(buf, content + 4)
                    for (i in 0 until count) {
                        val pos = content + 8 + i * 8
                        val value = readLong(buf, pos)
                        if (value >= oldMoovEnd) writeLong(buf, pos, value + delta)
                    }
                }
            }
        }
        return true
    }

    private fun hasAbsoluteBaseOffset(raf: RandomAccessFile, moof: Box): Boolean {
        if (moof.size > 16 * 1024 * 1024) return true
        val bytes = ByteArray(moof.size.toInt())
        raf.seek(moof.offset)
        raf.readFully(bytes)
        for (traf in parseBoxes(bytes, moof.headerSize, bytes.size).filter { it.type == "traf" }) {
            val tfhd = parseBoxes(bytes, traf.contentStart.toInt(), traf.end.toInt())
                .firstOrNull { it.type == "tfhd" } ?: continue
            val flags = readInt(bytes, tfhd.contentStart.toInt()) and 0x00FFFFFF
            if (flags and TFHD_BASE_DATA_OFFSET_PRESENT != 0) return true
        }
        return false
    }

    // ---- box parsing ----

    private fun readTopLevelBoxes(raf: RandomAccessFile): List<Box>? {
        val length = raf.length()
        val boxes = mutableListOf<Box>()
        var pos = 0L
        val header = ByteArray(16)
        while (pos < length) {
            if (length - pos < 8) return null
            raf.seek(pos)
            raf.readFully(header, 0, 8)
            var size = readInt(header, 0).toLong() and 0xFFFFFFFFL
            val type = boxType(header, 4) ?: return null
            var headerSize = 8
            when (size) {
                1L -> {
                    if (length - pos < 16) return null
                    raf.readFully(header, 8, 8)
                    size = readLong(header, 8)
                    headerSize = 16
                }
                0L -> size = length - pos
            }
            if (size < headerSize || pos + size > length) return null
            boxes += Box(type, pos, size, headerSize)
            pos += size
        }
        return boxes
    }

    internal fun parseBoxes(buf: ByteArray, start: Int, end: Int): List<Box> {
        val boxes = mutableListOf<Box>()
        var pos = start
        while (pos + 8 <= end) {
            var size = readInt(buf, pos).toLong() and 0xFFFFFFFFL
            val type = boxType(buf, pos + 4) ?: throw IllegalArgumentException("Invalid box type at $pos")
            var headerSize = 8
            when (size) {
                1L -> {
                    size = readLong(buf, pos + 8)
                    headerSize = 16
                }
                0L -> size = (end - pos).toLong()
            }
            if (size < headerSize || pos + size > end) throw IllegalArgumentException("Invalid box size at $pos")
            boxes += Box(type, pos.toLong(), size, headerSize)
            pos += size.toInt()
        }
        return boxes
    }

    // Box types are 4 bytes of Latin-1 ("moov", "©nam"); anything else means this isn't an MP4
    private fun boxType(buf: ByteArray, pos: Int): String? {
        for (i in 0 until 4) {
            val c = buf[pos + i].toInt() and 0xFF
            if (c < 0x20 || (c in 0x7F..0xA8) || c > 0xFF) return null
        }
        return String(buf, pos, 4, Charsets.ISO_8859_1)
    }

    private fun copyRange(raf: RandomAccessFile, offset: Long, size: Long, out: OutputStream) {
        val buffer = ByteArray(64 * 1024)
        raf.seek(offset)
        var remaining = size
        while (remaining > 0) {
            val read = raf.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) throw IllegalStateException("Unexpected end of file")
            out.write(buffer, 0, read)
            remaining -= read
        }
    }

    private fun intBytes(value: Int) = byteArrayOf(
        (value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte()
    )

    private fun readInt(buf: ByteArray, pos: Int): Int =
        ((buf[pos].toInt() and 0xFF) shl 24) or ((buf[pos + 1].toInt() and 0xFF) shl 16) or
            ((buf[pos + 2].toInt() and 0xFF) shl 8) or (buf[pos + 3].toInt() and 0xFF)

    private fun writeInt(buf: ByteArray, pos: Int, value: Int) {
        intBytes(value).copyInto(buf, pos)
    }

    private fun readLong(buf: ByteArray, pos: Int): Long =
        ((readInt(buf, pos).toLong() and 0xFFFFFFFFL) shl 32) or (readInt(buf, pos + 4).toLong() and 0xFFFFFFFFL)

    private fun writeLong(buf: ByteArray, pos: Int, value: Long) {
        writeInt(buf, pos, (value ushr 32).toInt())
        writeInt(buf, pos + 4, value.toInt())
    }

    private const val DATA_TYPE_UTF8 = 1
    private const val DATA_TYPE_JPEG = 13
    private const val TFHD_BASE_DATA_OFFSET_PRESENT = 0x000001
}
