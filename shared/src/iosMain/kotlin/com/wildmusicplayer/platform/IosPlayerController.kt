package com.wildmusicplayer.platform

import com.wildmusicplayer.data.DownloadIndex
import com.wildmusicplayer.model.Song
import com.wildmusicplayer.service.QueueStore
import com.wildmusicplayer.service.QueuedSong
import com.wildmusicplayer.service.SavedQueue
import com.wildmusicplayer.service.toSong
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.currentTime
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.AVFoundation.volume
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.MediaPlayer.MPChangePlaybackPositionCommandEvent
import platform.MediaPlayer.MPMediaItemPropertyAlbumTitle
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyPlaybackDuration
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoCenter
import platform.MediaPlayer.MPNowPlayingInfoPropertyElapsedPlaybackTime
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackRate
import platform.MediaPlayer.MPRemoteCommandCenter
import platform.MediaPlayer.MPRemoteCommandHandlerStatusCommandFailed
import platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess

/**
 * Plays the queue with AVPlayer. The lock screen and the car's display get the song through
 * MPNowPlayingInfoCenter; the car's (and headphones') play/pause/next buttons arrive through
 * MPRemoteCommandCenter, the iOS side of the same Bluetooth AVRCP controls Android uses.
 *
 * Online (YouTube) songs can't play on iOS and are left out of the queue.
 */
