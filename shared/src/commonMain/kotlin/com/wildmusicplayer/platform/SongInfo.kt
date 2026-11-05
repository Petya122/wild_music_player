package com.wildmusicplayer.platform

import com.wildmusicplayer.model.Song

/** Details read from the song's file. Any of them can be missing when the phone doesn't report it. */
data class SongFileInfo(
    /** "MP3", "FLAC", "AAC (M4A)"… */
    val format: String? = null,
    val bitrateKbps: Int? = null,
    val sampleRateHz: Int? = null,
    val sizeBytes: Long? = null,
    /** When the file came onto the phone, already written for the user ("5 Nov 2026"). */
    val addedOn: String? = null
)

/** Where the Song info sheet gets its details. */
interface SongInfoSource {
    /** Reads the file's details; null for online streams or when the file can't be read. */
    suspend fun fileInfo(song: Song): SongFileInfo?

    /** How often the song was played (counted after 30 seconds). */
    fun playCount(songId: Long): Int = 0
}
