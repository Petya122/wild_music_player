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

    /**
     * The Bluetooth device whose own sound settings are in use ("Nissan Connect"), or null for the phone's.
     * Null flow where sound isn't kept per device.
     */
    val soundProfileName: StateFlow<String?>? get() = null

    /** How many seconds songs fade into each other; 0 = off. Null where crossfade isn't supported. */
    val crossfadeSeconds: StateFlow<Int>? get() = null
    fun setCrossfadeSeconds(seconds: Int) {}
}

/** The longest crossfade offered. */
const val MAX_CROSSFADE_SECONDS = 12

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

/**
 * Where a sound setting is saved: the phone's under its plain name (as before 0.9.5), a Bluetooth device's
 * as "name@profile".
 */
fun soundProfileKey(baseKey: String, profileId: String?): String = if (profileId == null) baseKey else "$baseKey@$profileId"

/** A Bluetooth device's sound profile: by its address when the phone tells it, otherwise by its name. */
fun soundProfileId(deviceName: String?, address: String?): String {
    val usableAddress = address?.trim()?.takeIf { it.isNotEmpty() && it != "00:00:00:00:00:00" && it != "02:00:00:00:00:00" }
    return "bt:" + (usableAddress ?: deviceName?.trim()?.takeIf { it.isNotEmpty() } ?: "unknown")
}
