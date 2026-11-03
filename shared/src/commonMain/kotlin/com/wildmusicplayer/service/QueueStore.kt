package com.wildmusicplayer.service

import com.wildmusicplayer.model.Song
import com.wildmusicplayer.platform.FileStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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
    get() = Song.isOnlineUri(uri)

fun QueuedSong.toSong(): Song = Song(
    id = id,
    title = title,
    artist = artist,
    album = album,
    duration = durationMs,
    contentUri = uri,
    albumArtUri = artworkUri
)

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
class QueueStore(private val files: FileStore) {

    fun save(queue: SavedQueue) {
        files.writeText(FILE_NAME, toJson(queue))
    }

    fun load(): SavedQueue? = files.readText(FILE_NAME)?.let { fromJson(it) }

    fun clear() {
        files.delete(FILE_NAME)
    }

    // The file layout written since v0.7.0; missing artwork is stored as ""
    @Serializable
    private class QueueJson(
        val version: Int = 1,
        val songs: List<SongJson> = emptyList(),
        val index: Int = 0,
        val positionMs: Long = 0L,
        val shuffle: Boolean = false,
        val repeatMode: Int = 0
    )

    @Serializable
    private class SongJson(
        val id: Long,
        val title: String = "Unknown Title",
        val artist: String = "Unknown Artist",
        val album: String = "",
        val durationMs: Long = 0L,
        val uri: String,
        val artworkUri: String = ""
    )

    companion object {
        const val FILE_NAME = "last_queue.json"

        fun toJson(queue: SavedQueue): String = storageJson.encodeToString(
            QueueJson.serializer(),
            QueueJson(
                songs = queue.songs.map {
                    SongJson(it.id, it.title, it.artist, it.album, it.durationMs, it.uri, it.artworkUri ?: "")
                },
                index = queue.index,
                positionMs = queue.positionMs,
                shuffle = queue.shuffle,
                repeatMode = queue.repeatMode
            )
        )

        /** Parses a saved queue; returns null for empty or unreadable data. */
        fun fromJson(json: String): SavedQueue? = try {
            val root = storageJson.decodeFromString(QueueJson.serializer(), json)
            val songs = root.songs.map {
                QueuedSong(it.id, it.title, it.artist, it.album, it.durationMs, it.uri, it.artworkUri.takeIf { a -> a.isNotBlank() })
            }
            if (songs.isEmpty()) {
                null
            } else {
                SavedQueue(
                    songs = songs,
                    index = root.index.coerceIn(0, songs.lastIndex),
                    positionMs = root.positionMs.coerceAtLeast(0L),
                    shuffle = root.shuffle,
                    repeatMode = root.repeatMode
                )
            }
        } catch (e: Exception) {
            null
        }
    }
}

/** JSON settings for the app's saved files: tolerant when reading, every field written. */
internal val storageJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true
}