@OptIn(ExperimentalForeignApi::class)
class IosPlayerController(
    private val queueStore: QueueStore,
    private val effects: IosAudioEffects
) : PlayerController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val player = AVPlayer()

    private var songs: List<Song> = emptyList()
    private var order: List<Int> = emptyList() // play order: songs indices, shuffled when shuffle is on
    private var position = 0 // index into order
    private var progressJob: Job? = null

    private val _currentSong = MutableStateFlow<Song?>(null)
    override val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    override val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    override val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _isShuffleEnabled = MutableStateFlow(false)
    override val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    override val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _queue = MutableStateFlow<List<QueueItem>>(emptyList())
    override val queue: StateFlow<List<QueueItem>> = _queue.asStateFlow()

    private val _currentQueuePosition = MutableStateFlow(-1)
    override val currentQueuePosition: StateFlow<Int> = _currentQueuePosition.asStateFlow()

    init {
        AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, error = null)
        effects.onVolumeChanged = { player.volume = it }
        player.volume = effects.playerVolume()

        NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue
        ) { _ -> onSongFinished() }

        setUpRemoteCommands()
        restoreSavedQueue()
    }

    override fun playSong(song: Song, playlist: List<Song>) {
        val playable = playlist.filter { !it.isOnline }
        playQueue(playable, playable.indexOfFirst { it.mediaId == song.mediaId }.coerceAtLeast(0))
    }

    override fun playQueue(songs: List<Song>, startIndex: Int) {
        val playable = songs.filter { !it.isOnline }
        if (playable.isEmpty()) return
        val start = songs.getOrNull(startIndex)?.let { s -> playable.indexOfFirst { it.mediaId == s.mediaId } } ?: 0
        this.songs = playable
        rebuildOrder(startAt = start.coerceAtLeast(0))
        load(autoPlay = true)
    }

    override fun togglePlayPause() {
        if (_currentSong.value == null) return
        if (_isPlaying.value) pause() else resume()
    }

    override fun skipToNext() {
        if (songs.isEmpty()) return
        if (position < order.lastIndex) {
            position++
        } else if (_repeatMode.value == RepeatMode.ALL) {
            position = 0
        } else {
            return
        }
        load(autoPlay = true)
    }

    override fun skipToPrevious() {
        if (songs.isEmpty()) return
        // Like most players: restart the song unless it only just started
        if (_currentPosition.value > 3_000 || position == 0) {
            seekTo(0)
        } else {
            position--
            load(autoPlay = _isPlaying.value)
        }
    }

    override fun seekTo(positionMs: Long) {
        player.seekToTime(CMTimeMakeWithSeconds(positionMs / 1000.0, 600))
        _currentPosition.value = positionMs
        updateNowPlaying()
    }

    override fun toggleShuffle() {
        _isShuffleEnabled.value = !_isShuffleEnabled.value
        val current = order.getOrNull(position) ?: 0
        rebuildOrder(startAt = current)
        saveQueue()
    }

    override fun toggleRepeatMode() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            else -> RepeatMode.OFF
        }
        saveQueue()
    }

    override fun stopPlayback() {
        pause()
        player.replaceCurrentItemWithPlayerItem(null)
        songs = emptyList()
        order = emptyList()
        position = 0
        _currentSong.value = null
        _currentPosition.value = 0
        _duration.value = 0
        publishQueue()
        queueStore.clear()
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = null
    }

    override fun playNext(song: Song) = insert(song, if (order.isEmpty()) 0 else position + 1)

    override fun addToQueue(song: Song) = insert(song, order.size)

    // New songs go at the end of [songs]; [order] decides where they play
    private fun insert(song: Song, orderPosition: Int) {
        if (song.isOnline) return
        if (songs.isEmpty()) {
            playQueue(listOf(song), 0)
            return
        }
        songs = songs + song
        order = order.toMutableList().apply { add(orderPosition.coerceIn(0, size), songs.lastIndex) }
        publishQueue()
        saveQueue()
    }

    override fun moveQueueItem(from: Int, to: Int) {
        if (_isShuffleEnabled.value || from !in order.indices || to !in order.indices) return
        val current = order[position]
        order = order.toMutableList().apply { add(to, removeAt(from)) }
        position = order.indexOf(current)
        publishQueue()
        saveQueue()
    }

    override fun removeQueueItem(position: Int) {
        if (position !in order.indices) return
        if (order.size == 1) {
            stopPlayback()
            return
        }
        val removingCurrent = position == this.position
        order = order.toMutableList().apply { removeAt(position) }
        if (position < this.position || this.position > order.lastIndex) this.position--
        this.position = this.position.coerceIn(0, order.lastIndex)
        if (removingCurrent) load(autoPlay = _isPlaying.value) else publishQueue()
        saveQueue()
    }

    override fun skipToQueueItem(position: Int) {
        if (position !in order.indices) return
        this.position = position
        load(autoPlay = true)
    }

    private fun publishQueue() {
        _queue.value = order.map { QueueItem(songs[it], it) }
        _currentQueuePosition.value = if (order.isEmpty()) -1 else position
    }

    // There are no downloads on iOS
    override fun useDownloadedCopies(downloadIndex: DownloadIndex) = Unit

    // The player lives as long as the app
    override fun release() = Unit

    private fun rebuildOrder(startAt: Int) {
        order = if (_isShuffleEnabled.value) {
            listOf(startAt) + songs.indices.filter { it != startAt }.shuffled()
        } else {
            songs.indices.toList()
        }
        position = order.indexOf(startAt).coerceAtLeast(0)
        publishQueue()
    }

    private fun load(autoPlay: Boolean, startMs: Long = 0L) {
        val song = songs.getOrNull(order.getOrNull(position) ?: return) ?: return
        val url = NSURL.URLWithString(song.contentUri) ?: return
        player.replaceCurrentItemWithPlayerItem(AVPlayerItem(uRL = url))
        if (startMs > 0) player.seekToTime(CMTimeMakeWithSeconds(startMs / 1000.0, 600))
        _currentSong.value = song
        _duration.value = song.duration
        publishQueue()
        _currentPosition.value = startMs
        effects.onSongChanged(song)
        if (autoPlay) resume() else updateNowPlaying()
        saveQueue()
    }

    private fun resume() {
        AVAudioSession.sharedInstance().setActive(true, error = null)
        player.play()
        _isPlaying.value = true
        startProgressUpdates()
        updateNowPlaying()
    }

    private fun pause() {
        player.pause()
        _isPlaying.value = false
        progressJob?.cancel()
        updateNowPlaying()
        saveQueue()
    }

    private fun onSongFinished() {
        when {
            _repeatMode.value == RepeatMode.ONE -> {
                seekTo(0)
                player.play()
            }
            position < order.lastIndex || _repeatMode.value == RepeatMode.ALL -> skipToNext()
            else -> {
                pause()
                seekTo(0)
            }
        }
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = scope.launch {
            var ticks = 0
            while (isActive) {
                val seconds = CMTimeGetSeconds(player.currentTime())
                if (!seconds.isNaN()) _currentPosition.value = (seconds * 1000).toLong()
                // Save the position now and then, so the queue resumes close to where it stopped
                if (++ticks % 20 == 0) saveQueue()
                delay(500)
            }
        }
    }

    private fun updateNowPlaying() {
        val song = _currentSong.value ?: return
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = mapOf<Any?, Any?>(
            MPMediaItemPropertyTitle to song.title,
            MPMediaItemPropertyArtist to song.artist,
            MPMediaItemPropertyAlbumTitle to song.album,
            MPMediaItemPropertyPlaybackDuration to NSNumber(double = song.duration / 1000.0),
            MPNowPlayingInfoPropertyElapsedPlaybackTime to NSNumber(double = _currentPosition.value / 1000.0),
            MPNowPlayingInfoPropertyPlaybackRate to NSNumber(double = if (_isPlaying.value) 1.0 else 0.0)
        )
    }

    private fun setUpRemoteCommands() {
        val center = MPRemoteCommandCenter.sharedCommandCenter()
        center.playCommand.addTargetWithHandler { _ ->
            if (_currentSong.value == null) MPRemoteCommandHandlerStatusCommandFailed
            else { resume(); MPRemoteCommandHandlerStatusSuccess }
        }
        center.pauseCommand.addTargetWithHandler { _ -> pause(); MPRemoteCommandHandlerStatusSuccess }
        center.togglePlayPauseCommand.addTargetWithHandler { _ -> togglePlayPause(); MPRemoteCommandHandlerStatusSuccess }
        center.nextTrackCommand.addTargetWithHandler { _ -> skipToNext(); MPRemoteCommandHandlerStatusSuccess }
        center.previousTrackCommand.addTargetWithHandler { _ -> skipToPrevious(); MPRemoteCommandHandlerStatusSuccess }
        center.changePlaybackPositionCommand.addTargetWithHandler { event ->
            val target = event as? MPChangePlaybackPositionCommandEvent
            if (target == null) MPRemoteCommandHandlerStatusCommandFailed
            else { seekTo((target.positionTime * 1000).toLong()); MPRemoteCommandHandlerStatusSuccess }
        }
    }

    private fun saveQueue() {
        if (songs.isEmpty()) return
        // Saved in play order, so a shuffled queue resumes in the same order
        val ordered = order.map { songs[it] }
        queueStore.save(
            SavedQueue(
                songs = ordered.map { QueuedSong(it.id, it.title, it.artist, it.album, it.duration, it.contentUri, it.albumArtUri) },
                index = position,
                positionMs = _currentPosition.value,
                shuffle = _isShuffleEnabled.value,
                repeatMode = _repeatMode.value
            )
        )
    }

    /** Opening the app brings back the last queue, paused where it stopped (as on Android). */
    private fun restoreSavedQueue() {
        val saved = queueStore.load()?.withoutOnlineSongs() ?: return
        songs = saved.songs.map { it.toSong() }
        order = songs.indices.toList()
        position = saved.index.coerceIn(0, songs.lastIndex)
        _isShuffleEnabled.value = saved.shuffle
        _repeatMode.value = saved.repeatMode
        load(autoPlay = false, startMs = saved.positionMs)
    }
}
