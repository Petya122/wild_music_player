package com.wildmusicplayer.data

import com.wildmusicplayer.platform.KeyValueStore

/** Car / headset auto-resume preferences, read by PlaybackService and shown in Settings. */
class ResumeSettings(private val prefs: KeyValueStore) {

    /** Car radio or headset Play button continues the last queue, even if the app was closed. */
    var resumeFromPlayButton: Boolean
        get() = prefs.getBoolean(KEY_RESUME_FROM_PLAY_BUTTON, true)
        set(value) = prefs.putBoolean(KEY_RESUME_FROM_PLAY_BUTTON, value)

    /** Music that paused because Bluetooth audio disconnected starts again when it reconnects. */
    var resumeOnBluetoothReconnect: Boolean
        get() = prefs.getBoolean(KEY_RESUME_ON_BLUETOOTH, true)
        set(value) = prefs.putBoolean(KEY_RESUME_ON_BLUETOOTH, value)

    companion object {
        private const val KEY_RESUME_FROM_PLAY_BUTTON = "key_resume_from_play_button"
        private const val KEY_RESUME_ON_BLUETOOTH = "key_resume_on_bluetooth_reconnect"
    }
}
