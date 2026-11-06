package com.wildmusicplayer.service

import com.wildmusicplayer.data.upscaleGoogleThumbnail
import com.wildmusicplayer.platform.PlayerController
import com.wildmusicplayer.platform.QueueItem
import androidx.media3.common.C
import com.wildmusicplayer.data.forContext
import com.wildmusicplayer.data.getInstance
import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.wildmusicplayer.data.DownloadIndex
import com.wildmusicplayer.data.NetworkPolicyManager
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
class MusicControllerManager(private val context: Context) : PlayerController {

    private var mediaController: MediaController? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var progressJob: Job? = null

    // The player's items as songs, in the player's own (unshuffled) order
    private var currentPlaylist: List<Song> = emptyList()

    // Every song the app put in the queue, by media ID, so queue entries keep all their details
    private val knownSongs = mutableMapOf<String, Song>()

    private val _queue = MutableStateFlow<List<QueueItem>>(emptyList())
    override val queue: StateFlow<List<QueueItem>> = _queue.asStateFlow()

    private val _currentQueuePosition = MutableStateFlow(-1)
    override val currentQueuePosition: StateFlow<Int> = _currentQueuePosition.asStateFlow()

    // Last command issued before the controller finished connecting (e.g. a tap right after launch)
    private var pendingAction: ((MediaController) -> Unit)? = null

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

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    override val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

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
            val controller = controllerFuture.get()
            // Stored before reading its state: syncPlayerState() reads [mediaController]. (Reading it first
            // found nothing, so opening the app while music played from the widget, tile or car showed no
            // song or queue until the next song started.)
            mediaController = controller
            controller.addListener(PlayerListener())
            _isConnected.value = true
            syncPlayerState()

            val action = pendingAction
            pendingAction = null
            when {
                action != null -> action(controller)
                controller.mediaItemCount == 0 -> restoreSavedQueue(controller)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /** Opening the app with nothing loaded brings back the last queue, paused where it stopped. */
    private fun restoreSavedQueue(controller: MediaController) {
        val offline = NetworkPolicyManager.getInstance(context).isOfflineOnlyMode
        val saved = QueueStore.forContext(context).load()?.preferDownloads(DownloadIndex.getInstance(context))
            ?.let { if (offline) it.withoutOnlineSongs() else it }
            ?: return

        val songs = saved.songs.map { it.toSong() }
        remember(songs)
        currentPlaylist = songs
        controller.shuffleModeEnabled = saved.shuffle
        controller.repeatMode = saved.repeatMode
        controller.setMediaItems(songs.map { it.toMediaItem() }, saved.index, saved.positionMs)
        controller.prepare()
    }

    /** Runs [action] now, or right after the controller connects if it hasn't yet. */
    private fun withController(action: (MediaController) -> Unit) {
        val controller = mediaController
        if (controller != null) {
            action(controller)
        } else {
            pendingAction = action
        }
    }

    private fun syncPlayerState() {
        val controller = mediaController ?: return
        _isPlaying.value = controller.isPlaying
        _isShuffleEnabled.value = controller.shuffleModeEnabled
        _repeatMode.value = controller.repeatMode
        _duration.value = controller.duration.coerceAtLeast(0L)
        _currentPosition.value = controller.currentPosition.coerceAtLeast(0L)
        resolveCurrentSong(controller.currentMediaItem)
        refreshQueue()

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
        val song = songFor(mediaItem)
        _currentSong.value = if (song.duration > 0) song else song.copy(duration = mediaController?.duration?.coerceAtLeast(0L) ?: 0L)
    }

    /** The song behind a player item: the one the app queued, or one rebuilt from the item's metadata. */
    private fun songFor(mediaItem: MediaItem): Song =
        knownSongs[mediaItem.mediaId]
            ?: currentPlaylist.find { it.mediaId == mediaItem.mediaId }
            ?: Song(
                id = mediaItem.mediaId.toLongOrNull() ?: Song.ONLINE_SONG_ID,
                title = mediaItem.mediaMetadata.title?.toString() ?: "Unknown Title",
                artist = mediaItem.mediaMetadata.artist?.toString() ?: "Unknown Artist",
                album = mediaItem.mediaMetadata.albumTitle?.toString() ?: "Unknown Album",
                duration = 0L, // unknown until it plays
                contentUri = mediaItem.localConfiguration?.uri?.toString()
                    ?: mediaItem.requestMetadata.mediaUri?.toString() ?: "",
                albumArtUri = mediaItem.mediaMetadata.artworkUri?.toString()
            )

    private fun remember(songs: List<Song>) {
        songs.forEach { knownSongs[it.mediaId] = it }
    }

    /** Rebuilds [queue] from the player: its items in play order (the shuffled order when shuffle is on). */
    private fun refreshQueue() {
        val controller = mediaController ?: return
        val timeline = controller.currentTimeline
        if (timeline.isEmpty) {
            currentPlaylist = emptyList()
            _queue.value = emptyList()
            _currentQueuePosition.value = -1
            return
        }
        val songs = (0 until controller.mediaItemCount).map { songFor(controller.getMediaItemAt(it)) }
        currentPlaylist = songs

        val shuffle = controller.shuffleModeEnabled
        val order = mutableListOf<Int>()
        var index = timeline.getFirstWindowIndex(shuffle)
        while (index != C.INDEX_UNSET && order.size < timeline.windowCount) {
            order += index
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, shuffle)
        }
        _queue.value = order.mapNotNull { i -> songs.getOrNull(i)?.let { QueueItem(it, i) } }
        _currentQueuePosition.value = order.indexOf(controller.currentMediaItemIndex)
    }

