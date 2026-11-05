package com.wildmusicplayer.service

import com.wildmusicplayer.data.forContext
import com.wildmusicplayer.data.getInstance
import android.app.PendingIntent
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
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
import androidx.media3.session.MediaSessionService
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
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
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
            updateFavoriteButton()
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

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = saveQueue()

        override fun onRepeatModeChanged(repeatMode: Int) = saveQueue()

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            saveQueue()
            updateWidget()
            mainHandler.removeCallbacks(periodicSave)
            if (isPlaying) mainHandler.postDelayed(periodicSave, SAVE_INTERVAL_MS)
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
        audioEffectsManager.attachPlayer(player)
        SleepTimerManager.attach(player, audioEffectsManager)
        crossfade = CrossfadeController(this, player, audioEffectsManager)

        player.addListener(playerListener)

        running = this
        mediaSession = MediaSession.Builder(this, CarRepeatPlayer(player))
            .setSessionActivity(sessionActivityPendingIntent)
            .setCallback(SessionCallback())
            .build()

        audioManager = getSystemService(AudioManager::class.java)
        audioManager?.registerAudioDeviceCallback(audioDeviceCallback, mainHandler)

        // The notification's heart follows favorites changed anywhere, in the app too
        playlists = PlaylistRepository.getInstance(this)
        serviceScope.launch { playlists.favoriteSongIds.collect { updateFavoriteButton() } }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
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
            isPlaying = player.isPlaying
        )
    }

    // Only songs on the phone can be favorites (online streams have no MediaStore ID)
    private fun currentSongId(): Long? =
        mediaSession?.player?.currentMediaItem?.mediaId?.toLongOrNull()?.takeIf { it > 0 }

    /** A ♡ button in the notification and lock screen player, filled when the song is a favorite. */
    private fun updateFavoriteButton() {
        val session = mediaSession ?: return
        val songId = currentSongId()
        val buttons = if (songId == null) {
            emptyList()
        } else {
            val isFavorite = songId in playlists.favoriteSongIds.value
            listOf(
                CommandButton.Builder(if (isFavorite) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED)
                    .setDisplayName(if (isFavorite) "Remove from favorites" else "Add to favorites")
                    .setSessionCommand(FAVORITE_COMMAND)
                    // An extra button, not instead of Next (the default slot would replace it)
                    .setSlots(CommandButton.SLOT_OVERFLOW)
                    .build()
            )
        }
        session.setMediaButtonPreferences(buttons)
    }

    // Empty queues aren't saved: "Stop" clears the saved queue on purpose (MusicControllerManager)
    private fun saveQueue() {
        val snapshot = mediaSession?.player?.snapshotQueue() ?: return
        if (saveExecutor.isShutdown) return
        saveExecutor.execute { queueStore.save(snapshot) }
    }

    private inner class SessionCallback : MediaSession.Callback {
        // Every controller (the notification, the lock screen, the app) may use the favorite button
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(FAVORITE_COMMAND)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == ACTION_TOGGLE_FAVORITE) {
                // The favorites flow then updates the button
                currentSongId()?.let { playlists.toggleFavorite(it) }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        /**
         * Called when a car radio or headset presses Play while nothing is loaded, including when
         * Android had to start this service from scratch because the app was closed.
         */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            if (!resumeSettings.resumeFromPlayButton) {
                return Futures.immediateFailedFuture(UnsupportedOperationException("Resume is turned off"))
            }
            val offline = NetworkPolicyManager.getInstance(this@PlaybackService).isOfflineOnlyMode
            val saved = queueStore.load()
                ?.preferDownloads(DownloadIndex.getInstance(this@PlaybackService))
                ?.let { if (offline) it.withoutOnlineSongs() else it }
                ?: return Futures.immediateFailedFuture(UnsupportedOperationException("Nothing to resume"))

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
        // The running service, for the widget's previous / next buttons (same process, main thread)
        @Volatile private var running: PlaybackService? = null

        /**
         * Play / pause for the Quick Settings tile. False when there's nothing loaded to play or pause
         * (the player isn't running, or its queue is empty): the tile then asks for the last queue to resume.
         */
        fun togglePlayPause(): Boolean {
            val player = running?.mediaSession?.player ?: return false
            if (player.mediaItemCount == 0) return false
            if (player.playWhenReady) player.pause() else player.play()
            return true
        }

        /** Skips in the playing queue; does nothing when the player isn't running (nothing to skip). */
        fun skip(next: Boolean) {
            val player = running?.mediaSession?.player ?: return
            if (next) player.seekToNext() else player.seekToPrevious()
        }

        private const val SAVE_INTERVAL_MS = 10_000L
        private const val PLAY_COUNT_AFTER_MS = 30_000L
        private const val RECONNECT_WINDOW_MS = 2 * 60 * 60 * 1000L
        private const val RECONNECT_PLAY_DELAY_MS = 1_500L
        // Long enough for the car's control link to come up after the audio one
        private const val CAR_REFRESH_DELAY_MS = 4_000L
        // The Qashqai misses repeat changes closer together than about 1-2 seconds
        private const val CAR_NUDGE_GAP_MS = 2_000L
        private const val CAR_NUDGE_MIN_LEFT_MS = 5_000L

        private const val ACTION_TOGGLE_FAVORITE = "com.wildmusicplayer.TOGGLE_FAVORITE"
        private val FAVORITE_COMMAND = SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY)
    }
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
