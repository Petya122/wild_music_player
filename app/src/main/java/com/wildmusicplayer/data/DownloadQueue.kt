package com.wildmusicplayer.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where a song in the [DownloadQueue] is. */
enum class DownloadState { WAITING, DOWNLOADING, FAILED }

/** Why the [DownloadQueue] is paused (it can be for several reasons at once). */
enum class DownloadPause {
    /** Offline Mode is on: no network use at all. */
    OFFLINE,
    /** "Background Downloading" is off and the app isn't on screen. */
    APP_IN_BACKGROUND
}

data class QueuedDownload(
    val track: YouTubeTrack,
    val state: DownloadState = DownloadState.WAITING,
    val progress: Float = 0f,
    val error: String? = null
)

/**
 * Every song download, in order: the one downloading, the ones waiting, and the ones that failed (until
 * retried or removed). SongDownloadService works through it one song at a time. While it's paused nothing
 * starts, and a running download stops and goes back to the front to start again later.
 * Finished songs leave the queue (their IDs stay in [completed] until the app closes).
 */
object DownloadQueue {

    private val lock = Any()

    private val _items = MutableStateFlow<List<QueuedDownload>>(emptyList())
    val items: StateFlow<List<QueuedDownload>> = _items.asStateFlow()

    private val _pausedFor = MutableStateFlow<Set<DownloadPause>>(emptySet())
    val pausedFor: StateFlow<Set<DownloadPause>> = _pausedFor.asStateFlow()

    private val _completed = MutableStateFlow<Set<String>>(emptySet())
    val completed: StateFlow<Set<String>> = _completed.asStateFlow()

    val isPaused: Boolean get() = _pausedFor.value.isNotEmpty()

    /** True when there's a song waiting and nothing pauses the queue: time to (re)start the service. */
    val canStart: Boolean get() = !isPaused && _items.value.any { it.state == DownloadState.WAITING }

    /** Adds [track] at the end (a failed one is tried again). False when it's already waiting or downloading. */
    fun add(track: YouTubeTrack): Boolean = synchronized(lock) {
        val existing = _items.value.find { it.track.id == track.id }
        if (existing != null && existing.state != DownloadState.FAILED) return false
        _items.value = _items.value.filter { it.track.id != track.id } + QueuedDownload(track)
        true
    }

    /** Takes the song out of the queue; if it's downloading, the download stops. */
    fun remove(trackId: String) = synchronized(lock) {
        _items.value = _items.value.filter { it.track.id != trackId }
    }

    /** A failed song waits again (at the end). */
    fun retry(trackId: String): Unit = synchronized(lock) {
        val item = _items.value.find { it.track.id == trackId && it.state == DownloadState.FAILED } ?: return
        _items.value = _items.value.filter { it.track.id != trackId } + QueuedDownload(item.track)
    }

    fun clearFailed() = synchronized(lock) {
        _items.value = _items.value.filter { it.state != DownloadState.FAILED }
    }

    /** The next song to download, now marked as downloading; null when paused, busy or nothing waits. */
    fun takeNext(): QueuedDownload? = synchronized(lock) {
        if (isPaused) return null
        if (_items.value.any { it.state == DownloadState.DOWNLOADING }) return null
        val next = _items.value.firstOrNull { it.state == DownloadState.WAITING } ?: return null
        val taken = next.copy(state = DownloadState.DOWNLOADING, progress = 0f)
        _items.value = _items.value.map { if (it.track.id == next.track.id) taken else it }
        taken
    }

    fun setProgress(trackId: String, progress: Float) = synchronized(lock) {
        _items.value = _items.value.map {
            if (it.track.id == trackId && it.state == DownloadState.DOWNLOADING) it.copy(progress = progress) else it
        }
    }

    /** The running download should stop: the queue was paused, or the song was removed from it. */
    fun shouldStop(trackId: String): Boolean =
        isPaused || _items.value.none { it.track.id == trackId && it.state == DownloadState.DOWNLOADING }

    /**
     * The download of [trackId] ended. Saved: it leaves the queue. Stopped by a pause: it waits at the front
     * to start again. Otherwise it failed with [error] (removed songs are simply gone).
     */
    fun finished(trackId: String, saved: Boolean, error: String? = null) = synchronized(lock) {
        val item = _items.value.find { it.track.id == trackId }
        when {
            saved -> {
                _items.value = _items.value.filter { it.track.id != trackId }
                _completed.value = _completed.value + trackId
            }
            item == null -> Unit
            isPaused -> _items.value = listOf(QueuedDownload(item.track)) + _items.value.filter { it.track.id != trackId }
            else -> _items.value = _items.value.map {
                if (it.track.id == trackId) it.copy(state = DownloadState.FAILED, progress = 0f, error = error) else it
            }
        }
    }

    /** The download service ended mid-download (Android stopped it): that song waits at the front again. */
    fun interrupted(): Unit = synchronized(lock) {
        val running = _items.value.filter { it.state == DownloadState.DOWNLOADING }
        if (running.isEmpty()) return
        _items.value = running.map { QueuedDownload(it.track) } + _items.value.filter { it.state != DownloadState.DOWNLOADING }
    }

    fun pause(reason: DownloadPause) {
        _pausedFor.value = _pausedFor.value + reason
    }

    /** Lifts [reason]; true when the queue now may run and has songs waiting (start the service). */
    fun resume(reason: DownloadPause): Boolean {
        _pausedFor.value = _pausedFor.value - reason
        return canStart
    }

    /** For tests: an empty, running queue. */
    internal fun reset() = synchronized(lock) {
        _items.value = emptyList()
        _pausedFor.value = emptySet()
        _completed.value = emptySet()
    }
}
