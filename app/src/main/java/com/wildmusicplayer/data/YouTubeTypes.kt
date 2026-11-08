package com.wildmusicplayer.data

import java.util.Locale

// What both app versions share about YouTube songs. The extractor itself (YouTubeDownloadManager)
// is only in the full version: src/full, with an empty stand-in in src/play.

data class YouTubeTrack(
    val id: String,
    val title: String,
    val uploaderName: String,
    val durationSeconds: Long,
    val thumbnailUrl: String?,
    val webUrl: String,
    val viewCount: Long = -1L,
    val likeCount: Long = -1L,
    val isUploaderVerified: Boolean = false,
    // True for YouTube Music "Songs" results: title is the clean song name, uploaderName the artist
    val isMusicSong: Boolean = false
) {
    val formattedDuration: String
        get() {
            if (durationSeconds <= 0) return "--:--"
            val minutes = durationSeconds / 60
            val seconds = durationSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }

    val formattedViews: String?
        get() = formatMetric(viewCount, "views")

    val formattedLikes: String?
        get() = formatMetric(likeCount, "likes")

    companion object {
        fun formatMetric(count: Long, unit: String): String? {
            if (count < 0) return null
            val formatted = when {
                count >= 1_000_000_000 -> String.format(Locale.US, "%.1fB", count / 1_000_000_000.0)
                count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
                count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
                else -> count.toString()
            }
            return "$formatted $unit"
        }
    }
}

/**
 * A YouTube playlist as loaded for importing. [tracks] are the playable songs (only the first page for
 * a preview); [skipped] counts deleted, private or blocked videos; [truncated] is true when the playlist
 * has more songs than [MAX_PLAYLIST_TRACKS].
 */
data class YouTubePlaylist(
    val id: String,
    val name: String,
    val uploader: String,
    val thumbnailUrl: String?,
    val totalCount: Long,
    val tracks: List<YouTubeTrack>,
    val skipped: Int = 0,
    val truncated: Boolean = false
)

/** The most songs one playlist import takes (YouTube playlists can hold up to 5000). */
const val MAX_PLAYLIST_TRACKS = 2000

/** YouTube answered, but the content isn't there for us (private, deleted, blocked); [message] says so. */
class YouTubeUnavailableException(message: String) : Exception(message)

enum class SearchMode(val label: String) {
    SONGS("Songs"),
    VIDEOS("Videos")
}

/** What a paused download had: which audio stream ([streamId], YouTube's itag) and its full size. */
internal data class PartialDownload(val streamId: String, val totalBytes: Long) {
    fun format(): String = "$streamId\n$totalBytes"

    companion object {
        fun parse(text: String?): PartialDownload? {
            val lines = text?.lines() ?: return null
            val total = lines.getOrNull(1)?.trim()?.toLongOrNull() ?: return null
            val streamId = lines.first().trim().takeIf { it.isNotEmpty() } ?: return null
            return if (total > 0) PartialDownload(streamId, total) else null
        }
    }
}


/** The total file size from a "Content-Range: bytes 0-1048575/3547712" header, or null. */
internal fun parseContentRangeTotal(header: String?): Long? =
    header?.substringAfterLast('/', "")?.trim()?.toLongOrNull()

/** Replaces characters that aren't allowed in file names and caps the length. */
internal fun sanitizeFileName(title: String): String {
    val cleaned = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(120).trim()
    return cleaned.ifEmpty { "track" }
}

/** Returns "base.ext", or "base (2).ext", "base (3).ext"... for the first name that isn't taken yet. */
internal fun uniqueFileName(base: String, extension: String, exists: (String) -> Boolean): String {
    var candidate = "$base.$extension"
    var n = 2
    while (exists(candidate)) {
        candidate = "$base ($n).$extension"
        n++
    }
    return candidate
}
