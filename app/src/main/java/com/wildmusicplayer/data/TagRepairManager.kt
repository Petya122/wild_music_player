package com.wildmusicplayer.data

import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** A downloaded song in Music/WildMusic that has no artist tag. */
data class RepairCandidate(
    val id: Long,
    val uri: Uri,
    val title: String,
    val filePath: String?,
    val ownedByApp: Boolean
)

/**
 * Finds downloads saved before v0.6.0 (no artist, no cover), looks them up on YouTube Music,
 * and rewrites the files in place with proper tags. MediaStore IDs stay the same, so
 * playlists, favourites and recently played keep pointing at the right songs.
 */
class TagRepairManager(
    private val context: Context,
    private val downloadManager: YouTubeDownloadManager
) {

    suspend fun findUntaggedDownloads(): List<RepairCandidate> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DATA
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(MediaStore.Audio.Media.OWNER_PACKAGE_NAME)
        }.toTypedArray()

        val (selection, args) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?" to arrayOf("Music/WildMusic%")
        } else {
            "${MediaStore.Audio.Media.DATA} LIKE ?" to arrayOf("%/Music/WildMusic/%")
        }

        val result = mutableListOf<RepairCandidate>()
        context.contentResolver.query(collection, projection, selection, args, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
            val ownerCol = cursor.getColumnIndex(MediaStore.Audio.Media.OWNER_PACKAGE_NAME)

            while (cursor.moveToNext()) {
                val artist = cursor.getString(artistCol)
                if (!artist.isNullOrBlank() && !artist.equals("<unknown>", ignoreCase = true)) continue

                val id = cursor.getLong(idCol)
                val owner = if (ownerCol >= 0) cursor.getString(ownerCol) else null
                result += RepairCandidate(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    title = cursor.getString(titleCol) ?: "Unknown Title",
                    filePath = if (dataCol >= 0) cursor.getString(dataCol) else null,
                    ownedByApp = owner == null || owner == context.packageName
                )
            }
        }
        result.sortedBy { it.title.lowercase() }
    }

    /** Up to five YouTube Music songs that could be this download, best match first. */
    suspend fun findMatches(candidate: RepairCandidate): List<YouTubeTrack> {
        val query = stripTitleNoise(candidate.title).ifBlank { candidate.title }
        return downloadManager.search(query, SearchMode.SONGS)
            .sortedByDescending { searchScore(it, query) }
            .take(5)
    }

    /**
     * Android 11+ only lets the app edit files it created. Files from before a reinstall need the
     * user's OK in a system dialog; this returns that dialog, or null if nothing needs asking.
     */
    fun writePermissionRequest(candidates: List<RepairCandidate>): IntentSender? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val uris = candidates.filter { !it.ownedByApp }.map { it.uri }
        if (uris.isEmpty()) return null
        return MediaStore.createWriteRequest(context.contentResolver, uris).intentSender
    }

    /** Rewrites [candidate]'s file with [match]'s title, artist, album and cover. */
    suspend fun applyMatch(candidate: RepairCandidate, match: YouTubeTrack): Boolean = withContext(Dispatchers.IO) {
        val workDir = File(context.cacheDir, "repair").apply { mkdirs() }
        val original = File(workDir, "${candidate.id}.m4a")
        var tagged: File? = null
        try {
            val resolver = context.contentResolver
            val input = resolver.openInputStream(candidate.uri) ?: return@withContext false
            input.use { inp -> original.outputStream().use { inp.copyTo(it) } }

            val taggedFile = downloadManager.tagFile(original, trackMetadataFor(match))
            if (taggedFile == original) return@withContext false // couldn't tag safely, leave the song as it is
            tagged = taggedFile

            val output = resolver.openOutputStream(candidate.uri, "wt") ?: return@withContext false
            output.use { out -> taggedFile.inputStream().use { it.copyTo(out) } }

            // Make MediaStore re-read the new tags (Android 11+ also does this when the file closes)
            candidate.filePath?.let { path ->
                MediaScannerConnection.scanFile(context, arrayOf(path), arrayOf("audio/mp4"), null)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            original.delete()
            tagged?.delete()
        }
    }
}
