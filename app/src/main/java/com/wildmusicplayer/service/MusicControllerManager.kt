package com.wildmusicplayer.service

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.wildmusicplayer.data.toMediaItem
import com.wildmusicplayer.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Manages the client-side Media3 MediaController connection, binding the UI
 * to the background PlaybackService and providing reactive StateFlows for UI state.
 */
class MusicControllerManager(private val context: Context) {

    private var mediaController: MediaController? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var progressJob: Job? = null

    // Cache of the active playlist for metadata/song resolution
    private var currentPlaylist: List<Song> = emptyList()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    init {
        connectToService()
    }

    private fun connectToService() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()

        controllerFuture.addListener({
            mediaController = controllerFuture.get().apply {
                addListener(PlayerListener())
                _isConnected.value = true
                syncPlayerState()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun syncPlayerState() {
        val controller = mediaController ?: return
        _isPlaying.value = controller.isPlaying
        _isShuffleEnabled.value = controller.shuffleModeEnabled
        _repeatMode.value = controller.repeatMode
        _duration.value = controller.duration.coerceAtLeast(0L)
        _currentPosition.value = controller.currentPosition.coerceAtLeast(0L)
        resolveCurrentSong(controller.currentMediaItem)

        if (controller.isPlaying) {
            startProgressUpdates()
        } else {
            stopProgressUpdates()
        }
    }

    private fun resolveCurrentSong(mediaItem: MediaItem?) {
        if (mediaItem == null) {
            _currentSong.value = null
            return
        }
        val songId = mediaItem.mediaId.toLongOrNull()
        _currentSong.value = currentPlaylist.find { it.id == songId }
            ?: Song(
                id = songId ?: 0L,
                title = mediaItem.mediaMetadata.title?.toString() ?: "Unknown Title",
                artist = mediaItem.mediaMetadata.artist?.toString() ?: "Unknown Artist",
                album = mediaItem.mediaMetadata.albumTitle?.toString() ?: "Unknown Album",
                duration = mediaController?.duration?.coerceAtLeast(0L) ?: 0L,
                contentUri = mediaItem.requestMetadata.mediaUri ?: android.net.Uri.EMPTY,
                albumArtUri = mediaItem.mediaMetadata.artworkUri
            )
    }

    fun playSong(song: Song, playlist: List<Song>) {
        val controller = mediaController ?: return
        currentPlaylist = playlist

        val mediaItems = playlist.map { it.toMediaItem() }
        val startIndex = playlist.indexOfFirst { it.id == song.id }.coerceAtLeast(0)

        controller.setMediaItems(mediaItems, startIndex, 0L)
        controller.prepare()
        controller.play()
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
    }

    fun skipToNext() {
        mediaController?.seekToNextMediaItem()
    }

    fun skipToPrevious() {
        mediaController?.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        _currentPosition.value = positionMs
    }

    fun toggleShuffle() {
        val controller = mediaController ?: return
        controller.shuffleModeEnabled = !controller.shuffleModeEnabled
    }

    fun toggleRepeatMode() {
        val controller = mediaController ?: return
        val nextMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        controller.repeatMode = nextMode
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                mediaController?.let { controller ->
                    _currentPosition.value = controller.currentPosition.coerceAtLeast(0L)
                    _duration.value = controller.duration.coerceAtLeast(0L)
                }
                delay(500)
            }
        }
    }

    private fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }

    fun release() {
        stopProgressUpdates()
        mediaController?.release()
        mediaController = null
        _isConnected.value = false
    }

    /**
     * Completely stops current playback and clears the active queue,
     * removing the system media notification and dismissing the player bar
     * while keeping the controller connected for future plays.
     */
    fun stopPlayback() {
        stopProgressUpdates()
        mediaController?.stop()
        mediaController?.clearMediaItems()
        _currentSong.value = null
        _isPlaying.value = false
        _currentPosition.value = 0L
        _duration.value = 0L
    }

    fun stopAndShutdown() {
        stopProgressUpdates()
        mediaController?.stop()
        mediaController?.clearMediaItems()
        mediaController?.release()
        mediaController = null
        _isConnected.value = false
        _currentSong.value = null
        _isPlaying.value = false

        val serviceIntent = android.content.Intent(context, PlaybackService::class.java)
        context.stopService(serviceIntent)
    }


    private inner class PlayerListener : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            if (isPlaying) {
                startProgressUpdates()
            } else {
                stopProgressUpdates()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            resolveCurrentSong(mediaItem)
            _duration.value = mediaController?.duration?.coerceAtLeast(0L) ?: 0L
            _currentPosition.value = mediaController?.currentPosition?.coerceAtLeast(0L) ?: 0L
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _isShuffleEnabled.value = shuffleModeEnabled
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _repeatMode.value = repeatMode
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                _duration.value = mediaController?.duration?.coerceAtLeast(0L) ?: 0L
            }
        }
    }
}
