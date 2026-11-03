package com.wildmusicplayer

import com.wildmusicplayer.platform.SLEEP_FADE_MS
import com.wildmusicplayer.platform.formatSleepRemaining
import com.wildmusicplayer.platform.sleepFadeFactor
import kotlin.test.Test
import kotlin.test.assertEquals

class SleepTimerTest {

    @Test
    fun fullVolumeUntilTheFadeStarts() {
        assertEquals(1f, sleepFadeFactor(30 * 60_000L))
        assertEquals(1f, sleepFadeFactor(SLEEP_FADE_MS))
        assertEquals(1f, sleepFadeFactor(Long.MAX_VALUE))
    }

    @Test
    fun fadesEvenlyToSilence() {
        assertEquals(0.5f, sleepFadeFactor(SLEEP_FADE_MS / 2))
        assertEquals(0f, sleepFadeFactor(0))
        assertEquals(0f, sleepFadeFactor(-500))
    }

    @Test
    fun countdownText() {
        assertEquals("15:00", formatSleepRemaining(15 * 60_000L))
        assertEquals("0:01", formatSleepRemaining(1))
        assertEquals("1:30:00", formatSleepRemaining(90 * 60_000L))
        assertEquals("0:00", formatSleepRemaining(0))
    }
}
