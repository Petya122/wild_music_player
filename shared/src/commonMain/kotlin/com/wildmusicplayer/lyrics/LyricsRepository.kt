package com.wildmusicplayer.lyrics

import com.wildmusicplayer.data.stripTitleNoise
import com.wildmusicplayer.model.Song
import com.wildmusicplayer.model.encodeUriComponent
import com.wildmusicplayer.platform.FileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.math.abs

/** A web page's status code and text. */
class HttpResult(val code: Int, val body: String)

/** Fetches a web page; null when there's no connection. Provided by each platform. */
fun interface HttpGet {
    suspend fun get(url: String): HttpResult?
}

/**
 * Song lyrics from LRCLIB (lrclib.net: free, no account). Synced lyrics are preferred, plain text
 * otherwise. Every answer, "no lyrics" included, is saved in [cache], so a song is only looked up once
 * and its lyrics work offline afterwards. "No lyrics" is asked again after a week, in case they've
 * been added since. In Offline Mode only saved lyrics are shown.
 */
class LyricsRepository(
    private val cache: FileStore,
    private val http: HttpGet,
    private val isOffline: () -> Boolean,
    private val now: () -> Long
) {

    suspend fun lyricsFor(song: Song): LyricsResult {
        val query = LyricsQuery.of(song)
        val fileName = query.cacheFileName()

        val saved = withContext(Dispatchers.Default) { cache.readText(fileName) }
            ?.let { runCatching { json.decodeFromString(SavedLyrics.serializer(), it) }.getOrNull() }
        if (saved != null && (saved.hasAnswer || now() - saved.savedAt < RETRY_NOT_FOUND_MS)) {
            return saved.toResult()
        }
        if (isOffline()) return saved?.toResult() ?: LyricsResult.Offline

        val found = lookUp(query) ?: return saved?.toResult() ?: LyricsResult.Failed
        val toSave = found.copy(savedAt = now())
        withContext(Dispatchers.Default) { cache.writeText(fileName, json.encodeToString(SavedLyrics.serializer(), toSave)) }
        return toSave.toResult()
    }

    /** The best lyrics LRCLIB has (an empty [SavedLyrics] when it has none), or null when it couldn't be asked. */
    private suspend fun lookUp(query: LyricsQuery): SavedLyrics? {
        // 1. The exact song, by its name, artist, album and length
        if (query.artist.isNotEmpty()) {
            val exact = fetch(query.getUrl()) ?: return null
            when (exact.code) {
                200 -> decodeTrack(exact.body)?.takeIf { it.hasLyrics }?.let { return it.toSaved(query) }
                400, 404 -> Unit
                else -> return null
            }
        }

        // 2. A search, for titles that don't match exactly ("Song (Radio Edit)", "Artist - Song")
        val search = fetch(query.searchUrl()) ?: return null
        if (search.code != 200) return null
        val tracks = runCatching { json.decodeFromString(ListSerializer(LrclibTrack.serializer()), search.body) }
            .getOrDefault(emptyList())
            .filter { it.hasLyrics }
        val best = tracks.firstOrNull { query.lengthMatches(it) && it.syncedLyrics != null }
            ?: tracks.firstOrNull { query.lengthMatches(it) }
            ?: tracks.firstOrNull()
        return best?.toSaved(query) ?: SavedLyrics()
    }

    // LRCLIB answers "busy" now and then: one more try a moment later
    private suspend fun fetch(url: String): HttpResult? {
        val result = http.get(url) ?: return null
        if (result.code != 503 && result.code != 429) return result
        delay(BUSY_RETRY_MS)
        return http.get(url)
    }

    private fun decodeTrack(body: String): LrclibTrack? =
        runCatching { json.decodeFromString(LrclibTrack.serializer(), body) }.getOrNull()

    // Synced lines only fit a recording of the same length: an extended mix gets the plain text
    private fun LrclibTrack.toSaved(query: LyricsQuery) = SavedLyrics(
        synced = syncedLyrics?.takeIf { query.lengthMatches(this) },
        plain = plainLyrics ?: syncedLyrics?.let { lrc -> parseLrc(lrc).joinToString("\n") { it.text } },
        instrumental = instrumental
    )

    companion object {
        private const val BUSY_RETRY_MS = 1_500L
        private const val RETRY_NOT_FOUND_MS = 7 * 24 * 60 * 60 * 1000L
        private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    }
}

