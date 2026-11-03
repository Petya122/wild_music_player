package com.wildmusicplayer.platform

import kotlinx.coroutines.flow.StateFlow

/** Where the sleep timer is: off, counting down, or waiting for the current song to end. */
data class SleepTimerState(
    val isActive: Boolean = false,
    val atEndOfSong: Boolean = false,
    val remainingMs: Long = 0L
)

/** Pauses playback after a while or at the end of the current song, fading out first. */
interface SleepTimer {
    val state: StateFlow<SleepTimerState>

    fun start(minutes: Int)
    fun stopAfterCurrentSong()
    fun cancel()
}

/** The music fades out over this long before the timer pauses it. */
const val SLEEP_FADE_MS = 10_000L

/** Volume factor (0..1) with [remainingMs] left: full volume until the fade starts, silent at 0. */
fun sleepFadeFactor(remainingMs: Long): Float =
    (remainingMs.toFloat() / SLEEP_FADE_MS).coerceIn(0f, 1f)

/** "23:10", or "1:05:00" from an hour up; seconds are rounded up so it never shows 0:00 early. */
fun formatSleepRemaining(remainingMs: Long): String {
    val totalSeconds = (remainingMs.coerceAtLeast(0) + 999) / 1000
    val hours = totalSeconds / 3600
    val minutes = totalSeconds % 3600 / 60
    val seconds = (totalSeconds % 60).toString().padStart(2, '0')
    return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:$seconds" else "$minutes:$seconds"
}
