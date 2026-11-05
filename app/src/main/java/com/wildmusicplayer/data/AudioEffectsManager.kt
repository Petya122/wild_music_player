package com.wildmusicplayer.data

import com.wildmusicplayer.platform.AudioEffects
import com.wildmusicplayer.platform.EqualizerSettings
import com.wildmusicplayer.platform.EqualizerState
import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.pow

/**
 * Manages player volume scaling (0.0f to 1.0f), Mute state,
 * Hardware DSP Decibel (dB) Gain via [LoudnessEnhancer], and Audio Normalization.
 *
 * Normalization uses each song's measured loudness ([setCurrentTrackLoudness]): songs louder
 * than the target are turned down with the player volume, quieter ones up with the
 * LoudnessEnhancer, on top of the user's own volume and dB boost.
 *
 * The [Equalizer] is the phone's own, on the player's audio session: its presets, or a level per band.
 */
class AudioEffectsManager private constructor(context: Context) : AudioEffects {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Loudness of the song now playing (null = unknown, e.g. online streams or not measured yet)
    private var currentTrackLoudness: Double? = null

    private val trackGainDb: Double
        get() {
            val loudness = currentTrackLoudness
            return if (_isNormalizationEnabled.value && loudness != null) normalizationGainDb(loudness) else 0.0
        }

    private val _volume = MutableStateFlow(prefs.getFloat(KEY_VOLUME, 1.0f))
    override val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _isMuted = MutableStateFlow(prefs.getBoolean(KEY_IS_MUTED, false))
    override val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _volumeBoostDb = MutableStateFlow(prefs.getInt(KEY_BOOST_DB, 0))
    override val volumeBoostDb: StateFlow<Int> = _volumeBoostDb.asStateFlow()

    private val _isNormalizationEnabled = MutableStateFlow(prefs.getBoolean(KEY_NORMALIZATION, false))
    override val isNormalizationEnabled: StateFlow<Boolean> = _isNormalizationEnabled.asStateFlow()

    private var eqSettings = EqualizerSettings.decode(prefs.getString(KEY_EQUALIZER, null))
    private val _equalizer = MutableStateFlow<EqualizerState?>(null)
    override val equalizer: StateFlow<EqualizerState?> = _equalizer.asStateFlow()

    // The sleep timer's fade-out (1 = no fade)
    private var fadeFactor = 1f

    // Crossfade: the next song fading in (1 = full volume)
    private var crossfadeFactor = 1f

    private val _crossfadeSeconds = MutableStateFlow(
        prefs.getInt(KEY_CROSSFADE, 0).coerceIn(0, com.wildmusicplayer.platform.MAX_CROSSFADE_SECONDS)
    )
    override val crossfadeSeconds: StateFlow<Int> = _crossfadeSeconds.asStateFlow()

    private var player: ExoPlayer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var equalizerEffect: Equalizer? = null
    private var unmutedVolumeCache: Float = prefs.getFloat(KEY_LAST_UNMUTED_VOL, 1.0f)

