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
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
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

    private val mainHandler = Handler(Looper.getMainLooper())
    // Queue files are written off the main thread; a single thread keeps saves in order
    private val saveExecutor = Executors.newSingleThreadExecutor()

    // When playback paused because Bluetooth / headphones disconnected (0 = it didn't)
    private var pausedByDisconnectAt = 0L

    private val periodicSave = object : Runnable {
        override fun run() {
            saveQueue()
            mainHandler.postDelayed(this, SAVE_INTERVAL_MS)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            saveQueue()
            applyTrackLoudness(mediaItem)
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (reason != Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) return
            if (timeline.isEmpty) {
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

        player.addListener(playerListener)

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityPendingIntent)
            .setCallback(SessionCallback())
            .build()

        audioManager = getSystemService(AudioManager::class.java)
        audioManager?.registerAudioDeviceCallback(audioDeviceCallback, mainHandler)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        saveQueue()
        mainHandler.removeCallbacksAndMessages(null)
        audioManager?.unregisterAudioDeviceCallback(audioDeviceCallback)
        SleepTimerManager.detach()
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
     * missing after a reconnect. Once the car has connected, briefly flip it so the car hears it again.
     * (The song title is handled by the matching queue details in toMediaItem.)
     */
    private fun refreshCarDisplay() {
        mainHandler.postDelayed({
            val player = mediaSession?.player ?: return@postDelayed
            if (player.mediaItemCount == 0) return@postDelayed

            val repeatMode = player.repeatMode
            player.repeatMode = if (repeatMode == Player.REPEAT_MODE_OFF) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            mainHandler.postDelayed({ player.repeatMode = repeatMode }, CAR_NUDGE_GAP_MS)
        }, CAR_REFRESH_DELAY_MS)
    }

    // Empty queues aren't saved: "Stop" clears the saved queue on purpose (MusicControllerManager)
    private fun saveQueue() {
        val snapshot = mediaSession?.player?.snapshotQueue() ?: return
        if (saveExecutor.isShutdown) return
        saveExecutor.execute { queueStore.save(snapshot) }
    }

    private inner class SessionCallback : MediaSession.Callback {
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

            mediaSession.player.shuffleModeEnabled = saved.shuffle
            mediaSession.player.repeatMode = saved.repeatMode
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
        private const val SAVE_INTERVAL_MS = 10_000L
        private const val RECONNECT_WINDOW_MS = 2 * 60 * 60 * 1000L
        private const val RECONNECT_PLAY_DELAY_MS = 1_500L
        // Long enough for the car's control link to come up after the audio one
        private const val CAR_REFRESH_DELAY_MS = 4_000L
        private const val CAR_NUDGE_GAP_MS = 400L
    }
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
