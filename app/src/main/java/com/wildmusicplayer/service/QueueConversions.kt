package com.wildmusicplayer.service

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.wildmusicplayer.model.Song

fun QueuedSong.toSong(): Song = Song(
    id = id,
    title = title,
    artist = artist,
    album = album,
    duration = durationMs,
    contentUri = Uri.parse(uri),
    albumArtUri = artworkUri?.let { Uri.parse(it) }
)

private fun MediaItem.toQueuedSong(): QueuedSong? {
    val uri = localConfiguration?.uri ?: return null
    return QueuedSong(
        id = mediaId.toLongOrNull() ?: Song.ONLINE_SONG_ID,
        title = mediaMetadata.title?.toString() ?: "Unknown Title",
        artist = mediaMetadata.artist?.toString() ?: "Unknown Artist",
        album = mediaMetadata.albumTitle?.toString() ?: "",
        durationMs = 0L,
        uri = uri.toString(),
        artworkUri = mediaMetadata.artworkUri?.toString()
    )
}

/** The player's current queue and position, or null when there's nothing to save. */
fun Player.snapshotQueue(): SavedQueue? {
    if (mediaItemCount == 0) return null
    val songs = (0 until mediaItemCount).map { i -> getMediaItemAt(i).toQueuedSong() ?: return null }
    return SavedQueue(
        songs = songs,
        index = currentMediaItemIndex.coerceIn(0, songs.lastIndex),
        positionMs = currentPosition.coerceAtLeast(0L),
        shuffle = shuffleModeEnabled,
        repeatMode = repeatMode
    )
}
