package com.wildmusicplayer.data

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder

/** Decodes a song with Android's own decoders and measures its loudness. */
object LoudnessAnalyzer {

    // Long DJ mixes: the first 15 minutes are plenty to judge the level
    private const val MAX_SECONDS = 15 * 60
    private const val TIMEOUT_US = 10_000L

    /** Integrated loudness of the song at [uri] in LUFS, or null if it can't be decoded. Blocking. */
    fun measure(context: Context, uri: Uri): Double? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(track)
            val inputFormat = extractor.getTrackFormat(track)
            val decoder = MediaCodec.createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME)!!)
            codec = decoder
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

            var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var floatPcm = false
            var meter: LoudnessMeter? = null
            var samples = FloatArray(0)
            var framesMeasured = 0L

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var idleLoops = 0

            while (true) {
                if (!inputDone) {
                    val inIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val buffer = decoder.getInputBuffer(inIndex)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val format = decoder.outputFormat
                        sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        floatPcm = format.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            format.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    }
                    outIndex >= 0 -> {
                        idleLoops = 0
                        val out = decoder.getOutputBuffer(outIndex)!!.order(ByteOrder.nativeOrder())
                        out.position(info.offset)
                        out.limit(info.offset + info.size)

                        val count = if (floatPcm) info.size / 4 else info.size / 2
                        if (samples.size < count) samples = FloatArray(count)
                        if (floatPcm) {
                            out.asFloatBuffer().get(samples, 0, count)
                        } else {
                            val shorts = out.asShortBuffer()
                            for (i in 0 until count) samples[i] = shorts.get(i) / 32768f
                        }

                        val activeMeter = meter ?: LoudnessMeter(sampleRate, channels).also { meter = it }
                        activeMeter.addInterleaved(samples, count)
                        framesMeasured += count / channels.coerceAtLeast(1)
                        decoder.releaseOutputBuffer(outIndex, false)

                        val ended = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        if (ended || framesMeasured >= MAX_SECONDS.toLong() * sampleRate) break
                    }
                    // Safety net against a decoder that stops producing output
                    ++idleLoops > 500 -> break
                }
            }
            return meter?.integratedLoudness()
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            try {
                codec?.stop()
            } catch (_: Exception) {
            }
            codec?.release()
            extractor.release()
        }
    }
}
