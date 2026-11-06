package com.wildmusicplayer.service

import com.wildmusicplayer.data.BrowseCatalog
import com.google.common.collect.ImmutableList
import androidx.media3.common.MediaMetadata
import androidx.media3.session.SessionError
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService
import com.wildmusicplayer.widget.PlaylistWidget
import androidx.core.content.ContextCompat
import android.widget.Toast
import android.view.KeyEvent
import android.content.Context
import kotlinx.coroutines.withContext
import com.wildmusicplayer.data.AudioRepository
import com.wildmusicplayer.data.AppSettings
import com.wildmusicplayer.data.PlayTarget
import com.google.common.util.concurrent.SettableFuture
import com.wildmusicplayer.data.forContext
import com.wildmusicplayer.data.getInstance
import android.app.PendingIntent
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import com.wildmusicplayer.data.PlaylistRepository
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionCommand
import androidx.media3.session.CommandButton
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.wildmusicplayer.MainActivity
import com.wildmusicplayer.widget.PlayerWidget
import com.wildmusicplayer.data.DownloadIndex
import com.wildmusicplayer.data.LoudnessStore
import com.wildmusicplayer.data.NetworkPolicyManager
import com.wildmusicplayer.data.ResumeSettings
import com.wildmusicplayer.data.YouTubeDownloadManager
import com.wildmusicplayer.data.toMediaItem
import com.wildmusicplayer.model.Song
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Background MediaSessionService providing continuous audio playback,
 * Bluetooth AVRCP metadata synchronization, audio focus handling (GPS ducking),
 * and becoming-noisy protection for car audio systems (e.g., 2017 Nissan Qashqai).
 */
class PlaybackService : MediaLibraryService() {

    private var mediaSession: MediaLibrarySession? = null
    private lateinit var queueStore: QueueStore
    private lateinit var resumeSettings: ResumeSettings
    private var audioManager: AudioManager? = null

    private lateinit var playlists: PlaylistRepository
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val mainHandler = Handler(Looper.getMainLooper())
    // Queue files are written off the main thread; a single thread keeps saves in order
    private val saveExecutor = Executors.newSingleThreadExecutor()

    // When playback paused because Bluetooth / headphones disconnected (0 = it didn't)
    private var pausedByDisconnectAt = 0L

    // The song waiting to count as played (null = already counted, or nothing playing)
    private var playCountCandidate: Long? = null

    private var crossfade: CrossfadeController? = null

    // The real player, under the car's repeat handling (the notification's buttons are the app's own)
    private lateinit var exoPlayer: ExoPlayer

    // While playing, the widget's progress line moves on every few seconds
    private val widgetProgress = object : Runnable {
        override fun run() {
            updateWidget()
            mainHandler.postDelayed(this, WIDGET_PROGRESS_MS)
        }
    }

    private val periodicSave = object : Runnable {
        override fun run() {
            saveQueue()
            checkPlayCount()
            mainHandler.postDelayed(this, SAVE_INTERVAL_MS)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            saveQueue()
            applyTrackLoudness(mediaItem)
            updateNotificationButtons()
            updateWidget()
            playCountCandidate = currentSongId()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (reason != Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) return
            if (timeline.isEmpty) {
                updateWidget()
                // The queue was cleared on purpose (Stop): forget it, after any save still pending
                if (!saveExecutor.isShutdown) saveExecutor.execute { queueStore.clear() }
            } else {
                saveQueue()
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            saveQueue()
            updateNotificationButtons()
            updateWidget()
        }

        // A seek moves the widget's progress line at once
        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) updateWidget()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            saveQueue()
            updateNotificationButtons()
            updateWidget()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            saveQueue()
            updateWidget()
            mainHandler.removeCallbacks(periodicSave)
            mainHandler.removeCallbacks(widgetProgress)
            if (isPlaying) {
                mainHandler.postDelayed(periodicSave, SAVE_INTERVAL_MS)
                if (PlayerWidget.hasWidgets(this@PlaybackService)) mainHandler.postDelayed(widgetProgress, WIDGET_PROGRESS_MS)
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            pausedByDisconnectAt = when {
                playWhenReady -> 0L
                reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> SystemClock.elapsedRealtime()
                else -> 0L
            }
        }

        // A deleted file or an online track that can't be extracted shouldn't stop the whole queue
        override fun onPlayerError(error: PlaybackException) {
            val player = mediaSession?.player ?: return
            if (player.playWhenReady && player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.prepare()
            }
        }
    }

