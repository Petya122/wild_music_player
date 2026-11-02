package com.wildmusicplayer.data

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Remembers which YouTube video each downloaded file came from (video ID → MediaStore ID),
 * so a playlist's online track is only "on the phone" when that exact video was downloaded,
 * not just any song with the same title and artist.
 */
class DownloadIndex private constructor(private val file: File) {

    private val entries: MutableMap<String, Long> by lazy { load() }

    @Synchronized
    fun record(videoId: String, mediaStoreId: Long) {
        entries[videoId] = mediaStoreId
        save()
    }

    @Synchronized
    fun mediaStoreIdFor(videoId: String): Long? = entries[videoId]

    /** The MediaStore ID of the downloaded copy of [webUrl], if the app downloaded it. */
    fun downloadedIdFor(webUrl: String): Long? = youTubeVideoId(webUrl)?.let { mediaStoreIdFor(it) }

    @Synchronized
    fun forgetMediaStoreId(mediaStoreId: Long) {
        if (entries.values.removeAll { it == mediaStoreId }) save()
    }

    private fun load(): MutableMap<String, Long> = try {
        val json = JSONObject(file.readText())
        json.keys().asSequence().associateWith { json.getLong(it) }.toMutableMap()
    } catch (e: Exception) {
        mutableMapOf()
    }

    private fun save() {
        try {
            file.writeText(JSONObject(entries as Map<*, *>).toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        @Volatile
        private var instance: DownloadIndex? = null

        fun getInstance(context: Context): DownloadIndex {
            return instance ?: synchronized(this) {
                instance ?: DownloadIndex(File(context.applicationContext.filesDir, "download_index.json"))
                    .also { instance = it }
            }
        }
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
