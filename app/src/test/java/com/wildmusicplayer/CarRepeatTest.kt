package com.wildmusicplayer

import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import com.wildmusicplayer.service.outsideRepeatRequest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The car's RPT button: it only asks for "repeat all" or "off", and must toggle off / repeat-one. */
class CarRepeatTest {

    @Test
    fun firstPressTurnsRepeatOneOn() {
        assertEquals(REPEAT_MODE_ONE, outsideRepeatRequest(current = REPEAT_MODE_OFF, requested = REPEAT_MODE_ALL))
    }

    @Test
    fun secondPressTurnsItOffAgain() {
        // The radio sees repeat-one, which isn't "repeat all", so it asks for "repeat all" again
        assertEquals(REPEAT_MODE_OFF, outsideRepeatRequest(current = REPEAT_MODE_ONE, requested = REPEAT_MODE_ALL))
    }

    @Test
    fun pressingOverAndOverAlternates() {
        var mode = REPEAT_MODE_OFF
        val seen = (1..4).map { mode = outsideRepeatRequest(mode, REPEAT_MODE_ALL); mode }
        assertEquals(listOf(REPEAT_MODE_ONE, REPEAT_MODE_OFF, REPEAT_MODE_ONE, REPEAT_MODE_OFF), seen)
    }

    @Test
    fun offAlwaysTurnsItOff() {
        assertEquals(REPEAT_MODE_OFF, outsideRepeatRequest(current = REPEAT_MODE_ONE, requested = REPEAT_MODE_OFF))
        assertEquals(REPEAT_MODE_OFF, outsideRepeatRequest(current = REPEAT_MODE_ALL, requested = REPEAT_MODE_OFF))
    }

    @Test
    fun repeatAllSetInTheAppBecomesRepeatOneFromTheCar() {
        assertEquals(REPEAT_MODE_ONE, outsideRepeatRequest(current = REPEAT_MODE_ALL, requested = REPEAT_MODE_ALL))
    }

    @Test
    fun askingForRepeatOneKeepsRepeatOne() {
        assertEquals(REPEAT_MODE_ONE, outsideRepeatRequest(current = REPEAT_MODE_ONE, requested = REPEAT_MODE_ONE))
    }
}