    // Car / headphones came back: continue if they were what paused us, not long ago
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            val bluetoothAudio = addedDevices.any { it.isSink && it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
            if (!bluetoothAudio) return
            refreshCarDisplay()
            if (!resumeSettings.resumeOnBluetoothReconnect) return
            val pausedAt = pausedByDisconnectAt
            if (pausedAt == 0L || SystemClock.elapsedRealtime() - pausedAt > RECONNECT_WINDOW_MS) return
            val player = mediaSession?.player ?: return
            if (player.mediaItemCount == 0 || player.isPlaying) return

            pausedByDisconnectAt = 0L
            // Give the car a moment to switch its audio over to the phone
            mainHandler.postDelayed({ player.play() }, RECONNECT_PLAY_DELAY_MS)
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        queueStore = QueueStore.forContext(this)
        resumeSettings = ResumeSettings.forContext(this)

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        // Online songs in the queue carry a wmp-yt:// URI; it is swapped for a fresh
        // stream URL only when ExoPlayer reaches that item, so hybrid playlists play straight through.
        val dataSourceFactory = ResolvingDataSource.Factory(
            DefaultDataSource.Factory(this),
            OnlineStreamResolver(YouTubeDownloadManager(applicationContext))
        )

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            // "Play next" and "Add to queue" keep their place even with shuffle on
            .apply { setShuffleOrder(PlayNextShuffleOrder()) }

        val sessionActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            sessionActivityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val audioEffectsManager = com.wildmusicplayer.data.AudioEffectsManager.getInstance(this)
        exoPlayer = player
        audioEffectsManager.attachPlayer(player)
        SleepTimerManager.attach(player, audioEffectsManager)
        crossfade = CrossfadeController(this, player, audioEffectsManager)

        player.addListener(playerListener)

        running = this
        // A library session: cars (Android Auto, radios that browse over Bluetooth) can list the music too
        mediaSession = MediaLibrarySession.Builder(this, CarRepeatPlayer(player), SessionCallback())
            .setSessionActivity(sessionActivityPendingIntent)
            .build()

        audioManager = getSystemService(AudioManager::class.java)
        audioManager?.registerAudioDeviceCallback(audioDeviceCallback, mainHandler)

        // The notification's heart follows favorites changed anywhere, in the app too
        playlists = PlaylistRepository.getInstance(this)
        serviceScope.launch {
            playlists.favoriteSongIds.collect {
                updateNotificationButtons()
                updateWidget()
            }
        }

        // Offline Mode switched on: stop streaming right away
        serviceScope.launch {
            NetworkPolicyManager.getInstance(this@PlaybackService).offlineOnlyMode.drop(1).collect { offline ->
                if (offline) dropOnlineSongs()
            }
        }
    }

    /**
     * Offline Mode: online songs leave the queue (one playing now too: the next song plays instead). Songs
     * downloaded meanwhile stay, playing from the file. With nothing left the player stops.
     */
    private fun dropOnlineSongs() {
        if (!::exoPlayer.isInitialized) return
        val downloads = DownloadIndex.getInstance(this)
        for (i in exoPlayer.mediaItemCount - 1 downTo 0) {
            val item = exoPlayer.getMediaItemAt(i)
            val uri = item.localConfiguration?.uri?.toString() ?: continue
            // Songs on the phone are content:// (or file://); anything else needs the internet, including
            // the Downloader's "Preview online", which plays the stream's web address directly
            if (uri.startsWith("content:") || uri.startsWith("file:")) continue
            val localId = Song.webUrlOf(uri)?.let { downloads.downloadedIdFor(it) }
            if (localId != null) {
                exoPlayer.replaceMediaItem(i, item.buildUpon().setMediaId(localId.toString()).setUri(localSongUri(localId)).build())
            } else {
                exoPlayer.removeMediaItem(i)
            }
        }
        if (exoPlayer.mediaItemCount == 0) exoPlayer.stop()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaSession
    }

