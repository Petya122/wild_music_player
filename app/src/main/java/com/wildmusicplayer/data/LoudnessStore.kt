package com.wildmusicplayer.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File

/**
 * Measured loudness (LUFS) per MediaStore song ID, saved in the app's files.
 * Songs that couldn't be measured are remembered too, so they aren't retried every time.
 * (Moves to a proper database in the production phase.)
 */
class LoudnessStore private constructor(private val file: File) {

    /** Background measuring: [measured] of [total] library songs done. */
    data class Progress(val measured: Int, val total: Int, val running: Boolean)

    private val values: MutableMap<Long, Double> by lazy { load() }

    private val _progress = MutableStateFlow(Progress(0, 0, running = false))
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    /** The song's loudness, or null if it hasn't been (or couldn't be) measured. */
    @Synchronized
    fun get(songId: Long): Double? = values[songId]?.takeIf { it != UNMEASURABLE }

    @Synchronized
    fun isKnown(songId: Long): Boolean = values.containsKey(songId)

    @Synchronized
    fun put(songId: Long, loudnessLufs: Double?) {
        values[songId] = loudnessLufs ?: UNMEASURABLE
        save()
    }

    fun updateProgress(measured: Int, total: Int, running: Boolean) {
        _progress.value = Progress(measured, total, running)
    }

    private fun load(): MutableMap<Long, Double> = try {
        val json = JSONObject(file.readText())
        json.keys().asSequence()
            .mapNotNull { key -> key.toLongOrNull()?.let { it to json.getDouble(key) } }
            .toMap(mutableMapOf())
    } catch (e: Exception) {
        mutableMapOf()
    }

    private fun save() {
        try {
            val json = JSONObject()
            values.forEach { (id, lufs) -> json.put(id.toString(), lufs) }
            file.writeText(json.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        // Stored for songs that couldn't be decoded (JSON has no NaN)
        private const val UNMEASURABLE = -999.0

        @Volatile
        private var instance: LoudnessStore? = null

        fun getInstance(context: Context): LoudnessStore {
            return instance ?: synchronized(this) {
                instance ?: LoudnessStore(File(context.applicationContext.filesDir, "loudness.json"))
                    .also { instance = it }
            }
        }
    }
}
