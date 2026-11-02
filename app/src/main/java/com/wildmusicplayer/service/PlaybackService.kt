package com.wildmusicplayer.service

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.wildmusicplayer.MainActivity
import com.wildmusicplayer.data.YouTubeDownloadManager
import com.wildmusicplayer.model.Song
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Background MediaSessionService providing continuous audio playback,
 * Bluetooth AVRCP metadata synchronization, audio focus handling (GPS ducking),
 * and becoming-noisy protection for car audio systems (e.g., 2017 Nissan Qashqai).
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

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

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityPendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        com.wildmusicplayer.data.AudioEffectsManager.getInstance(this).release()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
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
        val webUrl = Song.webUrlOf(dataSpec.uri) ?: return dataSpec

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
