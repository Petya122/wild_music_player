package com.wildmusicplayer.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.wildmusicplayer.data.AudioEffectsManager
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Crossfade: [crossfadeSeconds][AudioEffectsManager.crossfadeSeconds] before a song ends, the player moves
 * on to the next song and fades it in, while a second, hidden player ("the tail") plays the rest of the old
 * song and fades it out. The notification, lock screen and car show the new song from the start of the fade.
 *
 * The tail is lined up with the main player shortly before: it waits paused at the fade's start, then fades
 * in over a few milliseconds while the main player still plays, so the hand-over has no gap.
 *
 * No crossfade with repeat-one, "pause after this song" (sleep timer), online streams, at the end of the
 * queue, or for songs too short for it. Pausing, skipping, seeking, a call or a Bluetooth disconnect end it
 * at once. Everything runs on the main thread, like the player.
 */
@OptIn(UnstableApi::class)
internal class CrossfadeController(
    private val context: Context,
    private val player: ExoPlayer,
    private val effects: AudioEffectsManager
) {
    private val handler = Handler(Looper.getMainLooper())

    private var tail: ExoPlayer? = null
    // The song (media ID and queue index) waiting in the tail
    private var tailKey: String? = null
    // A song whose crossfade didn't work out (the tail wasn't ready in time): it just ends normally
    private var skippedKey: String? = null

    private var fading = false
    // True while this class moves the player on, so its own events aren't taken for the user's
    private var switching = false
    private var stepStartedAt = 0L
    private var fadeLengthMs = 0L
    private var tailVolume = 1f

    private val check = Runnable { checkPosition() }

    // Hand-over: the tail fades up while the main player still plays the same moment of the song
    private val handOverStep = object : Runnable {
        override fun run() {
            val t = tail ?: return endFade()
            val progress = ((SystemClock.elapsedRealtime() - stepStartedAt).toFloat() / HAND_OVER_MS).coerceIn(0f, 1f)
            t.volume = tailVolume * progress
            if (progress < 1f) {
                handler.postDelayed(this, STEP_MS)
                return
            }
            val next = player.nextMediaItemIndex
            if (next == C.INDEX_UNSET) return endFade()
            switching = true
            try {
                effects.setCrossfadeFactor(0f)
                player.seekTo(next, 0)
            } finally {
                switching = false
            }
            stepStartedAt = SystemClock.elapsedRealtime()
            handler.post(fadeStep)
        }
    }

    // Equal-power fade: the sum sounds equally loud all the way through
    private val fadeStep = object : Runnable {
        override fun run() {
            val progress = ((SystemClock.elapsedRealtime() - stepStartedAt).toFloat() / fadeLengthMs).coerceIn(0f, 1f)
            val angle = progress * (PI / 2).toFloat()
            effects.setCrossfadeFactor(sin(angle))
            tail?.volume = tailVolume * cos(angle)
            if (progress < 1f) handler.postDelayed(this, STEP_MS) else endFade()
        }
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (switching) return
            endFade() // skipped, went back, or the queue changed
            skippedKey = null
            scheduleCheck(0)
        }

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            if (switching) return
            if (reason == Player.DISCONTINUITY_REASON_SEEK) endFade()
            scheduleCheck(0)
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady) endFade()
            scheduleCheck(0)
        }

        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
            // A call or another app took the audio
            if (playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE) endFade()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) = scheduleCheck(0)

        override fun onRepeatModeChanged(repeatMode: Int) = scheduleCheck(0)

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = scheduleCheck(0)
    }

    init {
        player.addListener(listener)
    }

    fun release() {
        endFade()
        handler.removeCallbacks(check)
        player.removeListener(listener)
        tail?.release()
        tail = null
    }

    private fun scheduleCheck(delayMs: Long) {
        handler.removeCallbacks(check)
        if (!fading && player.isPlaying) handler.postDelayed(check, delayMs.coerceAtLeast(0))
    }

    private fun checkPosition() {
        if (fading || !player.isPlaying) return
        val seconds = effects.crossfadeSeconds.value
        val item = player.currentMediaItem
        if (seconds <= 0 || item == null || !canCrossfade(item)) {
            if (seconds <= 0) releaseTail()
            return scheduleCheck(IDLE_CHECK_MS)
        }
        val key = "${item.mediaId}@${player.currentMediaItemIndex}"
        val duration = player.duration
        val fadeMs = seconds * 1000L
        if (key == skippedKey || duration == C.TIME_UNSET || duration < fadeMs * 2 + MIN_SONG_EXTRA_MS) {
            return scheduleCheck(IDLE_CHECK_MS)
        }

        val fadeStart = duration - fadeMs
        val position = player.currentPosition
        val untilStart = fadeStart - position
        when {
            untilStart > PREPARE_AHEAD_MS -> scheduleCheck(minOf(untilStart - PREPARE_AHEAD_MS, IDLE_CHECK_MS))
            // Seeked to the very end: let it end on its own
            duration - position < MIN_FADE_MS -> scheduleCheck(IDLE_CHECK_MS)
            untilStart > HAND_OVER_MS -> {
                prepareTail(item, key, fadeStart - HAND_OVER_MS)
                scheduleCheck(minOf(untilStart - HAND_OVER_MS, PRECISE_CHECK_MS))
            }
            else -> startFade(item, key, position, duration - position - HAND_OVER_MS)
        }
    }

    private fun canCrossfade(item: MediaItem): Boolean {
        if (player.repeatMode == Player.REPEAT_MODE_ONE || player.pauseAtEndOfMediaItems) return false
        val next = player.nextMediaItemIndex
        if (next == C.INDEX_UNSET) return false
        // Online streams would need a second stream; they keep playing back to back
        return !isOnline(item) && !isOnline(player.getMediaItemAt(next))
    }

    private fun isOnline(item: MediaItem) = item.mediaId.startsWith("online:")

    /** Loads the song now playing into the tail, waiting paused at [positionMs]. */
    private fun prepareTail(item: MediaItem, key: String, positionMs: Long) {
        if (tailKey == key) return
        val t = tail ?: ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                /* handleAudioFocus = */ false // the main player owns the audio focus
            )
            .setHandleAudioBecomingNoisy(false)
            .build()
            .also { tail = it }
        // Same audio session: the equalizer and the dB boost work on the tail too
        t.setAudioSessionId(player.audioSessionId)
        t.volume = 0f
        t.playWhenReady = false
        t.setMediaItem(item, positionMs.coerceAtLeast(0))
        t.prepare()
        tailKey = key
    }

    private fun startFade(item: MediaItem, key: String, position: Long, remainingMs: Long) {
        prepareTail(item, key, position)
        val t = tail
        // Just loaded (after a seek near the end): give it a moment while there's time
        if (t != null && t.playbackState == Player.STATE_BUFFERING && remainingMs > MIN_FADE_MS + PREPARE_GRACE_MS) {
            return scheduleCheck(PRECISE_CHECK_MS)
        }
        if (t == null || t.playbackState != Player.STATE_READY) {
            // Not ready in time (a slow file): this song ends normally
            skippedKey = key
            releaseTail()
            return scheduleCheck(IDLE_CHECK_MS)
        }
        if (abs(t.currentPosition - position) > MAX_DRIFT_MS) t.seekTo(position)
        fading = true
        fadeLengthMs = remainingMs.coerceIn(MIN_FADE_MS, effects.crossfadeSeconds.value * 1000L)
        tailVolume = effects.songVolume()
        t.volume = 0f
        t.play()
        stepStartedAt = SystemClock.elapsedRealtime()
        handler.post(handOverStep)
    }

    /** Ends a fade (finished or cut short): the tail stops and the playing song gets its full volume. */
    private fun endFade() {
        handler.removeCallbacks(handOverStep)
        handler.removeCallbacks(fadeStep)
        if (fading || tailKey != null) {
            tail?.run {
                stop()
                clearMediaItems()
            }
            tailKey = null
        }
        if (fading) {
            fading = false
            effects.setCrossfadeFactor(1f)
            scheduleCheck(IDLE_CHECK_MS)
        }
    }

    private fun releaseTail() {
        if (fading) return
        tail?.release()
        tail = null
        tailKey = null
    }

    private companion object {
        const val IDLE_CHECK_MS = 2_000L
        const val PRECISE_CHECK_MS = 50L
        // Long enough for a song file to open and be ready to play
        const val PREPARE_AHEAD_MS = 5_000L
        const val HAND_OVER_MS = 60L
        const val STEP_MS = 16L
        const val MIN_FADE_MS = 1_000L
        const val PREPARE_GRACE_MS = 500L
        const val MAX_DRIFT_MS = 80L
        // A song must be at least this much longer than two fades to crossfade
        const val MIN_SONG_EXTRA_MS = 5_000L
    }
}