    private val playerListener = object : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            setupLoudnessEnhancer(audioSessionId)
            setupEqualizer(audioSessionId)
        }
    }

    fun attachPlayer(exoPlayer: ExoPlayer) {
        this.player = exoPlayer
        exoPlayer.addListener(playerListener)
        applyVolumeToPlayer()
        setupLoudnessEnhancer(exoPlayer.audioSessionId)
        setupEqualizer(exoPlayer.audioSessionId)
    }

    override fun setVolume(newVolume: Float) {
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

    override fun toggleMute() {
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

    override fun setVolumeBoostDb(db: Int) {
        val clamped = db.coerceIn(0, MAX_BOOST_DB)
        _volumeBoostDb.value = clamped
        prefs.edit().putInt(KEY_BOOST_DB, clamped).apply()
        applyDspEnhancements()
    }

    override fun setNormalizationEnabled(enabled: Boolean) {
        _isNormalizationEnabled.value = enabled
        prefs.edit().putBoolean(KEY_NORMALIZATION, enabled).apply()
        // Measure the library in the background so every song has a level to even out to
        if (enabled) com.wildmusicplayer.service.LoudnessAnalysisWorker.enqueue(appContext)
        applyVolumeToPlayer()
        applyDspEnhancements()
    }

    override fun setEqualizerEnabled(enabled: Boolean) = saveEqualizer(eqSettings.copy(isEnabled = enabled))

    override fun useEqualizerPreset(index: Int) = saveEqualizer(eqSettings.copy(presetIndex = index))

    // Moving a band turns the current curve (a preset's, too) into the user's own
    override fun setEqualizerBandLevel(band: Int, levelMb: Int) {
        val levels = _equalizer.value?.bandLevelsMb?.toMutableList() ?: return
        if (band !in levels.indices) return
        levels[band] = levelMb
        saveEqualizer(eqSettings.copy(presetIndex = -1, bandLevelsMb = levels))
    }

    private fun saveEqualizer(settings: EqualizerSettings) {
        eqSettings = settings
        prefs.edit().putString(KEY_EQUALIZER, settings.encode()).apply()
        applyEqualizer()
    }

    override fun setCrossfadeSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(0, com.wildmusicplayer.platform.MAX_CROSSFADE_SECONDS)
        _crossfadeSeconds.value = clamped
        prefs.edit().putInt(KEY_CROSSFADE, clamped).apply()
    }

    /** Called by the crossfade while the next song fades in. */
    fun setCrossfadeFactor(factor: Float) {
        crossfadeFactor = factor.coerceIn(0f, 1f)
        applyVolumeToPlayer()
    }

    /**
     * The volume the song playing now gets, before any crossfade: the user's volume, mute, normalization
     * and the sleep timer's fade. The crossfade plays the end of this song at it while the next one starts.
     */
    fun songVolume(): Float {
        val cut = trackGainDb.coerceAtMost(0.0)
        val normalizationScale = 10.0.pow(cut / 20).toFloat()
        return if (_isMuted.value) 0.0f else _volume.value * normalizationScale * fadeFactor
    }

    /** Called by the sleep timer while it fades the music out. */
    fun setFadeFactor(factor: Float) {
        fadeFactor = factor.coerceIn(0f, 1f)
        applyVolumeToPlayer()
    }

    /** Called by PlaybackService when a new song starts. */
    fun setCurrentTrackLoudness(loudnessLufs: Double?) {
        currentTrackLoudness = loudnessLufs
        applyVolumeToPlayer()
        applyDspEnhancements()
    }

    // Turning a loud song down is done with the player volume (clean, no processing)
    private fun applyVolumeToPlayer() {
        player?.volume = songVolume() * crossfadeFactor
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

    private fun setupEqualizer(audioSessionId: Int) {
        try {
            equalizerEffect?.release()
            equalizerEffect = null
            _equalizer.value = null
            if (audioSessionId > 0) {
                equalizerEffect = Equalizer(0, audioSessionId)
                applyEqualizer()
            }
        } catch (e: Exception) {
            // Some phones have no equalizer: the Sound sheet then says so
            e.printStackTrace()
        }
    }

    private fun applyEqualizer() {
        val eq = equalizerEffect ?: return
        try {
            val bands = (0 until eq.numberOfBands).map { it.toShort() }
            val presetCount = eq.numberOfPresets.toInt()
            val settings = eqSettings
            val presetIndex = settings.presetIndex.takeIf { it in 0 until presetCount } ?: -1
            if (presetIndex >= 0) {
                eq.usePreset(presetIndex.toShort())
            } else {
                bands.forEach { band -> eq.setBandLevel(band, (settings.bandLevelsMb.getOrNull(band.toInt()) ?: 0).toShort()) }
            }
            eq.enabled = settings.isEnabled

            val range = eq.bandLevelRange
            _equalizer.value = EqualizerState(
                isEnabled = settings.isEnabled,
                bandFrequenciesHz = bands.map { eq.getCenterFreq(it) / 1000 },
                bandLevelsMb = bands.map { eq.getBandLevel(it).toInt() },
                minLevelMb = range[0].toInt(),
                maxLevelMb = range[1].toInt(),
                presets = (0 until presetCount).map { eq.getPresetName(it.toShort()) },
                presetIndex = presetIndex
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun applyDspEnhancements() {
        try {
            val enhancer = loudnessEnhancer ?: return
            // Gain is in millibels (mB): 100 mB = 1 dB
            // The user's dB boost, plus raising a quiet song towards the normalization target
            val boostmB = _volumeBoostDb.value * 100
            val normmB = (trackGainDb.coerceAtLeast(0.0) * 100).toInt()
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
            equalizerEffect?.release()
            equalizerEffect = null
            _equalizer.value = null
        } catch (_: Exception) {}
    }

    companion object {
        private const val PREFS_NAME = "audio_effects_prefs"
        private const val KEY_VOLUME = "pref_player_volume"
        private const val KEY_IS_MUTED = "pref_is_muted"
        private const val KEY_LAST_UNMUTED_VOL = "pref_last_unmuted_vol"
        private const val KEY_BOOST_DB = "pref_volume_boost_db"
        private const val KEY_NORMALIZATION = "pref_audio_normalization"
        private const val KEY_EQUALIZER = "pref_equalizer"
        private const val KEY_CROSSFADE = "pref_crossfade_seconds"

        // Matches the largest preset offered in SoundSettingsSheet
        const val MAX_BOOST_DB = 12

        @Volatile
        private var instance: AudioEffectsManager? = null

        fun getInstance(context: Context): AudioEffectsManager {
            return instance ?: synchronized(this) {
                instance ?: AudioEffectsManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