/** What's asked for: the song's title and artist, cleaned up, and its length in seconds (0 = unknown). */
internal data class LyricsQuery(val title: String, val artist: String, val album: String, val durationSec: Long) {

    fun getUrl(): String = buildString {
        append("$API/get?track_name=").append(encodeUriComponent(title))
        append("&artist_name=").append(encodeUriComponent(artist))
        if (album.isNotEmpty()) append("&album_name=").append(encodeUriComponent(album))
        if (durationSec > 0) append("&duration=").append(durationSec)
    }

    fun searchUrl(): String = buildString {
        append("$API/search?track_name=").append(encodeUriComponent(baseTitle(title)))
        if (artist.isNotEmpty()) append("&artist_name=").append(encodeUriComponent(artist))
    }

    fun lengthMatches(track: LrclibTrack): Boolean {
        val length = track.duration ?: return durationSec == 0L
        return durationSec == 0L || abs(length - durationSec) <= LENGTH_TOLERANCE_SEC
    }

    fun cacheFileName(): String {
        val key = "${artist.lowercase()}|${title.lowercase()}|$durationSec"
        return "lyrics_${key.hashCode().toUInt().toString(16)}.json"
    }

    companion object {
        private const val API = "https://lrclib.net/api"
        private const val LENGTH_TOLERANCE_SEC = 4.0
        private val separators = listOf(" - ", " – ", " — ")
        private val unknownArtists = setOf("", "<unknown>", "unknown artist", "unknown")
        private val unknownAlbums = setOf("", "<unknown>", "unknown album", "music", "download", "downloads")

        fun of(song: Song): LyricsQuery {
            var title = stripTitleNoise(song.title)
            var artist = song.artist.trim()
            // Files named "Artist - Title" with no artist tag
            if (artist.lowercase() in unknownArtists) {
                val separator = separators.firstOrNull { title.contains(it) }
                if (separator != null) {
                    artist = title.substringBefore(separator).trim()
                    title = title.substringAfter(separator).trim()
                } else {
                    artist = ""
                }
            }
            val album = song.album.trim().takeUnless {
                it.lowercase() in unknownAlbums || it.equals(song.title, ignoreCase = true)
            } ?: ""
            return LyricsQuery(title, artist, album, song.duration / 1000)
        }

        /** "Move On Baby (Extended Mix)" → "Move On Baby". */
        fun baseTitle(title: String): String =
            Regex("""\s*[(\[][^)\]]*[)\]]""").replace(title, "").trim().ifEmpty { title }
    }
}

@Serializable
internal class LrclibTrack(
    val duration: Double? = null,
    val instrumental: Boolean = false,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null
) {
    val hasLyrics: Boolean get() = instrumental || !plainLyrics.isNullOrBlank() || !syncedLyrics.isNullOrBlank()
}

/** One song's saved answer. Nothing set = LRCLIB had no lyrics. */
@Serializable
internal data class SavedLyrics(
    val synced: String? = null,
    val plain: String? = null,
    val instrumental: Boolean = false,
    val savedAt: Long = 0L
) {
    val hasAnswer: Boolean get() = instrumental || !synced.isNullOrBlank() || !plain.isNullOrBlank()

    fun toResult(): LyricsResult {
        val lines = synced?.let { parseLrc(it) }.orEmpty()
        return when {
            lines.isNotEmpty() -> LyricsResult.Synced(lines)
            !plain.isNullOrBlank() -> LyricsResult.Plain(plain.trim())
            instrumental -> LyricsResult.Instrumental
            else -> LyricsResult.NotFound
        }
    }
}
