package com.wildmusicplayer.data

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.LoudnessEnhancer
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages player volume scaling (0.0f to 1.0f), Mute state,
 * Hardware DSP Decibel (dB) Gain via [LoudnessEnhancer], and Audio Normalization.
 */
class AudioEffectsManager private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _volume = MutableStateFlow(prefs.getFloat(KEY_VOLUME, 1.0f))
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _isMuted = MutableStateFlow(prefs.getBoolean(KEY_IS_MUTED, false))
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _volumeBoostDb = MutableStateFlow(prefs.getInt(KEY_BOOST_DB, 0))
    val volumeBoostDb: StateFlow<Int> = _volumeBoostDb.asStateFlow()

    private val _isNormalizationEnabled = MutableStateFlow(prefs.getBoolean(KEY_NORMALIZATION, false))
    val isNormalizationEnabled: StateFlow<Boolean> = _isNormalizationEnabled.asStateFlow()

    private var player: ExoPlayer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var unmutedVolumeCache: Float = prefs.getFloat(KEY_LAST_UNMUTED_VOL, 1.0f)

    private val playerListener = object : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            setupLoudnessEnhancer(audioSessionId)
        }
    }

    fun attachPlayer(exoPlayer: ExoPlayer) {
        this.player = exoPlayer
        exoPlayer.addListener(playerListener)
        applyVolumeToPlayer()
        setupLoudnessEnhancer(exoPlayer.audioSessionId)
    }

    fun setVolume(newVolume: Float) {
        val clamped = newVolume.coerceIn(0f, 1f)
        _volume.value = clamped
        if (clamped > 0f && _isMuted.value) {
            _isMuted.value = false
            prefs.edit().putBoolean(KEY_IS_MUTED, false).apply()
        }
        if (clamped > 0f) {
            unmutedVolumeCache = clamped
            prefs.edit().putFloat(KEY_LAST_UNMUTED_VOL, clamped).apply()
        }
        prefs.edit().putFloat(KEY_VOLUME, clamped).apply()
        applyVolumeToPlayer()
    }

    fun toggleMute() {
        val nextMuted = !_isMuted.value
        _isMuted.value = nextMuted
        prefs.edit().putBoolean(KEY_IS_MUTED, nextMuted).apply()

        if (!nextMuted && _volume.value <= 0f) {
            val restored = if (unmutedVolumeCache > 0f) unmutedVolumeCache else 1.0f
            _volume.value = restored
            prefs.edit().putFloat(KEY_VOLUME, restored).apply()
        }

        applyVolumeToPlayer()
    }

    fun setVolumeBoostDb(db: Int) {
        val clamped = db.coerceIn(0, 15)
        _volumeBoostDb.value = clamped
        prefs.edit().putInt(KEY_BOOST_DB, clamped).apply()
        applyDspEnhancements()
    }

    fun setNormalizationEnabled(enabled: Boolean) {
        _isNormalizationEnabled.value = enabled
        prefs.edit().putBoolean(KEY_NORMALIZATION, enabled).apply()
        applyDspEnhancements()
    }

    private fun applyVolumeToPlayer() {
        val targetVolume = if (_isMuted.value) 0.0f else _volume.value
        player?.volume = targetVolume
    }

    private fun setupLoudnessEnhancer(audioSessionId: Int) {
        try {
            loudnessEnhancer?.release()
            loudnessEnhancer = null

            if (audioSessionId > 0) {
                loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                    applyDspEnhancements()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun applyDspEnhancements() {
        try {
            val enhancer = loudnessEnhancer ?: return
            // Gain is in millibels (mB): 100 mB = 1 dB
            // Normalization adds +350 mB baseline leveling boost if enabled
            val boostmB = _volumeBoostDb.value * 100
            val normmB = if (_isNormalizationEnabled.value) 350 else 0
            val totalGainmB = boostmB + normmB

            if (totalGainmB > 0) {
                enhancer.setTargetGain(totalGainmB)
                enhancer.enabled = true
            } else {
                enhancer.setTargetGain(0)
                enhancer.enabled = false
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        try {
            player?.removeListener(playerListener)
            player = null
            loudnessEnhancer?.release()
            loudnessEnhancer = null
        } catch (_: Exception) {}
    }

    companion object {
        private const val PREFS_NAME = "audio_effects_prefs"
        private const val KEY_VOLUME = "pref_player_volume"
        private const val KEY_IS_MUTED = "pref_is_muted"
        private const val KEY_LAST_UNMUTED_VOL = "pref_last_unmuted_vol"
        private const val KEY_BOOST_DB = "pref_volume_boost_db"
        private const val KEY_NORMALIZATION = "pref_audio_normalization"

        @Volatile
        private var instance: AudioEffectsManager? = null

        fun getInstance(context: Context): AudioEffectsManager {
            return instance ?: synchronized(this) {
                instance ?: AudioEffectsManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
