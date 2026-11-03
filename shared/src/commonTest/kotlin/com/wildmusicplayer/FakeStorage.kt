package com.wildmusicplayer

import com.wildmusicplayer.platform.FileStore
import com.wildmusicplayer.platform.KeyValueStore

class FakeFileStore : FileStore {
    val files = mutableMapOf<String, String>()
    override fun readText(name: String): String? = files[name]
    override fun writeText(name: String, text: String) { files[name] = text }
    override fun delete(name: String) { files.remove(name) }
}

class FakeKeyValueStore : KeyValueStore {
    val values = mutableMapOf<String, Any>()
    override fun getString(key: String): String? = values[key] as? String
    override fun putString(key: String, value: String) { values[key] = value }
    override fun getBoolean(key: String, default: Boolean): Boolean = values[key] as? Boolean ?: default
    override fun putBoolean(key: String, value: Boolean) { values[key] = value }
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String): Set<String>? = values[key] as? Set<String>
    override fun putStringSet(key: String, value: Set<String>) { values[key] = value }
    override fun remove(key: String) { values.remove(key) }
}
