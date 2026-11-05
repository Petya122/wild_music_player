package com.wildmusicplayer

import com.wildmusicplayer.lyrics.LyricLine
import com.wildmusicplayer.ui.components.formatOffset
import com.wildmusicplayer.ui.components.timingOffsetFor
import kotlin.test.Test
import kotlin.test.assertEquals

class LyricsTimingTest {

    @Test
    fun longPressedLineStartsNow() {
        // The line is stamped at 0:15.75 but the words are heard at 0:17.30: show everything 1.5 s later
        assertEquals(1_500L, timingOffsetFor(LyricLine(15_750, "Mi deh pon di attack"), 17_300))
        // Heard earlier than stamped: lines come earlier
        assertEquals(-700L, timingOffsetFor(LyricLine(15_750, "x"), 15_000))
    }

    @Test
    fun offsetsAreShownInSeconds() {
        assertEquals("+1.5 s", formatOffset(1_500))
        assertEquals("−0.5 s", formatOffset(-500))
        assertEquals("+12.0 s", formatOffset(12_000))
    }
}
