package com.wildmusicplayer.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Volume, mute, boost, loudness normalization and the equalizer, applied to the player by each platform. */
interface AudioEffects {
    val volume: StateFlow<Float>
    val isMuted: StateFlow<Boolean>
    val volumeBoostDb: StateFlow<Int>
    val isNormalizationEnabled: StateFlow<Boolean>

    /** The phone's equalizer; null where there is none, or until the player has started. */
    val equalizer: StateFlow<EqualizerState?> get() = NO_EQUALIZER

    fun setVolume(newVolume: Float)
    fun toggleMute()
    fun setVolumeBoostDb(db: Int)
    fun setNormalizationEnabled(enabled: Boolean)

    fun setEqualizerEnabled(enabled: Boolean) {}
    fun useEqualizerPreset(index: Int) {}
    fun setEqualizerBandLevel(band: Int, levelMb: Int) {}
}

private val NO_EQUALIZER: StateFlow<EqualizerState?> = MutableStateFlow(null).asStateFlow()

/** What the equalizer can do and how it's set now. Levels are in millibels (100 mB = 1 dB). */
data class EqualizerState(
    val isEnabled: Boolean,
    val bandFrequenciesHz: List<Int>,
    val bandLevelsMb: List<Int>,
    val minLevelMb: Int,
    val maxLevelMb: Int,
    val presets: List<String>,
    /** The chosen preset, or -1 for the user's own curve. */
    val presetIndex: Int
)

/** The saved equalizer choice: on/off, the preset (-1 = custom) and the custom band levels. */
data class EqualizerSettings(
    val isEnabled: Boolean = false,
    val presetIndex: Int = -1,
    val bandLevelsMb: List<Int> = emptyList()
) {
    fun encode(): String = "${if (isEnabled) 1 else 0};$presetIndex;${bandLevelsMb.joinToString(",")}"

    companion object {
        fun decode(text: String?): EqualizerSettings {
            val parts = text?.split(';') ?: return EqualizerSettings()
            if (parts.size != 3) return EqualizerSettings()
            return EqualizerSettings(
                isEnabled = parts[0] == "1",
                presetIndex = parts[1].toIntOrNull() ?: -1,
                bandLevelsMb = parts[2].split(',').mapNotNull { it.toIntOrNull() }
            )
        }
    }
}
