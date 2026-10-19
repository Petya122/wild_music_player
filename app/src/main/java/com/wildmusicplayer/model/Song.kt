package com.wildmusicplayer.model

import android.net.Uri

/**
 * Represents an audio track stored locally on the device.
 */
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val contentUri: Uri,
    val albumArtUri: Uri?
)
