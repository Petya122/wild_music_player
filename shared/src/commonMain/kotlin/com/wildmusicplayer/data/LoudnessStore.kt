package com.wildmusicplayer.data

import com.wildmusicplayer.platform.FileStore
import com.wildmusicplayer.platform.Lock
import com.wildmusicplayer.service.storageJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Measured loudness (LUFS) per song ID, saved in the app's files.
 * Songs that couldn't be measured are remembered too, so they aren't retried every time.
 * (Moves to a proper database in the production phase.)
 */
class LoudnessStore(private val files: FileStore) {

    /** Background measuring: [measured] of [total] library songs done. */
    data class Progress(val measured: Int, val total: Int, val running: Boolean)

    private val lock = Lock()
    private val values: MutableMap<Long, Double> by lazy { load() }

    private val _progress = MutableStateFlow(Progress(0, 0, running = false))
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    /** The song's loudness, or null if it hasn't been (or couldn't be) measured. */
    fun get(songId: Long): Double? = lock.withLock { values[songId]?.takeIf { it != UNMEASURABLE } }

    fun isKnown(songId: Long): Boolean = lock.withLock { values.containsKey(songId) }

    fun put(songId: Long, loudnessLufs: Double?) = lock.withLock {
        values[songId] = loudnessLufs ?: UNMEASURABLE
        save()
    }

    fun updateProgress(measured: Int, total: Int, running: Boolean) {
        _progress.value = Progress(measured, total, running)
    }

    private fun load(): MutableMap<Long, Double> = try {
        val stored = files.readText(FILE_NAME)?.let { storageJson.decodeFromString(serializer, it) } ?: emptyMap()
        stored.entries.mapNotNull { (key, lufs) -> key.toLongOrNull()?.let { it to lufs } }.toMap(mutableMapOf())
    } catch (e: Exception) {
        mutableMapOf()
    }

    private fun save() {
        files.writeText(FILE_NAME, storageJson.encodeToString(serializer, values.mapKeys { it.key.toString() }))
    }

    companion object {
        const val FILE_NAME = "loudness.json"

        // Stored for songs that couldn't be decoded (JSON has no NaN)
        private const val UNMEASURABLE = -999.0

        private val serializer = MapSerializer(String.serializer(), Double.serializer())
    }
}