    override fun onDestroy() {
        saveQueue()
        mainHandler.removeCallbacksAndMessages(null)
        audioManager?.unregisterAudioDeviceCallback(audioDeviceCallback)
        SleepTimerManager.detach()
        crossfade?.release()
        crossfade = null
        serviceScope.cancel()
        PlayerWidget.markPaused(this)
        running = null
        com.wildmusicplayer.data.AudioEffectsManager.getInstance(this).release()
        mediaSession?.run {
            player.removeListener(playerListener)
            player.release()
            release()
            mediaSession = null
        }
        saveExecutor.shutdown() // finishes the last queued save first
        super.onDestroy()
    }

    // Normalization: tell the effects the measured loudness of the song that just started.
    // Local songs have numeric MediaStore IDs; online streams don't and play unadjusted.
    private fun applyTrackLoudness(mediaItem: MediaItem?) {
        val songId = mediaItem?.mediaId?.toLongOrNull()
        val loudness = songId?.let { LoudnessStore.getInstance(this).get(it) }
        com.wildmusicplayer.data.AudioEffectsManager.getInstance(this).setCurrentTrackLoudness(loudness)
    }

    /**
     * Car radios (the Qashqai, at least) only hear the repeat setting when it changes, so RPT can be
     * missing after a reconnect. RPT means "repeat this song" there, so only then: switch repeat off and,
     * after a pause long enough for the radio (it misses quicker changes), back on. Not in a song's last
     * seconds, where repeat being off for a moment would let it move on to the next song.
     * (The song title is handled by the matching queue details in toMediaItem.)
     */
    private fun refreshCarDisplay() {
        mainHandler.postDelayed({
            val player = mediaSession?.player ?: return@postDelayed
            if (player.mediaItemCount == 0 || player.repeatMode != Player.REPEAT_MODE_ONE) return@postDelayed
            if (player.duration != C.TIME_UNSET && player.duration - player.currentPosition < CAR_NUDGE_MIN_LEFT_MS) {
                return@postDelayed
            }

            player.repeatMode = Player.REPEAT_MODE_OFF
            mainHandler.postDelayed({
                // Unless someone changed it meanwhile
                if (player.repeatMode == Player.REPEAT_MODE_OFF) player.repeatMode = Player.REPEAT_MODE_ONE
            }, CAR_NUDGE_GAP_MS)
        }, CAR_REFRESH_DELAY_MS)
    }

    /**
     * The car's RPT button switches between off and repeat-one (see [outsideRepeatRequest]). Any other app
     * or device asking for repeat gets the same; the app's own repeat button keeps off / all / one.
     */
    private inner class CarRepeatPlayer(player: ExoPlayer) : ForwardingPlayer(player) {
        override fun setRepeatMode(repeatMode: Int) {
            val requester = mediaSession?.controllerForCurrentRequest?.packageName
            val fromOutside = requester != null && requester != packageName
            super.setRepeatMode(if (fromOutside) outsideRepeatRequest(this.repeatMode, repeatMode) else repeatMode)
        }
    }

    /**
     * A song counts as played for "Most Played" after 30 seconds, or half of a shorter song. Checked with
     * the regular queue save (every 10 s while playing), so it also counts with the app closed.
     */
    private fun checkPlayCount() {
        val player = mediaSession?.player ?: return
        val songId = currentSongId() ?: return
        if (songId != playCountCandidate) return
        val needed = player.duration.takeIf { it > 0 }?.let { minOf(PLAY_COUNT_AFTER_MS, it / 2) } ?: PLAY_COUNT_AFTER_MS
        if (player.currentPosition >= needed) {
            playlists.countPlay(songId)
            playCountCandidate = null
        }
    }

    /** Tells the home-screen widget what's playing now (or that nothing is). */
    private fun updateWidget() {
        val player = mediaSession?.player ?: return
        val metadata = player.currentMediaItem?.mediaMetadata
        PlayerWidget.update(
            context = this,
            title = metadata?.title?.toString(),
            artist = metadata?.artist?.toString(),
            artworkUri = metadata?.artworkUri?.toString(),
            isPlaying = player.isPlaying,
            songId = currentSongId(),
            shuffle = player.shuffleModeEnabled,
            // The user's own mode (the car's RPT sees a translated one through CarRepeatPlayer)
            repeatMode = if (::exoPlayer.isInitialized) exoPlayer.repeatMode else player.repeatMode,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.takeIf { it > 0 } ?: 0L
        )
    }

