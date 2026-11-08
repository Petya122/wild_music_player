package com.wildmusicplayer.data

// YouTube and YouTube Music playlist links, for importing a whole playlist (GitHub version only).

private val playlistParam = Regex("""[?&]list=([A-Za-z0-9_-]+)""")
private val musicBrowsePage = Regex("""/browse/VL([A-Za-z0-9_-]+)""")

/**
 * The playlist ID in a YouTube or YouTube Music link, or null when the link has none.
 * youtube.com/playlist?list=X, a song inside a playlist (watch?v=…&list=X), youtu.be/…?list=X,
 * music.youtube.com/playlist?list=X and music.youtube.com/browse/VLX all give X.
 */
fun youTubePlaylistId(link: String): String? {
    val text = link.trim()
    if (!text.contains("youtube.com", ignoreCase = true) && !text.contains("youtu.be", ignoreCase = true)) return null
    return playlistParam.find(text)?.groupValues?.get(1)
        ?: musicBrowsePage.find(text)?.groupValues?.get(1)
}

/**
 * Why the playlist with this [id] can't be imported, or null when it can. Mixes ("RD…") are made for
 * each listener and change every time; Liked videos, Liked music and Watch Later need a Google sign-in.
 */
fun playlistImportBlocker(id: String): String? = when {
    id.startsWith("RD") ->
        "YouTube Mixes are made for each listener and change every time, so they can't be imported. " +
            "Save the songs to a playlist on YouTube first, then share that playlist's link."
    id == "LL" || id == "LM" || id == "WL" ->
        "Liked songs and Watch Later are private to your Google account, so they can't be imported."
    else -> null
}

/** An imported playlist opened after this long is updated by itself (when online). */
const val PLAYLIST_AUTO_UPDATE_MS = 12 * 60 * 60 * 1000L

/** "updated just now", "updated 5 min ago", "updated 3 h ago", "updated 2 days ago". */
fun updatedAgoText(updatedAt: Long, now: Long): String {
    val minutes = ((now - updatedAt) / 60_000L).coerceAtLeast(0L)
    return when {
        minutes < 1 -> "updated just now"
        minutes < 60 -> "updated $minutes min ago"
        minutes < 24 * 60 -> "updated ${minutes / 60} h ago"
        minutes < 48 * 60 -> "updated yesterday"
        else -> "updated ${minutes / (24 * 60)} days ago"
    }
}

/** The address used to load (and later update) the playlist with this [id]. */
fun youTubePlaylistUrl(id: String): String = "https://www.youtube.com/playlist?list=$id"
