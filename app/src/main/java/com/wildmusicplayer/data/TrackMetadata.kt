package com.wildmusicplayer.data

/**
 * The tags written into a downloaded file: what the library, lock screen and car radio show.
 * Album is the song title ("single"), so every download gets its own cover in MediaStore
 * (MediaStore groups cover art by album name).
 */
data class TrackMetadata(
    val title: String,
    val artist: String,
    val album: String,
    val coverUrl: String?
)

fun trackMetadataFor(track: YouTubeTrack): TrackMetadata {
    val (artist, title) = if (track.isMusicSong) {
        // YouTube Music songs already have a clean title and the real artist
        track.uploaderName.trim().ifEmpty { "Unknown Artist" } to track.title.trim()
    } else {
        parseVideoTitle(track.title, track.uploaderName)
    }
    return TrackMetadata(
        title = title,
        artist = artist,
        album = title,
        coverUrl = track.thumbnailUrl?.let { upscaleGoogleThumbnail(it) }
    )
}
