package com.wildmusicplayer.data

import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.tan

/**
 * Integrated loudness in LUFS (EBU R128 / ITU-R BS.1770-4), the measure streaming services
 * normalize to. Feed it interleaved PCM floats (-1..1); it applies the K-weighting filter,
 * measures 400 ms blocks every 100 ms, and gates out silence and quiet passages.
 */
class LoudnessMeter(private val sampleRate: Int, private val channels: Int) {

    // K-weighting: a high-shelf ("head") filter, then a high-pass, per channel (coefficients as in libebur128)
    private val shelf = Biquad.highShelf(sampleRate)
    private val highPass = Biquad.highPass(sampleRate)
    private val shelfState = Array(channels) { DoubleArray(2) }
    private val highPassState = Array(channels) { DoubleArray(2) }

    private val samplesPer100ms = (sampleRate / 10).coerceAtLeast(1)
    private var subBlockSum = 0.0
    private var subBlockFrames = 0
    private val lastSubBlocks = DoubleArray(4)
    private var subBlockCount = 0

    // Mean square of every 400 ms block, summed over channels
    private val blockPowers = ArrayList<Double>()

    /** Adds [count] interleaved samples (count / channels frames). */
    fun addInterleaved(samples: FloatArray, count: Int = samples.size) {
        var i = 0
        while (i + channels <= count) {
            var frameEnergy = 0.0
            for (c in 0 until channels) {
                val weighted = highPass.process(shelf.process(samples[i + c].toDouble(), shelfState[c]), highPassState[c])
                frameEnergy += weighted * weighted // channel weight 1.0 for mono / stereo
            }
            subBlockSum += frameEnergy
            subBlockFrames++
            if (subBlockFrames == samplesPer100ms) finishSubBlock()
            i += channels
        }
    }

    private fun finishSubBlock() {
        lastSubBlocks[subBlockCount % 4] = subBlockSum / subBlockFrames
        subBlockCount++
        subBlockSum = 0.0
        subBlockFrames = 0
        // A 400 ms block is four 100 ms pieces: 75% overlap between blocks
        if (subBlockCount >= 4) blockPowers += lastSubBlocks.average()
    }

    /** Integrated loudness in LUFS, or null for silence or audio shorter than 400 ms. */
    fun integratedLoudness(): Double? {
        val aboveAbsolute = blockPowers.filter { loudnessOf(it) > ABSOLUTE_GATE_LUFS }
        if (aboveAbsolute.isEmpty()) return null
        val relativeGate = loudnessOf(aboveAbsolute.average()) - RELATIVE_GATE_LU
        val gated = aboveAbsolute.filter { loudnessOf(it) > relativeGate }
        if (gated.isEmpty()) return null
        return loudnessOf(gated.average())
    }

    private fun loudnessOf(power: Double): Double =
        if (power <= 0.0) Double.NEGATIVE_INFINITY else -0.691 + 10 * log10(power)

    private class Biquad(
        val b0: Double, val b1: Double, val b2: Double,
        val a1: Double, val a2: Double
    ) {
        // Transposed direct form II; state = [z1, z2]
        fun process(x: Double, state: DoubleArray): Double {
            val y = b0 * x + state[0]
            state[0] = b1 * x - a1 * y + state[1]
            state[1] = b2 * x - a2 * y
            return y
        }

        companion object {
            fun highShelf(sampleRate: Int): Biquad {
                val f0 = 1681.974450955533
                val gainDb = 3.999843853973347
                val q = 0.7071752369554196
                val k = tan(PI * f0 / sampleRate)
                val vh = 10.0.pow(gainDb / 20)
                val vb = vh.pow(0.4996667741545416)
                val a0 = 1 + k / q + k * k
                return Biquad(
                    b0 = (vh + vb * k / q + k * k) / a0,
                    b1 = 2 * (k * k - vh) / a0,
                    b2 = (vh - vb * k / q + k * k) / a0,
                    a1 = 2 * (k * k - 1) / a0,
                    a2 = (1 - k / q + k * k) / a0
                )
            }

            fun highPass(sampleRate: Int): Biquad {
                val f0 = 38.13547087602444
                val q = 0.5003270373238773
                val k = tan(PI * f0 / sampleRate)
                val a0 = 1 + k / q + k * k
                return Biquad(
                    b0 = 1.0, b1 = -2.0, b2 = 1.0,
                    a1 = 2 * (k * k - 1) / a0,
                    a2 = (1 - k / q + k * k) / a0
                )
            }
        }
    }

    companion object {
        private const val ABSOLUTE_GATE_LUFS = -70.0
        private const val RELATIVE_GATE_LU = 10.0
    }
}

/** The level songs are evened out to: the common streaming level. */
const val NORMALIZATION_TARGET_LUFS = -14.0

/**
 * How much to change a song's volume (dB) so it plays at [NORMALIZATION_TARGET_LUFS].
 * Loud songs are turned down up to 12 dB; quiet ones up at most 6 dB, so near-silent
 * intros or badly measured files never get blasted.
 */
fun normalizationGainDb(loudnessLufs: Double): Double =
    (NORMALIZATION_TARGET_LUFS - loudnessLufs).coerceIn(-12.0, 6.0)
