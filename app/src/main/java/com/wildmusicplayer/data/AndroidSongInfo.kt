package com.wildmusicplayer.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.wildmusicplayer.model.Song
import com.wildmusicplayer.platform.SongFileInfo
import com.wildmusicplayer.platform.SongInfoSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** Song info from MediaStore (type, size, date added) and the file itself (bitrate, sample rate). */
class AndroidSongInfo(context: Context, private val playlists: PlaylistRepository) : SongInfoSource {

    private val appContext = context.applicationContext

    override fun playCount(songId: Long): Int = playlists.playCounts.value[songId] ?: 0

    override suspend fun fileInfo(song: Song): SongFileInfo? = withContext(Dispatchers.IO) {
        if (song.isOnline) return@withContext null
        val uri = Uri.parse(song.contentUri)

        var mime: String? = null
        var size: Long? = null
        var addedSeconds: Long? = null
        var bitrate: Int? = null
        val projection = buildList {
            add(MediaStore.Audio.Media.MIME_TYPE)
            add(MediaStore.Audio.Media.SIZE)
            add(MediaStore.Audio.Media.DATE_ADDED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) add(MediaStore.Audio.Media.BITRATE)
        }.toTypedArray()
        try {
            appContext.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    mime = cursor.getString(0)
                    size = cursor.getLong(1).takeIf { it > 0 }
                    addedSeconds = cursor.getLong(2).takeIf { it > 0 }
                    if (projection.size > 3 && !cursor.isNull(3)) bitrate = cursor.getInt(3).takeIf { it > 0 }
                }
            }
        } catch (e: Exception) {
            // Deleted meanwhile, or no permission: show what the tags say
        }

        var sampleRate: Int? = null
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(appContext, uri)
            if (bitrate == null) {
                bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                sampleRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull()
            }
            if (mime == null) mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
        } catch (e: Exception) {
            // Unreadable file: leave those details out
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }

        SongFileInfo(
            format = formatName(mime, song.filePath),
            bitrateKbps = bitrate?.let { (it + 500) / 1000 }?.takeIf { it > 0 },
            sampleRateHz = sampleRate?.takeIf { it > 0 },
            sizeBytes = size,
            addedOn = addedSeconds?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it * 1000)) }
        )
    }

    /** "audio/mpeg" → "MP3"; unknown types fall back to the file's extension. */
    private fun formatName(mime: String?, path: String?): String? {
        val byMime = when (mime?.lowercase()) {
            "audio/mpeg", "audio/mp3" -> "MP3"
            "audio/mp4", "audio/x-m4a", "audio/m4a", "audio/aac", "audio/mp4a-latm" -> "AAC (M4A)"
            "audio/flac", "audio/x-flac" -> "FLAC"
            "audio/ogg", "application/ogg", "audio/vorbis" -> "Ogg Vorbis"
            "audio/opus" -> "Opus"
            "audio/wav", "audio/x-wav", "audio/wave" -> "WAV"
            "audio/webm" -> "WebM"
            "audio/amr" -> "AMR"
            else -> null
        }
        return byMime ?: path?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() && it.length <= 5 }?.uppercase() ?: mime
    }
}