    // Only songs on the phone can be favorites (online streams have no MediaStore ID)
    private fun currentSongId(): Long? =
        mediaSession?.player?.currentMediaItem?.mediaId?.toLongOrNull()?.takeIf { it > 0 }

    /**
     * Extra buttons in the notification and lock screen player, next to previous / play / next: shuffle,
     * repeat (off → all → one, like the app's own button) and a ♡, filled when the song is a favorite.
     * Android fits two next to Next, so the ♡ shows only when there's room (the user's choice in 0.9.5).
     */
    private fun updateNotificationButtons() {
        val session = mediaSession ?: return
        if (!::exoPlayer.isInitialized) return
        val buttons = mutableListOf<CommandButton>()
        if (exoPlayer.mediaItemCount > 0) {
            val shuffle = exoPlayer.shuffleModeEnabled
            buttons += extraButton(
                if (shuffle) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF,
                if (shuffle) "Shuffle on" else "Shuffle off",
                SHUFFLE_COMMAND
            )
            buttons += extraButton(
                when (exoPlayer.repeatMode) {
                    Player.REPEAT_MODE_ONE -> CommandButton.ICON_REPEAT_ONE
                    Player.REPEAT_MODE_ALL -> CommandButton.ICON_REPEAT_ALL
                    else -> CommandButton.ICON_REPEAT_OFF
                },
                when (exoPlayer.repeatMode) {
                    Player.REPEAT_MODE_ONE -> "Repeat this song"
                    Player.REPEAT_MODE_ALL -> "Repeat all"
                    else -> "Repeat off"
                },
                REPEAT_COMMAND
            )
        }
        // Third: shown only where there's room (at the end of the queue, without Next)
        currentSongId()?.let { songId ->
            val isFavorite = songId in playlists.favoriteSongIds.value
            buttons += extraButton(
                if (isFavorite) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED,
                if (isFavorite) "Remove from favorites" else "Add to favorites",
                FAVORITE_COMMAND
            )
        }
        session.setMediaButtonPreferences(buttons)
    }

    // Extra buttons, not instead of Next (the default slot would replace it)
    @OptIn(UnstableApi::class)
    private fun extraButton(icon: Int, name: String, command: SessionCommand) =
        CommandButton.Builder(icon)
            .setDisplayName(name)
            .setSessionCommand(command)
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build()

