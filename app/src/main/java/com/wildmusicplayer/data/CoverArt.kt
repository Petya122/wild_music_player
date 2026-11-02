package com.wildmusicplayer.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.min

/** Cover art and file checks used when tagging downloaded songs. */
object CoverArt {

    private const val COVER_SIZE = 1000

    /**
     * Downloads [url] and returns it as a square 1000×1000 JPEG, or null if anything fails.
     * 16:9 video thumbnails are center-cropped; 4:3 ones ("hqdefault") first lose their black bars.
     */
    fun fetchSquareJpeg(httpClient: OkHttpClient, url: String?): ByteArray? {
        if (url.isNullOrBlank()) return null
        return try {
            val request = Request.Builder().url(url).build()
            val bytes = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.bytes()
            } ?: return null
            val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val square = cropToSquare(source)
            // Never upscale: a bigger copy of a small thumbnail isn't any sharper
            val size = minOf(square.width, COVER_SIZE)
            val scaled = Bitmap.createScaledBitmap(square, size, size, true)
            ByteArrayOutputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)
                out.toByteArray()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun cropToSquare(bitmap: Bitmap): Bitmap {
        var left = 0
        var top = 0
        var width = bitmap.width
        var height = bitmap.height

        // 4:3 thumbnails of 16:9 videos have black bars above and below the picture
        if (abs(width.toFloat() / height - 4f / 3f) < 0.05f) {
            val contentHeight = width * 9 / 16
            top = (height - contentHeight) / 2
            height = contentHeight
        }

        val side = min(width, height)
        left += (width - side) / 2
        top += (height - side) / 2
        return Bitmap.createBitmap(bitmap, left, top, side, side)
    }

    /**
     * True if Android can open [file] as audio and read its first samples.
     * Guards against ever saving a file the tag writer damaged.
     */
    fun isPlayableAudio(file: File): Boolean {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            val audioTrack = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(android.media.MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return false
            extractor.selectTrack(audioTrack)
            val buffer = ByteBuffer.allocate(256 * 1024)
            var samplesRead = 0
            while (samplesRead < 5 && extractor.readSampleData(buffer, 0) > 0) {
                samplesRead++
                extractor.advance()
            }
            samplesRead > 0
        } catch (e: Exception) {
            false
        } finally {
            extractor.release()
        }
    }
}
