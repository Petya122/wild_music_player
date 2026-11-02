package com.wildmusicplayer

import com.wildmusicplayer.data.LoudnessMeter
import com.wildmusicplayer.data.normalizationGainDb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class LoudnessMeterTest {

    /** Interleaved [channels] copies of a 997 Hz sine (the BS.1770 reference tone). */
    private fun sine(amplitude: Double, seconds: Double, sampleRate: Int, channels: Int): FloatArray {
        val frames = (seconds * sampleRate).toInt()
        val out = FloatArray(frames * channels)
        for (f in 0 until frames) {
            val v = (amplitude * sin(2 * PI * 997.0 * f / sampleRate)).toFloat()
            for (c in 0 until channels) out[f * channels + c] = v
        }
        return out
    }

    private fun measure(sampleRate: Int, channels: Int, vararg parts: FloatArray): Double? {
        val meter = LoudnessMeter(sampleRate, channels)
        parts.forEach { meter.addInterleaved(it) }
        return meter.integratedLoudness()
    }

    @Test
    fun fullScaleStereoToneIsZeroLufs() {
        assertEquals(0.0, measure(48000, 2, sine(1.0, 10.0, 48000, 2))!!, 0.1)
    }

    @Test
    fun minus20dbStereoToneIsMinus20Lufs() {
        assertEquals(-20.0, measure(48000, 2, sine(0.1, 10.0, 48000, 2))!!, 0.1)
    }

    @Test
    fun monoToneIsThreeDbQuieterThanStereo() {
        assertEquals(-23.01, measure(48000, 1, sine(0.1, 10.0, 48000, 1))!!, 0.1)
    }

    @Test
    fun worksAt44100Hz() {
        assertEquals(-20.0, measure(44100, 2, sine(0.1, 10.0, 44100, 2))!!, 0.2)
    }

    @Test
    fun silenceIsGatedOut() {
        val tone = sine(0.1, 10.0, 48000, 2)
        val silence = FloatArray(10 * 48000 * 2)
        assertEquals(-20.0, measure(48000, 2, tone, silence)!!, 0.1)
    }

    @Test
    fun quietPassagesBelowRelativeGateDoNotPullTheResultDown() {
        val loud = sine(0.1, 10.0, 48000, 2)      // -20 LUFS
        val veryQuiet = sine(0.001, 10.0, 48000, 2) // -60 LUFS, more than 10 LU below
        assertEquals(-20.0, measure(48000, 2, loud, veryQuiet)!!, 0.1)
    }

    @Test
    fun pureSilenceHasNoLoudness() {
        assertNull(measure(48000, 2, FloatArray(5 * 48000 * 2)))
    }

    @Test
    fun gainTowardsTargetIsCapped() {
        assertEquals(-6.0, normalizationGainDb(-8.0), 0.001)   // loud master turned down
        assertEquals(4.0, normalizationGainDb(-18.0), 0.001)   // quiet song turned up
        assertEquals(6.0, normalizationGainDb(-40.0), 0.001)   // boost capped at +6
        assertEquals(-12.0, normalizationGainDb(5.0), 0.001)   // cut capped at -12
    }
}