    // What the last press from the tile or the playlist widget asked for (see [startFromOutside])
    private class OutsideRequest(val target: String, val shuffle: Boolean)
    private var outsideRequest: OutsideRequest? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val target = intent?.getStringExtra(EXTRA_START_TARGET)
        if (target != null && ::exoPlayer.isInitialized) {
            if (target != START_RESUME) {
                // A playlist replaces what's loaded: with the player empty, the Play press handled below
                // asks onPlaybackResumption for the songs
                exoPlayer.clearMediaItems()
                outsideRequest = OutsideRequest(target, intent.getBooleanExtra(EXTRA_START_SHUFFLE, false))
            } else if (exoPlayer.mediaItemCount == 0) {
                // "Resume" with something loaded just plays it
                outsideRequest = OutsideRequest(target, shuffle = true)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    /** Nothing to resume: the playlist on the playlist widget (set or pressed last), else everything shuffled. */
    private fun nothingToResume(): OutsideRequest {
        val (target, shuffle) = PlaylistWidget.fallbackChoice(this) ?: (PlayTarget.ShuffleAll to true)
        return OutsideRequest(target.key, shuffle)
    }

    /** The saved queue to pick up again (without online songs in Offline Mode), or null when there's none. */
    private fun savedQueueToResume(): SavedQueue? {
        val offline = NetworkPolicyManager.getInstance(this).isOfflineOnlyMode
        return queueStore.load()
            ?.preferDownloads(DownloadIndex.getInstance(this))
            ?.let { if (offline) it.withoutOnlineSongs() else it }
            ?.takeIf { it.songs.isNotEmpty() }
    }

    /**
     * The songs for a press from the tile, the car or the playlist widget: the last queue ([START_RESUME])
     * or a [PlayTarget]. No saved queue: the playlist widget's playlist ([nothingToResume]). An empty
     * playlist: the whole library shuffled. So a press always plays something; it only fails with no songs.
     */
    private fun songsFor(request: OutsideRequest): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        if (request.target == START_RESUME) {
            val saved = savedQueueToResume() ?: return songsFor(nothingToResume())
            exoPlayer.shuffleModeEnabled = saved.shuffle
            exoPlayer.repeatMode = saved.repeatMode
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(saved.songs.map { it.toSong().toMediaItem() }, saved.index, saved.positionMs)
            )
        }
        val result = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
        serviceScope.launch {
            val settings = AppSettings.getInstance(this@PlaybackService)
            val library = withContext(Dispatchers.IO) {
                AudioRepository(this@PlaybackService).getLocalSongs(settings.filterShortAudio.value, settings.filterRecordings.value)
            }
            val target = PlayTarget.fromKey(request.target) ?: PlayTarget.ShuffleAll
            val downloads = DownloadIndex.getInstance(this@PlaybackService)
            val offline = NetworkPolicyManager.getInstance(this@PlaybackService).isOfflineOnlyMode
            var songs = target.songs(
                library, playlists.favoriteSongIds.value, playlists.playCounts.value, playlists.customPlaylists.value,
                downloadedIdFor = { downloads.downloadedIdFor(it) },
                offline = offline
            )
            var shuffle = request.shuffle || target == PlayTarget.ShuffleAll
            if (songs.isEmpty() && library.isNotEmpty()) {
                if (target != PlayTarget.ShuffleAll) {
                    val name = target.label(playlists.customPlaylists.value)
                    Toast.makeText(this@PlaybackService, "Nothing in $name yet, shuffling all songs", Toast.LENGTH_SHORT).show()
                }
                songs = library
                shuffle = true
            }
            if (songs.isEmpty()) {
                result.setException(UnsupportedOperationException("No songs"))
                return@launch
            }
            // Shuffled like the app's own Shuffle buttons; "in order" really plays in order
            if (!shuffle) exoPlayer.shuffleModeEnabled = false
            result.set(
                MediaSession.MediaItemsWithStartPosition((if (shuffle) songs.shuffled() else songs).map { it.toMediaItem() }, 0, 0L)
            )
        }
        return result
    }

    // Empty queues aren't saved: "Stop" clears the saved queue on purpose (MusicControllerManager)
    private fun saveQueue() {
        val snapshot = mediaSession?.player?.snapshotQueue() ?: return
        if (saveExecutor.isShutdown) return
        saveExecutor.execute { queueStore.save(snapshot) }
    }

    // ---- The car's music browser (Android Auto) ----

    // The library is read again at most every half minute while the car browses
    private var libraryCache: List<Song> = emptyList()
    private var libraryReadAt = 0L

    private suspend fun libraryForBrowsing(): List<Song> {
        val now = SystemClock.elapsedRealtime()
        if (libraryCache.isEmpty() || now - libraryReadAt > LIBRARY_CACHE_MS) {
            val settings = AppSettings.getInstance(this)
            libraryCache = withContext(Dispatchers.IO) {
                AudioRepository(this@PlaybackService).getLocalSongs(settings.filterShortAudio.value, settings.filterRecordings.value)
            }
            libraryReadAt = now
        }
        return libraryCache
    }

    private suspend fun catalog(): BrowseCatalog {
        val downloads = DownloadIndex.getInstance(this)
        // "Continue": what's loaded now, or the queue that would be picked up again
        val continueTitle = if (exoPlayer.mediaItemCount > 0) {
            exoPlayer.currentMediaItem?.mediaMetadata?.title?.toString()
        } else {
            savedQueueToResume()?.let { it.songs.getOrNull(it.index)?.title }
        }
        return BrowseCatalog(
            library = libraryForBrowsing(),
            favoriteIds = playlists.favoriteSongIds.value,
            playCounts = playlists.playCounts.value,
            playlists = playlists.customPlaylists.value,
            recentIds = playlists.recentlyPlayedIds.value,
            downloadedIdFor = { downloads.downloadedIdFor(it) },
            offline = NetworkPolicyManager.getInstance(this).isOfflineOnlyMode,
            continueTitle = continueTitle
        )
    }

