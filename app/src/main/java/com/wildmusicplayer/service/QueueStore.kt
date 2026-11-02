package com.wildmusicplayer.service

import android.content.Context
import com.wildmusicplayer.model.Song
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One queue entry, enough to rebuild the song without asking MediaStore or YouTube again. */
data class QueuedSong(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uri: String,
    val artworkUri: String?
)

val QueuedSong.isOnline: Boolean
    get() = uri.startsWith("${Song.ONLINE_SCHEME}:")

/** The playback queue as it was when the app last played. */
data class SavedQueue(
    val songs: List<QueuedSong>,
    val index: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeatMode: Int
) {
    /**
     * The queue for Offline Only Mode: online songs removed, keeping the same place in the queue.
     * If the current song was online, playback starts at the next local song. Null if nothing is left.
     */
    fun withoutOnlineSongs(): SavedQueue? {
        val kept = songs.filter { !it.isOnline }
        if (kept.isEmpty()) return null
        if (kept.size == songs.size) return this
        val currentIsLocal = songs.getOrNull(index)?.isOnline == false
        val newIndex = songs.take(index).count { !it.isOnline }.coerceAtMost(kept.lastIndex)
        return copy(songs = kept, index = newIndex, positionMs = if (currentIsLocal) positionMs else 0L)
    }
}

/**
 * Keeps the last queue on disk so playback can resume after the app was closed:
 * from the car's Play button (PlaybackService.onPlaybackResumption) or when the app opens.
 */
class QueueStore(private val file: File) {

    fun save(queue: SavedQueue) {
        try {
            // Write then rename, so a crash mid-write never leaves a half file behind
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(toJson(queue))
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun load(): SavedQueue? = try {
        if (file.exists()) fromJson(file.readText()) else null
    } catch (e: Exception) {
        null
    }

    fun clear() {
        file.delete()
    }

    companion object {
        fun forContext(context: Context) = QueueStore(File(context.applicationContext.filesDir, "last_queue.json"))

        internal fun toJson(queue: SavedQueue): String {
            val songs = JSONArray()
            queue.songs.forEach { song ->
                songs.put(JSONObject().apply {
                    put("id", song.id)
                    put("title", song.title)
                    put("artist", song.artist)
                    put("album", song.album)
                    put("durationMs", song.durationMs)
                    put("uri", song.uri)
                    put("artworkUri", song.artworkUri ?: "")
                })
            }
            return JSONObject().apply {
                put("version", 1)
                put("songs", songs)
                put("index", queue.index)
                put("positionMs", queue.positionMs)
                put("shuffle", queue.shuffle)
                put("repeatMode", queue.repeatMode)
            }.toString()
        }

        /** Parses a saved queue; returns null for empty or unreadable data. */
        internal fun fromJson(json: String): SavedQueue? = try {
            val root = JSONObject(json)
            val array = root.getJSONArray("songs")
            val songs = (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                QueuedSong(
                    id = obj.getLong("id"),
                    title = obj.optString("title", "Unknown Title"),
                    artist = obj.optString("artist", "Unknown Artist"),
                    album = obj.optString("album", ""),
                    durationMs = obj.optLong("durationMs", 0L),
                    uri = obj.getString("uri"),
                    artworkUri = obj.optString("artworkUri", "").takeIf { it.isNotBlank() }
                )
            }
            if (songs.isEmpty()) {
                null
            } else {
                SavedQueue(
                    songs = songs,
                    index = root.optInt("index", 0).coerceIn(0, songs.lastIndex),
                    positionMs = root.optLong("positionMs", 0L).coerceAtLeast(0L),
                    shuffle = root.optBoolean("shuffle", false),
                    repeatMode = root.optInt("repeatMode", 0)
                )
            }
        } catch (e: Exception) {
            null
        }
    }
}
