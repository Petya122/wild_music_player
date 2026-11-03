package com.wildmusicplayer.platform

import com.wildmusicplayer.data.DownloadIndex
import com.wildmusicplayer.data.OnlineTrack
import com.wildmusicplayer.model.Song
import kotlinx.coroutines.flow.StateFlow

/** The songs on the device: MediaStore on Android, the app's Documents folder on iOS. */
interface MusicLibrary {
    suspend fun loadSongs(filterShortAudio: Boolean, filterRecordings: Boolean): List<Song>
}

/** Starts and controls playback: Media3 on Android, AVPlayer on iOS. */
interface PlayerController {
    val currentSong: StateFlow<Song?>
    fun playSong(song: Song, playlist: List<Song>)
    fun playQueue(songs: List<Song>, startIndex: Int)
    fun skipToNext()

    /** Queued online songs that have been downloaded since play from the file instead. */
    fun useDownloadedCopies(downloadIndex: DownloadIndex)

    /** Disconnects from the playback service (playback itself keeps going). */
    fun release()
}

/** Repeat modes, numbered like Media3's Player.REPEAT_MODE_* (also how saved queues store them). */
object RepeatMode {
    const val OFF = 0
    const val ONE = 1
    const val ALL = 2
}

/** Why a download can't start right now. */
enum class DownloadBlock { OFFLINE_MODE, NO_NETWORK, METERED }

/** Background downloads of online playlist tracks. Android only: iOS doesn't download from YouTube. */
interface TrackDownloader {
    /** Progress 0..1 of each track downloading right now, by track ID. */
    val activeDownloads: StateFlow<Map<String, Float>>

    /** Null if downloads may start now, otherwise why not. */
    fun blockedReason(): DownloadBlock?

    fun start(tracks: List<OnlineTrack>)
}