    private fun artworkFor(artUri: String?): Uri? = ArtworkProvider.uriFor(this, artUri)

    /** Runs [block] off the binder thread and hands its result to Media3. */
    private fun <T> async(block: suspend () -> T): ListenableFuture<T> {
        val result = SettableFuture.create<T>()
        serviceScope.launch {
            try {
                result.set(block())
            } catch (e: Exception) {
                result.setException(e)
            }
        }
        return result
    }

    /**
     * What a tap, a voice request or a search from the car plays. Rows from the browser start their whole
     * list; "Continue" (or "play music on Wild Music") picks up what's loaded or the last queue.
     * Null when it isn't one of those (the app's own requests carry their songs already).
     */
    private suspend fun carRequest(item: MediaItem): MediaSession.MediaItemsWithStartPosition? {
        val query = item.requestMetadata.searchQuery
        val id = item.mediaId
        val catalog = catalog()
        val play = when {
            query != null -> catalog.playFromSearch(query) ?: return continueOrStart()
            id == BrowseCatalog.CONTINUE -> return continueOrStart()
            id.startsWith("song|") || id.startsWith("shuffle|") -> catalog.play(id)
                ?: throw IllegalStateException("That song isn't there anymore")
            else -> return null
        }
        // A Shuffle row is shuffled already, like the app's own Shuffle buttons
        if (play.shuffled) exoPlayer.shuffleModeEnabled = false
        return MediaSession.MediaItemsWithStartPosition(play.songs.map { it.toMediaItem() }, play.startIndex, 0L)
    }

    /** "Continue": the loaded queue where it is, else the saved one, else the widget's playlist / everything. */
    private suspend fun continueOrStart(): MediaSession.MediaItemsWithStartPosition {
        if (exoPlayer.mediaItemCount > 0) {
            val items = (0 until exoPlayer.mediaItemCount).map { exoPlayer.getMediaItemAt(it) }
            return MediaSession.MediaItemsWithStartPosition(items, exoPlayer.currentMediaItemIndex, exoPlayer.currentPosition)
        }
        val saved = savedQueueToResume()
        if (saved != null) {
            exoPlayer.shuffleModeEnabled = saved.shuffle
            exoPlayer.repeatMode = saved.repeatMode
            return MediaSession.MediaItemsWithStartPosition(saved.songs.map { it.toSong().toMediaItem() }, saved.index, saved.positionMs)
        }
        return songsFor(nothingToResume()).awaitResult()
    }

