package com.wildmusicplayer.data

/**
 * The tags written into a downloaded file: what the library, lock screen and car radio show.
 * Album is the song title ("single"), so every download gets its own cover in MediaStore
 * (MediaStore groups cover art by album name).
 */
data class TrackMetadata(
    val title: String,
    val artist: String,
    val album: String,
    val coverUrl: String?
)

fun trackMetadataFor(track: YouTubeTrack): TrackMetadata {
    val (artist, title) = if (track.isMusicSong) {
        // YouTube Music songs already have a clean title and the real artist
        track.uploaderName.trim().ifEmpty { "Unknown Artist" } to track.title.trim()
    } else {
        parseVideoTitle(track.title, track.uploaderName)
    }
    return TrackMetadata(
        title = title,
        artist = artist,
        album = title,
        coverUrl = track.thumbnailUrl?.let { upscaleGoogleThumbnail(it) }
    )
}

// Bracketed parts like "(Official Video)" or "[Lyrics]" are dropped when they contain one of these
private val noiseKeywords = listOf(
    "official", "video", "audio", "lyric", "lyrics", "visualizer", "visualiser",
    "hd", "hq", "4k", "8k", "mv", "m/v", "clip", "music video", "full song"
)

private val bracketRegex = Regex("""\s*[(\[【]([^)\]】]*)[)\]】]""")
private val trailingNoiseRegex = Regex(
    """\s*[-|•]?\s*(official\s+(music\s+)?(video|audio|lyric video|visualizer)|lyrics?(\s+video)?)\s*$""",
    RegexOption.IGNORE_CASE
)
private val separators = listOf(" - ", " – ", " — ", " | ")

/**
 * Splits a YouTube video title into (artist, title).
 * "Queen - Bohemian Rhapsody (Official Video Remastered)" → ("Queen", "Bohemian Rhapsody").
 * Without an "Artist - Title" pattern the channel name becomes the artist.
 */
internal fun parseVideoTitle(videoTitle: String, channelName: String): Pair<String, String> {
    val cleaned = stripTitleNoise(videoTitle)
    for (separator in separators) {
        val index = cleaned.indexOf(separator)
        if (index > 0) {
            val artist = cleaned.substring(0, index).trim()
            val title = cleaned.substring(index + separator.length).trim().trim('"', '\'', '“', '”')
            if (artist.isNotEmpty() && title.isNotEmpty()) {
                return artist to title
            }
        }
    }
    val artist = cleanChannelName(channelName).ifEmpty { "Unknown Artist" }
    return artist to cleaned.trim('"', '\'', '“', '”').ifEmpty { videoTitle.trim() }
}

/** Removes "(Official Video)", "[4K]", "| Lyrics" and similar from a video title. */
internal fun stripTitleNoise(videoTitle: String): String {
    var result = bracketRegex.replace(videoTitle) { match ->
        val inside = match.groupValues[1].lowercase()
        val words = inside.split(Regex("[^\\p{L}\\p{N}/]+")).filter { it.isNotEmpty() }
        val isNoise = noiseKeywords.any { keyword ->
            if (keyword.contains(' ')) inside.contains(keyword) else keyword in words
        }
        if (isNoise) "" else match.value
    }
    result = trailingNoiseRegex.replace(result, "")
    return result.replace(Regex("\\s+"), " ").trim()
}

/** "Queen - Topic" → "Queen", "QueenVEVO" → "Queen", "Queen Official" → "Queen". */
internal fun cleanChannelName(channelName: String): String =
    channelName
        .replace(Regex("""\s*-\s*Topic$""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s*VEVO$""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s*\(?official\)?""", RegexOption.IGNORE_CASE), "")
        .trim()

/**
 * YouTube Music artwork URLs carry their size ("=w120-h120-l90-rj"); ask for 1000×1000 instead.
 * Other URLs are returned unchanged.
 */
internal fun upscaleGoogleThumbnail(url: String): String =
    if (url.contains("googleusercontent.com")) {
        url.replace(Regex("""=w\d+-h\d+"""), "=w1000-h1000")
    } else {
        url
    }
