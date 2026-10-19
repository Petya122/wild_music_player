package com.wildmusicplayer.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.wildmusicplayer.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository responsible for scanning local audio files via Android's MediaStore
 * and converting them into Media3 MediaItem representations with rich metadata
 * formatted for Bluetooth AVRCP head units (e.g., 2017 Nissan Qashqai).
 */
class AudioRepository(private val context: Context) {

    suspend fun getLocalSongs(): List<Song> = withContext(Dispatchers.IO) {
        val songList = mutableListOf<Song>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID
        )

        // Filter: real music only, minimum 10 seconds to exclude notification chimes
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 10000"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val collectionUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        context.contentResolver.query(
            collectionUri,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

            val albumArtBaseUri = Uri.parse("content://media/external/audio/albumart")

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val rawTitle = cursor.getString(titleColumn) ?: "Unknown Title"
                val rawArtist = cursor.getString(artistColumn) ?: "Unknown Artist"
                val rawAlbum = cursor.getString(albumColumn) ?: "Unknown Album"
                val duration = cursor.getLong(durationColumn)
                val albumId = cursor.getLong(albumIdColumn)

                val contentUri = ContentUris.withAppendedId(collectionUri, id)
                val albumArtUri = if (albumId >= 0) {
                    ContentUris.withAppendedId(albumArtBaseUri, albumId)
                } else null

                // Clean up unknown placeholders for cleaner car displays
                val artist = if (rawArtist.equals("<unknown>", ignoreCase = true)) "Unknown Artist" else rawArtist
                val album = if (rawAlbum.equals("<unknown>", ignoreCase = true)) "Unknown Album" else rawAlbum

                songList.add(
                    Song(
                        id = id,
                        title = rawTitle.trim(),
                        artist = artist.trim(),
                        album = album.trim(),
                        duration = duration,
                        contentUri = contentUri,
                        albumArtUri = albumArtUri
                    )
                )
            }
        }

        songList
    }
}

/**
 * Extension function to convert a domain [Song] to a Media3 [MediaItem]
 * with structured metadata for lock screens, notifications, and car Bluetooth AVRCP.
 */
fun Song.toMediaItem(): MediaItem {
    val metadata = MediaMetadata.Builder()
        .setTitle(title)                      // Line 1 on Nissan Qashqai dot-matrix LCD
        .setArtist(artist)                    // Line 2 on Nissan Qashqai dot-matrix LCD
        .setAlbumTitle(album)                 // Cycled via 'DISP' button on the radio
        .setDisplayTitle(title)
        .setArtworkUri(albumArtUri)
        .setIsPlayable(true)
        .build()

    return MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(contentUri)
        .setMediaMetadata(metadata)
        .build()
}