    private inner class SessionCallback : MediaLibrarySession.Callback {
        // Every controller (the notification, the lock screen, the app) may use the favorite button
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(FAVORITE_COMMAND)
                .add(SHUFFLE_COMMAND)
                .add(REPEAT_COMMAND)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            // The car's "recent" suggestion: just the Continue row
            val rootId = if (params?.isRecent == true) RECENT_ROOT else BrowseCatalog.ROOT
            val root = MediaItem.Builder()
                .setMediaId(rootId)
                .setMediaMetadata(
                    MediaMetadata.Builder().setTitle("Wild Music").setIsBrowsable(true).setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).build()
                )
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(root, rootParams()))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = async {
            val catalog = catalog()
            val rows = if (parentId == RECENT_ROOT) {
                catalog.item(BrowseCatalog.CONTINUE)?.let { listOf(it) }.orEmpty()
            } else {
                catalog.children(parentId)
            }
            if (rows == null) {
                LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            } else {
                LibraryResult.ofItemList(rows.page(page, pageSize).map { it.toMediaItem(::artworkFor) }, params)
            }
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> = async {
            val node = catalog().item(mediaId)
            if (node == null) LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            else LibraryResult.ofItem(node.toMediaItem(::artworkFor), null)
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> = async {
            session.notifySearchResultChanged(browser, query, catalog().search(query).size, params)
            LibraryResult.ofVoid()
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = async {
            LibraryResult.ofItemList(catalog().search(query).page(page, pageSize).map { it.toMediaItem(::artworkFor) }, params)
        }

        /**
         * The car taps a row, asks by voice or picks a search result: it sends only an ID or a query, which
         * become the songs to play. The app's own requests carry their songs (with addresses) and go through.
         */
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val single = mediaItems.singleOrNull()
            if (single == null || single.localConfiguration != null) {
                return super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)
            }
            return async {
                carRequest(single)
                    ?: super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs).awaitResult()
            }
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                // The favorites flow then updates the button
                ACTION_TOGGLE_FAVORITE -> currentSongId()?.let { playlists.toggleFavorite(it) }
                ACTION_TOGGLE_SHUFFLE -> exoPlayer.shuffleModeEnabled = !exoPlayer.shuffleModeEnabled
                // Straight to the player: these are the app's own buttons, not the car's RPT
                ACTION_CYCLE_REPEAT -> exoPlayer.repeatMode = nextRepeatMode(exoPlayer.repeatMode)
                else -> return super.onCustomCommand(session, controller, customCommand, args)
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        /**
         * Called when Play is pressed while nothing is loaded, including when Android had to start this
         * service from scratch because the app was closed: by a car radio or headset (the last queue, if
         * "Resume with Play Button" is on), or by the tile or the playlist widget ([startFromOutside]).
         */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            outsideRequest?.let { request ->
                outsideRequest = null
                return songsFor(request)
            }
            if (!resumeSettings.resumeFromPlayButton) {
                return Futures.immediateFailedFuture(UnsupportedOperationException("Resume is turned off"))
            }
            // Nothing saved (after Stop, or a fresh install): the widget's playlist or everything shuffled,
            // rather than failing, which leaves Android waiting for the player ("isn't responding")
            val saved = savedQueueToResume() ?: return songsFor(nothingToResume())

            // Straight to the player: the saved repeat mode is the user's, not a car's request
            val player = (mediaSession.player as? ForwardingPlayer)?.wrappedPlayer ?: mediaSession.player
            player.shuffleModeEnabled = saved.shuffle
            player.repeatMode = saved.repeatMode
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(
                    saved.songs.map { it.toSong().toMediaItem() },
                    saved.index,
                    saved.positionMs
                )
            )
        }
    }

    companion object {
        /** For [startFromOutside]: play the last queue again (or shuffle everything when there's none). */
        const val START_RESUME = "resume"
        private const val EXTRA_START_TARGET = "com.wildmusicplayer.START_TARGET"
        private const val EXTRA_START_SHUFFLE = "com.wildmusicplayer.START_SHUFFLE"

        /**
         * Starts playback from outside the app (the Quick Settings tile, the playlist widget), the way a
         * headset's Play button does: Android starts this service in the foreground for it. It must be in
         * the foreground (or the app on screen) to get audio focus; asked any other way from the background,
         * Android 15 refuses and the music stays paused. [target] is a [PlayTarget] key or [START_RESUME].
         * False when Android didn't allow the start.
         */
        fun startFromOutside(context: Context, target: String, shuffle: Boolean = false): Boolean {
            val intent = Intent(context, PlaybackService::class.java)
                .setAction(Intent.ACTION_MEDIA_BUTTON)
                .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
                .putExtra(EXTRA_START_TARGET, target)
                .putExtra(EXTRA_START_SHUFFLE, shuffle)
            return try {
                ContextCompat.startForegroundService(context, intent)
                true
            } catch (e: IllegalStateException) {
                false
            }
        }

        /** True while the player is playing (or about to). */
        fun isPlaying(): Boolean {
            val player = running?.mediaSession?.player ?: return false
            return player.mediaItemCount > 0 && player.playWhenReady
        }

        /** Pauses the player, if it's running. */
        fun pause() {
            running?.mediaSession?.player?.pause()
        }

        // The car's "recent" root (its suggestion card), and how long the browsed library stays fresh
        private const val RECENT_ROOT = "recent_root"
        private const val LIBRARY_CACHE_MS = 30_000L

        // The running service, for the widget's previous / next buttons (same process, main thread)
        @Volatile private var running: PlaybackService? = null

        /** Shuffle on / off for the widget; nothing when the player isn't running. */
        fun toggleShuffle() {
            val service = running ?: return
            if (!service::exoPlayer.isInitialized) return
            service.exoPlayer.shuffleModeEnabled = !service.exoPlayer.shuffleModeEnabled
        }

        /** Repeat off -> all -> one for the widget; nothing when the player isn't running. */
        fun cycleRepeat() {
            val service = running ?: return
            if (!service::exoPlayer.isInitialized) return
            service.exoPlayer.repeatMode = nextRepeatMode(service.exoPlayer.repeatMode)
        }

        /** Skips in the playing queue; does nothing when the player isn't running (nothing to skip). */
        fun skip(next: Boolean) {
            val player = running?.mediaSession?.player ?: return
            if (next) player.seekToNext() else player.seekToPrevious()
        }

        private const val SAVE_INTERVAL_MS = 10_000L
        private const val WIDGET_PROGRESS_MS = 5_000L
        private const val PLAY_COUNT_AFTER_MS = 30_000L
        private const val RECONNECT_WINDOW_MS = 2 * 60 * 60 * 1000L
        private const val RECONNECT_PLAY_DELAY_MS = 1_500L
        // Long enough for the car's control link to come up after the audio one
        private const val CAR_REFRESH_DELAY_MS = 4_000L
        // The Qashqai misses repeat changes closer together than about 1-2 seconds
        private const val CAR_NUDGE_GAP_MS = 2_000L
        private const val CAR_NUDGE_MIN_LEFT_MS = 5_000L

        private const val ACTION_TOGGLE_FAVORITE = "com.wildmusicplayer.TOGGLE_FAVORITE"
        private const val ACTION_TOGGLE_SHUFFLE = "com.wildmusicplayer.TOGGLE_SHUFFLE"
        private const val ACTION_CYCLE_REPEAT = "com.wildmusicplayer.CYCLE_REPEAT"
        private val FAVORITE_COMMAND = SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY)
        private val SHUFFLE_COMMAND = SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY)
        private val REPEAT_COMMAND = SessionCommand(ACTION_CYCLE_REPEAT, Bundle.EMPTY)
    }
}

