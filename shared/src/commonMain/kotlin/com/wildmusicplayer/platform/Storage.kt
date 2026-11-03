package com.wildmusicplayer.platform

/**
 * Small key/value settings: SharedPreferences on Android, NSUserDefaults on iOS.
 * Each instance is one named group (one SharedPreferences file).
 */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun getStringSet(key: String): Set<String>?
    fun putStringSet(key: String, value: Set<String>)
    fun remove(key: String)
}

/** Text files in the app's private storage (filesDir on Android). */
interface FileStore {
    /** The file's text, or null if it doesn't exist or can't be read. */
    fun readText(name: String): String?

    /** Writes the whole file; a crash mid-write never leaves a half file behind. */
    fun writeText(name: String, text: String)

    fun delete(name: String)
}

/** A lock usable from common code (synchronized blocks are JVM-only). */
expect class Lock() {
    fun <T> withLock(block: () -> T): T
}
