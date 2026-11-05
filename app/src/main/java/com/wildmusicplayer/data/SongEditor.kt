package com.wildmusicplayer.data

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.wildmusicplayer.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Edits a song's title, artist, album and cover inside its file (MP3: ID3, M4A: iTunes tags), so the car,
 * lock screen and other apps see them too. Everything else in the file stays as it was. The MediaStore ID
 * doesn't change, so playlists, favorites and play counts keep pointing at the song.
 */
class SongEditor(context: Context) {

    private val appContext = context.applicationContext

    enum class Format(val label: String) { MP3("MP3"), M4A("M4A") }

    sealed interface Result {
        data object Saved : Result
        /** Android 10 asks the user first (other Android versions ask before, see [writeRequest]). */
        class NeedsPermission(val intentSender: IntentSender) : Result
        data object Failed : Result
    }

    /** MP3 and M4A can be edited; null for other formats (FLAC, OGG…) and online streams. */
    fun formatOf(song: Song): Format? {
        if (song.isOnline || song.id <= 0) return null
        return when (song.filePath?.substringAfterLast('.', "")?.lowercase()) {
            "mp3" -> Format.MP3
            "m4a", "mp4", "m4b" -> Format.M4A
            else -> null
        }
    }

    /**
     * Android 11+ only lets the app change files it created itself. For others it asks the user in a
     * system dialog first: this returns that dialog, or null if nothing needs asking.
     */
    fun writeRequest(song: Song): IntentSender? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val uri = uriOf(song)
        val owner = try {
            appContext.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.OWNER_PACKAGE_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        } catch (e: Exception) {
            null
        }
        if (owner == appContext.packageName) return null
        return MediaStore.createWriteRequest(appContext.contentResolver, listOf(uri)).intentSender
    }

    /** Writes the new details (and [coverJpeg], if a new cover was picked) into the song's file. */
    suspend fun save(song: Song, title: String, artist: String, album: String, coverJpeg: ByteArray?): Result =
        withContext(Dispatchers.IO) {
            val format = formatOf(song) ?: return@withContext Result.Failed
            val uri = uriOf(song)
            val workDir = File(appContext.cacheDir, "edit").apply { mkdirs() }
            val original = File(workDir, "${song.id}.orig")
            val edited = File(workDir, "${song.id}.edited")
            try {
                val resolver = appContext.contentResolver
                resolver.openInputStream(uri)?.use { input -> original.outputStream().use { input.copyTo(it) } }
                    ?: return@withContext Result.Failed
                val ok = when (format) {
                    Format.MP3 -> Id3TagWriter.writeTags(original, edited, Id3TagWriter.Tags(title, artist, album, coverJpeg))
                    Format.M4A -> Mp4TagWriter.writeTags(original, edited, Mp4TagWriter.Tags(title, artist, album, coverJpeg))
                }
                if (!ok || edited.length() == 0L) return@withContext Result.Failed

                val output = try {
                    resolver.openOutputStream(uri, "wt")
                } catch (e: SecurityException) {
                    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                        return@withContext Result.NeedsPermission(e.userAction.actionIntent.intentSender)
                    }
                    throw e
                } ?: return@withContext Result.Failed
                output.use { out -> edited.inputStream().use { it.copyTo(out) } }

                // Make MediaStore read the new details (Android 11+ also does this when the file closes)
                song.filePath?.let { path -> MediaScannerConnection.scanFile(appContext, arrayOf(path), null, null) }
                Result.Saved
            } catch (e: Exception) {
                e.printStackTrace()
                Result.Failed
            } finally {
                original.delete()
                edited.delete()
            }
        }

    private fun uriOf(song: Song): Uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id)

    companion object {
        /** A picked picture as a JPEG cover: at most [MAX_COVER_PX] on its longer side. */
        fun coverJpeg(context: Context, picture: Uri): ByteArray? = try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(picture)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_COVER_PX) sample *= 2
            val bitmap = context.contentResolver.openInputStream(picture)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return null
            val scale = MAX_COVER_PX.toFloat() / maxOf(bitmap.width, bitmap.height)
            val sized = if (scale < 1f) {
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
            } else bitmap
            ByteArrayOutputStream().also { sized.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
        } catch (e: Exception) {
            null
        }

        private const val MAX_COVER_PX = 800
    }
}
