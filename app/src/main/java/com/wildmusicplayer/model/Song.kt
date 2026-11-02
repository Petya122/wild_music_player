package com.wildmusicplayer.model

import android.net.Uri

/**
 * Represents an audio track stored locally on the device, or an online track
 * queued for streaming (see [isOnline]).
 */
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val contentUri: Uri,
    val albumArtUri: Uri?,
    val folderName: String = "Music"
) {
    /** True for YouTube tracks whose stream URL is resolved at play time by PlaybackService. */
    val isOnline: Boolean
        get() = contentUri.scheme == ONLINE_SCHEME

    /** Media3 media ID: the MediaStore ID for local files, the stream URI for online tracks. */
    val mediaId: String
        get() = if (isOnline) "online:$contentUri" else id.toString()

    companion object {
        const val ONLINE_SCHEME = "wmp-yt"

        /** ID used for every song that isn't a MediaStore file (online streams). */
        const val ONLINE_SONG_ID = -1L

        /** Builds a queueable online song; PlaybackService turns its URI into a stream URL when it plays. */
        fun fromOnline(
            title: String,
            artist: String,
            webUrl: String,
            durationSeconds: Long,
            thumbnailUrl: String?
        ): Song = Song(
            id = ONLINE_SONG_ID,
            title = title,
            artist = artist,
            album = "YouTube Stream",
            duration = durationSeconds * 1000L,
            contentUri = Uri.Builder()
                .scheme(ONLINE_SCHEME)
                .authority("stream")
                .appendQueryParameter("url", webUrl)
                .build(),
            albumArtUri = thumbnailUrl?.let { Uri.parse(it) }
        )

        /** The YouTube page URL inside an online song URI, or null for any other URI. */
        fun webUrlOf(uri: Uri): String? =
            if (uri.scheme == ONLINE_SCHEME) uri.getQueryParameter("url") else null
    }
}