/** The app's own repeat button order: off -> all -> one -> off. */
internal fun nextRepeatMode(current: Int): Int = when (current) {
    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
    else -> Player.REPEAT_MODE_OFF
}

/**
 * What a repeat request from outside the app (the car's RPT button) becomes. The Qashqai's RPT only ever asks
 * for "repeat all" or "off", and it decides by itself: "off" when repeat-all is on, otherwise "repeat all". It
 * only shows RPT for repeat-one, so "repeat all" turns repeat-one on, and asking for it again while repeat-one
 * is on is the second press: off. (v0.9.3 kept answering repeat-one, so RPT could never be switched off.)
 */
internal fun outsideRepeatRequest(current: Int, requested: Int): Int = when {
    requested == Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_OFF
    requested == Player.REPEAT_MODE_ALL && current == Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
    else -> Player.REPEAT_MODE_ONE
}

/**
 * Resolves [Song.ONLINE_SCHEME] URIs to playable YouTube audio stream URLs.
 * Runs on ExoPlayer's loader thread, so the blocking extraction is fine here.
 * Resolved URLs are cached for a while because YouTube stream URLs expire after a few hours.
 */
@OptIn(UnstableApi::class)
private class OnlineStreamResolver(
    private val downloadManager: YouTubeDownloadManager
) : ResolvingDataSource.Resolver {

    private val cache = ConcurrentHashMap<String, Pair<String, Long>>()

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val webUrl = Song.webUrlOf(dataSpec.uri.toString()) ?: return dataSpec

        val now = SystemClock.elapsedRealtime()
        val cached = cache[webUrl]?.takeIf { now - it.second < CACHE_TTL_MS }?.first
        if (cached != null) {
            return dataSpec.withUri(Uri.parse(cached))
        }

        val streamUrl = try {
            downloadManager.resolveStreamUrlBlocking(webUrl)
        } catch (e: Exception) {
            throw IOException("Unable to extract stream for $webUrl", e)
        } ?: throw IOException("No audio stream available for $webUrl")

        cache[webUrl] = streamUrl to now
        return dataSpec.withUri(Uri.parse(streamUrl))
    }

    companion object {
        private const val CACHE_TTL_MS = 60 * 60 * 1000L
    }
}
