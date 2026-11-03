package com.wildmusicplayer.platform

import com.wildmusicplayer.data.LoudnessStore
import com.wildmusicplayer.data.normalizationGainDb
import com.wildmusicplayer.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.pow

/**
 * Volume, mute and loudness normalization on iOS, applied through the player's volume.
 * AVPlayer can only turn audio down, so normalization only lowers loud songs and the dB boost
 * is remembered but has no effect yet. Songs are measured by a later version; until then
 * [LoudnessStore] has no values and normalization changes nothing.
 */
class IosAudioEffects(
    private val prefs: KeyValueStore,
    private val loudness: LoudnessStore
) : AudioEffects {

    private val _volume = MutableStateFlow(prefs.getString(KEY_VOLUME)?.toFloatOrNull() ?: 1f)
    override val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _isMuted = MutableStateFlow(prefs.getBoolean(KEY_IS_MUTED, false))
    override val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _volumeBoostDb = MutableStateFlow(prefs.getString(KEY_BOOST_DB)?.toIntOrNull() ?: 0)
    override val volumeBoostDb: StateFlow<Int> = _volumeBoostDb.asStateFlow()

    private val _isNormalizationEnabled = MutableStateFlow(prefs.getBoolean(KEY_NORMALIZATION, false))
    override val isNormalizationEnabled: StateFlow<Boolean> = _isNormalizationEnabled.asStateFlow()

    /** Set by the player: called with the new player volume (0..1) whenever it changes. */
    var onVolumeChanged: (Float) -> Unit = {}

    private var trackGainFactor = 1f

    override fun setVolume(newVolume: Float) {
        _volume.value = newVolume.coerceIn(0f, 1f)
        if (newVolume > 0f) _isMuted.value = false
        prefs.putString(KEY_VOLUME, _volume.value.toString())
        prefs.putBoolean(KEY_IS_MUTED, _isMuted.value)
        apply()
    }

    override fun toggleMute() {
        _isMuted.value = !_isMuted.value
        prefs.putBoolean(KEY_IS_MUTED, _isMuted.value)
        apply()
    }

    override fun setVolumeBoostDb(db: Int) {
        _volumeBoostDb.value = db
        prefs.putString(KEY_BOOST_DB, db.toString())
    }

    override fun setNormalizationEnabled(enabled: Boolean) {
        _isNormalizationEnabled.value = enabled
        prefs.putBoolean(KEY_NORMALIZATION, enabled)
        apply()
    }

    /** A new song started: use its measured loudness, if any. */
    fun onSongChanged(song: Song) {
        val gainDb = loudness.get(song.id)?.let { normalizationGainDb(it) } ?: 0.0
        // Only cuts are possible with AVPlayer's volume
        trackGainFactor = 10.0.pow(minOf(gainDb, 0.0) / 20.0).toFloat()
        apply()
    }

    fun playerVolume(): Float = when {
        _isMuted.value -> 0f
        _isNormalizationEnabled.value -> _volume.value * trackGainFactor
        else -> _volume.value
    }

    private fun apply() = onVolumeChanged(playerVolume())

    private companion object {
        const val KEY_VOLUME = "volume"
        const val KEY_IS_MUTED = "is_muted"
        const val KEY_BOOST_DB = "boost_db"
        const val KEY_NORMALIZATION = "normalization"
    }
}
