package com.wildmusicplayer.platform

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** [KeyValueStore] over one SharedPreferences file, so existing settings keep working. */
class SharedPreferencesStore(context: Context, name: String) : KeyValueStore {
    private val prefs: SharedPreferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
    override fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    override fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    override fun getStringSet(key: String): Set<String>? = prefs.getStringSet(key, null)
    override fun putStringSet(key: String, value: Set<String>) = prefs.edit().putStringSet(key, value).apply()
    override fun remove(key: String) = prefs.edit().remove(key).apply()
}

/** [FileStore] in the app's private files folder. */
class AndroidFileStore(private val dir: File) : FileStore {
    constructor(context: Context) : this(context.applicationContext.filesDir)

    override fun readText(name: String): String? = try {
        File(dir, name).takeIf { it.exists() }?.readText()
    } catch (e: Exception) {
        null
    }

    override fun writeText(name: String, text: String) {
        try {
            // Write then rename, so a crash mid-write never leaves a half file behind
            val file = File(dir, name)
            val temp = File(dir, "$name.tmp")
            temp.writeText(text)
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun delete(name: String) {
        File(dir, name).delete()
    }
}

actual class Lock actual constructor() {
    private val lock = ReentrantLock()
    actual fun <T> withLock(block: () -> T): T = lock.withLock(block)
}
