package com.wildmusicplayer.data

import com.wildmusicplayer.platform.FileStore
import com.wildmusicplayer.platform.Lock
import com.wildmusicplayer.service.storageJson
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Remembers which YouTube video each downloaded file came from (video ID → MediaStore ID),
 * so a playlist's online track is only "on the phone" when that exact video was downloaded,
 * not just any song with the same title and artist.
 */
class DownloadIndex(private val files: FileStore) {

    private val lock = Lock()
    private val entries: MutableMap<String, Long> by lazy { load() }

    fun record(videoId: String, mediaStoreId: Long) = lock.withLock {
        entries[videoId] = mediaStoreId
        save()
    }

    fun mediaStoreIdFor(videoId: String): Long? = lock.withLock { entries[videoId] }

    /** The MediaStore ID of the downloaded copy of [webUrl], if the app downloaded it. */
    fun downloadedIdFor(webUrl: String): Long? = youTubeVideoId(webUrl)?.let { mediaStoreIdFor(it) }

    fun forgetMediaStoreId(mediaStoreId: Long) = lock.withLock {
        if (entries.values.removeAll { it == mediaStoreId }) save()
    }

    private fun load(): MutableMap<String, Long> = try {
        files.readText(FILE_NAME)
            ?.let { storageJson.decodeFromString(serializer, it).toMutableMap() }
            ?: mutableMapOf()
    } catch (e: Exception) {
        mutableMapOf()
    }

    private fun save() {
        files.writeText(FILE_NAME, storageJson.encodeToString(serializer, entries))
    }

    companion object {
        const val FILE_NAME = "download_index.json"
        private val serializer = MapSerializer(String.serializer(), Long.serializer())
    }
}

private val videoIdPatterns = listOf(
    Regex("""[?&]v=([A-Za-z0-9_-]{11})"""),
    Regex("""youtu\.be/([A-Za-z0-9_-]{11})"""),
    Regex("""/(?:shorts|embed|live)/([A-Za-z0-9_-]{11})""")
)

/**
 * The 11-character video ID in a YouTube or YouTube Music link, or null.
 * youtube.com/watch?v=X, music.youtube.com/watch?v=X and youtu.be/X all give X.
 */
fun youTubeVideoId(url: String): String? =
    videoIdPatterns.firstNotNullOfOrNull { it.find(url)?.groupValues?.get(1) }
