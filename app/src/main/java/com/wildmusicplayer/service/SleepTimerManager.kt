package com.wildmusicplayer.service

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.wildmusicplayer.data.AudioEffectsManager
import com.wildmusicplayer.platform.SLEEP_FADE_MS
import com.wildmusicplayer.platform.SleepTimer
import com.wildmusicplayer.platform.SleepTimerState
import com.wildmusicplayer.platform.sleepFadeFactor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The sleep timer. PlaybackService hands it the player, so it keeps counting with the screen off or
 * the app closed. The last [SLEEP_FADE_MS] fade the music out, then it pauses.
 * "After this song" lets ExoPlayer pause at the end of the song itself. Main thread only.
 */
object SleepTimerManager : SleepTimer {

    private val handler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(SleepTimerState())
    override val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    private var player: ExoPlayer? = null
    private var effects: AudioEffectsManager? = null
    // When the countdown ends (elapsedRealtime); 0 = "after this song" or off
    private var endsAt = 0L

    private val tick = Runnable { update() }

    private val playerListener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM &&
                _state.value.atEndOfSong
            ) {
                cancel()
            }
        }
    }

    fun attach(player: ExoPlayer, effects: AudioEffectsManager) {
        this.player = player
        this.effects = effects
        player.addListener(playerListener)
    }

    fun detach() {
        cancel()
        player?.removeListener(playerListener)
        player = null
        effects = null
    }

    override fun start(minutes: Int) {
        reset()
        endsAt = SystemClock.elapsedRealtime() + minutes * 60_000L
        update()
    }

    override fun stopAfterCurrentSong() {
        reset()
        player?.pauseAtEndOfMediaItems = true
        update()
    }

    override fun cancel() {
        reset()
        _state.value = SleepTimerState()
    }

    private fun reset() {
        handler.removeCallbacks(tick)
        endsAt = 0L
        player?.pauseAtEndOfMediaItems = false
        effects?.setFadeFactor(1f)
    }

    private fun update() {
        val player = player ?: return cancel()
        // Stopped: nothing left to put to sleep
        if (player.mediaItemCount == 0) return cancel()
        val atEndOfSong = endsAt == 0L
        val remaining = if (atEndOfSong) {
            if (player.duration == C.TIME_UNSET) Long.MAX_VALUE else player.duration - player.currentPosition
        } else {
            endsAt - SystemClock.elapsedRealtime()
        }

        if (!atEndOfSong && remaining <= 0) {
            player.pause()
            cancel()
            return
        }

        _state.value = SleepTimerState(isActive = true, atEndOfSong = atEndOfSong, remainingMs = remaining.coerceAtLeast(0))
        effects?.setFadeFactor(sleepFadeFactor(remaining))
        handler.postDelayed(tick, if (remaining <= SLEEP_FADE_MS) FADE_TICK_MS else TICK_MS)
    }

    private const val TICK_MS = 1_000L
    private const val FADE_TICK_MS = 200L
}
