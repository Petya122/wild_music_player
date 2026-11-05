package com.wildmusicplayer.data

import com.wildmusicplayer.platform.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * General app preferences (SharedPreferences "wild_music_player_prefs" on Android, same keys as before).
 * Values the UI reacts to are StateFlows.
 */
class AppSettings(private val prefs: KeyValueStore) {

    private val _themeMode = MutableStateFlow(prefs.getString(KEY_THEME_MODE) ?: "SYSTEM")
    /** Name of the theme: "SYSTEM", "LIGHT" or "DARK". */
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _accentColor = MutableStateFlow(prefs.getString(KEY_ACCENT_COLOR) ?: "BLUE")
    /** Name of the accent color (AccentColor in the theme), "BLUE" by default. */
    val accentColor: StateFlow<String> = _accentColor.asStateFlow()

    private val _keepScreenOn = MutableStateFlow(prefs.getBoolean(KEY_KEEP_SCREEN_ON, false))
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    private val _filterShortAudio = MutableStateFlow(prefs.getBoolean(KEY_FILTER_SHORT_AUDIO, true))
    /** Hide clips shorter than 30 seconds (ringtones, notification sounds). */
    val filterShortAudio: StateFlow<Boolean> = _filterShortAudio.asStateFlow()

    private val _filterRecordings = MutableStateFlow(prefs.getBoolean(KEY_FILTER_RECORDINGS, true))
    /** Hide voice and call recordings. */
    val filterRecordings: StateFlow<Boolean> = _filterRecordings.asStateFlow()

    fun setThemeMode(name: String) {
        _themeMode.value = name
        prefs.putString(KEY_THEME_MODE, name)
    }

    fun setAccentColor(name: String) {
        _accentColor.value = name
        prefs.putString(KEY_ACCENT_COLOR, name)
    }

    fun setKeepScreenOn(enabled: Boolean) {
        _keepScreenOn.value = enabled
        prefs.putBoolean(KEY_KEEP_SCREEN_ON, enabled)
    }

    fun setFilterShortAudio(enabled: Boolean) {
        _filterShortAudio.value = enabled
        prefs.putBoolean(KEY_FILTER_SHORT_AUDIO, enabled)
    }

    fun setFilterRecordings(enabled: Boolean) {
        _filterRecordings.value = enabled
        prefs.putBoolean(KEY_FILTER_RECORDINGS, enabled)
    }

    /**
     * Records [currentVersion] as seen and returns true if the app was just updated from another version
     * (false on a fresh install), so What's New only shows after an update.
     */
    fun markVersionSeen(currentVersion: String): Boolean {
        val lastSeen = prefs.getString(KEY_LAST_SEEN_VERSION)
        prefs.putString(KEY_LAST_SEEN_VERSION, currentVersion)
        return lastSeen != null && lastSeen != currentVersion
    }

    companion object {
        const val PREFS_NAME = "wild_music_player_prefs"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_ACCENT_COLOR = "accent_color"
        private const val KEY_LAST_SEEN_VERSION = "last_seen_version"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_FILTER_SHORT_AUDIO = "key_filter_short_audio"
        private const val KEY_FILTER_RECORDINGS = "key_filter_recordings"
    }
}
