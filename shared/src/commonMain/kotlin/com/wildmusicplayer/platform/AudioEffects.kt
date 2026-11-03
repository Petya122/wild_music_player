package com.wildmusicplayer.platform

import kotlinx.coroutines.flow.StateFlow

/** Volume, mute, boost and loudness normalization, applied to the player by each platform. */
interface AudioEffects {
    val volume: StateFlow<Float>
    val isMuted: StateFlow<Boolean>
    val volumeBoostDb: StateFlow<Int>
    val isNormalizationEnabled: StateFlow<Boolean>

    fun setVolume(newVolume: Float)
    fun toggleMute()
    fun setVolumeBoostDb(db: Int)
    fun setNormalizationEnabled(enabled: Boolean)
}
