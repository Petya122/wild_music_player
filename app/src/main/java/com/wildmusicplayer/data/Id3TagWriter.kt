package com.wildmusicplayer.data

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile

/**
 * Writes title, artist, album and (optionally) a new cover into an MP3's ID3v2 tag, keeping every other
 * frame (track number, year, genre, the old cover when no new one is given…) and the audio untouched.
 * The tag keeps its version (2.3 or 2.4); a file without one, or with an old 2.2 tag, gets a new 2.3 tag.
 * An ID3v1 tag at the end, if any, gets the new title, artist and album too.
 *
 * Files it can't edit safely (unsynchronised or compressed tags) are left alone: [writeTags] returns false.
 */
object Id3TagWriter {

    class Tags(
        val title: String,
        val artist: String,
        val album: String,
        val coverJpeg: ByteArray?
    )

    private const val HEADER_SIZE = 10
    private const val PADDING = 1024
    private val replacedText = setOf("TIT2", "TPE1", "TALB")

    /** Writes [input] with [tags] to [output]. False if the file can't be edited safely. */
    fun writeTags(input: File, output: File, tags: Tags): Boolean = try {
        RandomAccessFile(input, "r").use { raf -> write(raf, output, tags) }
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }

    private fun write(raf: RandomAccessFile, output: File, tags: Tags): Boolean {
        val length = raf.length()
        val header = ByteArray(HEADER_SIZE)
        var version = 3
        var audioStart = 0L
        var keptFrames = ByteArray(0)

        if (length >= HEADER_SIZE) {
            raf.seek(0)
            raf.readFully(header)
        }
        if (length >= HEADER_SIZE && header[0] == 'I'.code.toByte() && header[1] == 'D'.code.toByte() && header[2] == '3'.code.toByte()) {
            val major = header[3].toInt()
            val flags = header[5].toInt() and 0xFF
            val size = syncsafe(header, 6)
            val footer = if (major == 4 && flags and 0x10 != 0) 10 else 0
            audioStart = HEADER_SIZE + size.toLong() + footer
            if (audioStart > length) return false
            when (major) {
                3, 4 -> {
                    // Unsynchronised tags would need decoding first; rare enough to leave alone
                    if (flags and 0x80 != 0) return false
                    version = major
                    val tagBytes = ByteArray(size)
                    raf.seek(HEADER_SIZE.toLong())
                    raf.readFully(tagBytes)
                    var start = 0
                    if (flags and 0x40 != 0) {
                        // Extended header: its size (v2.3 without itself, v2.4 with itself)
                        start = if (major == 4) syncsafe(tagBytes, 0) else readInt(tagBytes, 0) + 4
                    }
                    keptFrames = keptFrames(tagBytes, start, major, tags) ?: return false
                }
                // ID3v2.2 (three-letter frames): replaced by a new 2.3 tag with just these details
                2 -> version = 3
                else -> return false
            }
        }

        val frames = ByteArrayOutputStream().apply {
            write(textFrame("TIT2", tags.title, version))
            write(textFrame("TPE1", tags.artist, version))
            write(textFrame("TALB", tags.album, version))
            tags.coverJpeg?.let { write(pictureFrame(it, version)) }
            write(keptFrames)
        }.toByteArray()
        val tagSize = frames.size + PADDING

        // An ID3v1 tag in the last 128 bytes: update it too, or players reading it would show the old name
        var audioEnd = length
        var v1: ByteArray? = null
        if (length - audioStart >= 128) {
            val tail = ByteArray(128)
            raf.seek(length - 128)
            raf.readFully(tail)
            if (tail[0] == 'T'.code.toByte() && tail[1] == 'A'.code.toByte() && tail[2] == 'G'.code.toByte()) {
                audioEnd = length - 128
                v1 = updatedV1(tail, tags)
            }
        }

        output.outputStream().buffered().use { out ->
            out.write(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), version.toByte(), 0, 0))
            out.write(syncsafeBytes(tagSize))
            out.write(frames)
            out.write(ByteArray(PADDING))
            raf.seek(audioStart)
            val buffer = ByteArray(64 * 1024)
            var remaining = audioEnd - audioStart
            while (remaining > 0) {
                val read = raf.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (read < 0) break
                out.write(buffer, 0, read)
                remaining -= read
            }
            v1?.let { out.write(it) }
        }
        return true
    }

    /** The existing frames that aren't replaced, as raw bytes; null if the tag is damaged or compressed. */
    private fun keptFrames(tag: ByteArray, start: Int, version: Int, tags: Tags): ByteArray? {
        val out = ByteArrayOutputStream()
        var pos = start
        while (pos + HEADER_SIZE <= tag.size) {
            // Padding (zeros) ends the frames
            if (tag[pos].toInt() == 0) break
            val id = String(tag, pos, 4, Charsets.ISO_8859_1)
            if (!id.all { it in 'A'..'Z' || it in '0'..'9' }) return null
            val size = if (version == 4) syncsafe(tag, pos + 4) else readInt(tag, pos + 4)
            val end = pos + HEADER_SIZE + size
            if (size < 0 || end > tag.size) return null
            // Compressed or encrypted frames are kept as they are; v2.3 flags 0x80/0x40 in the second byte
            val replaced = id in replacedText || (id == "APIC" && tags.coverJpeg != null)
            if (!replaced) out.write(tag, pos, end - pos)
            pos = end
        }
        return out.toByteArray()
    }

    // v2.3: UTF-16 with a byte order mark; v2.4: UTF-8
    private fun textFrame(id: String, text: String, version: Int): ByteArray {
        val payload = if (version == 4) {
            byteArrayOf(3) + text.toByteArray(Charsets.UTF_8)
        } else {
            byteArrayOf(1) + byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE)
        }
        return frame(id, payload, version)
    }

    // Front cover (type 3), JPEG, no description
    private fun pictureFrame(jpeg: ByteArray, version: Int): ByteArray {
        val payload = byteArrayOf(0) + "image/jpeg".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0, 3, 0) + jpeg
        return frame("APIC", payload, version)
    }

    private fun frame(id: String, payload: ByteArray, version: Int): ByteArray =
        id.toByteArray(Charsets.ISO_8859_1) +
            (if (version == 4) syncsafeBytes(payload.size) else intBytes(payload.size)) +
            byteArrayOf(0, 0) + payload

    private fun updatedV1(tail: ByteArray, tags: Tags): ByteArray = tail.copyOf().also { v1 ->
        fun put(text: String, offset: Int) {
            val bytes = text.map { if (it.code in 32..255) it.code.toByte() else '?'.code.toByte() }.take(30)
            for (i in 0 until 30) v1[offset + i] = bytes.getOrElse(i) { 0 }
        }
        put(tags.title, 3)
        put(tags.artist, 33)
        put(tags.album, 63)
    }

    // ---- reading back (tests) ----

    /** The text frames (and "APIC" as "<n> bytes") of [file]'s ID3v2 tag. */
    internal fun readFrames(file: File): Map<String, String> {
        val bytes = file.readBytes()
        if (bytes.size < HEADER_SIZE || String(bytes, 0, 3, Charsets.ISO_8859_1) != "ID3") return emptyMap()
        val version = bytes[3].toInt()
        val size = syncsafe(bytes, 6)
        val result = mutableMapOf<String, String>()
        var pos = HEADER_SIZE
        while (pos + HEADER_SIZE <= HEADER_SIZE + size && bytes[pos].toInt() != 0) {
            val id = String(bytes, pos, 4, Charsets.ISO_8859_1)
            val frameSize = if (version == 4) syncsafe(bytes, pos + 4) else readInt(bytes, pos + 4)
            val data = bytes.copyOfRange(pos + HEADER_SIZE, pos + HEADER_SIZE + frameSize)
            result[id] = when {
                id == "APIC" -> "${frameSize} bytes"
                id.startsWith("T") -> decodeText(data)
                else -> "${frameSize} bytes"
            }
            pos += HEADER_SIZE + frameSize
        }
        return result
    }

    private fun decodeText(data: ByteArray): String {
        if (data.isEmpty()) return ""
        val body = data.copyOfRange(1, data.size)
        return when (data[0].toInt()) {
            1 -> String(body, Charsets.UTF_16)
            2 -> String(body, Charsets.UTF_16BE)
            3 -> String(body, Charsets.UTF_8)
            else -> String(body, Charsets.ISO_8859_1)
        }.trimEnd('\u0000')
    }

    // ---- numbers ----

    private fun syncsafe(b: ByteArray, p: Int): Int =
        ((b[p].toInt() and 0x7F) shl 21) or ((b[p + 1].toInt() and 0x7F) shl 14) or
            ((b[p + 2].toInt() and 0x7F) shl 7) or (b[p + 3].toInt() and 0x7F)

    private fun syncsafeBytes(v: Int): ByteArray =
        byteArrayOf(((v shr 21) and 0x7F).toByte(), ((v shr 14) and 0x7F).toByte(), ((v shr 7) and 0x7F).toByte(), (v and 0x7F).toByte())

    private fun readInt(b: ByteArray, p: Int): Int =
        ((b[p].toInt() and 0xFF) shl 24) or ((b[p + 1].toInt() and 0xFF) shl 16) or
            ((b[p + 2].toInt() and 0xFF) shl 8) or (b[p + 3].toInt() and 0xFF)

    private fun intBytes(v: Int): ByteArray =
        byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())
}