    override fun playNext(song: Song) {
        remember(listOf(song))
        val controller = mediaController
        if (controller == null || controller.mediaItemCount == 0) {
            playQueue(listOf(song), 0)
            return
        }
        controller.addMediaItem(controller.currentMediaItemIndex + 1, song.toMediaItem())
    }

    override fun addToQueue(song: Song) {
        remember(listOf(song))
        val controller = mediaController
        if (controller == null || controller.mediaItemCount == 0) {
            playQueue(listOf(song), 0)
            return
        }
        controller.addMediaItem(song.toMediaItem())
    }

    override fun moveQueueItem(from: Int, to: Int) {
        val controller = mediaController ?: return
        // With shuffle on the queue isn't in list order, so moving there would land somewhere unexpected
        if (controller.shuffleModeEnabled) return
        val fromIndex = _queue.value.getOrNull(from)?.playerIndex ?: return
        val toIndex = _queue.value.getOrNull(to)?.playerIndex ?: return
        if (fromIndex != toIndex) controller.moveMediaItem(fromIndex, toIndex)
    }

    override fun removeQueueItem(position: Int) {
        val controller = mediaController ?: return
        val index = _queue.value.getOrNull(position)?.playerIndex ?: return
        if (controller.mediaItemCount <= 1) stopPlayback() else controller.removeMediaItem(index)
    }

    override fun restoreQueueItem(item: QueueItem) {
        val controller = mediaController ?: return
        if (item.song.contentUri.isEmpty()) return
        remember(listOf(item.song))
        controller.addMediaItem(item.playerIndex.coerceIn(0, controller.mediaItemCount), item.song.toMediaItem())
    }

    override fun skipToQueueItem(position: Int) {
        val controller = mediaController ?: return
        val index = _queue.value.getOrNull(position)?.playerIndex ?: return
        controller.seekTo(index, 0L)
        controller.play()
    }

    override fun playSong(song: Song, playlist: List<Song>) {
        val startIndex = playlist.indexOfFirst { it.mediaId == song.mediaId }.coerceAtLeast(0)
        playQueue(playlist, startIndex)
    }

    /**
     * Replaces the queue with [songs] and starts at [startIndex]. The queue may mix local files
     * and online songs ([Song.fromOnline]); online stream URLs are resolved by PlaybackService.
     */
    override fun playQueue(songs: List<Song>, startIndex: Int) {
        if (songs.isEmpty()) return
        remember(songs)
        currentPlaylist = songs
        val mediaItems = songs.map { it.toMediaItem() }
        val index = startIndex.coerceIn(0, songs.lastIndex)

        withController { controller ->
            controller.setMediaItems(mediaItems, index, 0L)
            controller.prepare()
            controller.play()
        }
    }

    fun playOnlineStream(title: String, artist: String, streamUrl: String, artworkUrl: String? = null) {
        val virtualSong = Song(
            id = Song.ONLINE_SONG_ID,
            title = title,
            artist = artist,
            album = "YouTube Stream",
            duration = 0L,
            contentUri = streamUrl,
            albumArtUri = artworkUrl?.let(::upscaleGoogleThumbnail)
        )
        _currentSong.value = virtualSong
        playQueue(listOf(virtualSong), 0)
    }

    override fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
    }

    override fun skipToNext() {
        mediaController?.seekToNextMediaItem()
    }

    override fun skipToPrevious() {
        mediaController?.seekToPreviousMediaItem()
    }

    override fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        _currentPosition.value = positionMs
    }

    override fun toggleShuffle() {
        val controller = mediaController ?: return
        controller.shuffleModeEnabled = !controller.shuffleModeEnabled
    }

    override fun toggleRepeatMode() {
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

    override fun release() {
        pendingAction = null
        stopProgressUpdates()
        mediaController?.release()
        mediaController = null
        _isConnected.value = false
    }

    /**
     * After a download finishes: online songs waiting in the queue that are now on the phone
     * play from the file instead of streaming. The song playing right now is left alone so it
     * isn't interrupted.
     */
    override fun useDownloadedCopies(downloadIndex: DownloadIndex) {
        val controller = mediaController ?: return
        val updated = currentPlaylist.toMutableList()
        var changed = false
        currentPlaylist.forEachIndexed { index, song ->
            if (!song.isOnline || index == controller.currentMediaItemIndex) return@forEachIndexed
            if (index >= controller.mediaItemCount || controller.getMediaItemAt(index).mediaId != song.mediaId) {
                return@forEachIndexed
            }
            val localId = Song.webUrlOf(song.contentUri)?.let { downloadIndex.downloadedIdFor(it) }
                ?: return@forEachIndexed
            val local = song.copy(id = localId, contentUri = localSongUri(localId).toString())
            knownSongs[local.mediaId] = local
            controller.replaceMediaItem(index, local.toMediaItem())
            updated[index] = local
            changed = true
        }
        if (changed) currentPlaylist = updated
    }

    /**
     * Completely stops current playback and clears the active queue,
     * removing the system media notification and dismissing the player bar
     * while keeping the controller connected for future plays.
     */
    override fun stopPlayback() {
        // Clearing the queue also makes PlaybackService forget the saved one, so the car won't bring it back
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
        // stop() keeps the queue and position, so PlaybackService saves them and the car's
        // Play button can resume after exiting (unlike stopPlayback, which forgets the queue)
        mediaController?.stop()
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

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            refreshQueue()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            refreshQueue()
            resolveCurrentSong(mediaItem)
            _duration.value = mediaController?.duration?.coerceAtLeast(0L) ?: 0L
            _currentPosition.value = mediaController?.currentPosition?.coerceAtLeast(0L) ?: 0L
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _isShuffleEnabled.value = shuffleModeEnabled
            refreshQueue()
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
