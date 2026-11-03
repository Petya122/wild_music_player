package com.wildmusicplayer.model

/**
 * Represents an audio track stored locally on the device, or an online track
 * queued for streaming (see [isOnline]). URIs are plain strings so the model works on every platform:
 * "content://media/…" on Android, "wmp-yt://stream?url=…" for online tracks.
 */
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val contentUri: String,
    val albumArtUri: String?,
    val folderName: String = "Music",
    /** Where the file is on the phone ("/storage/emulated/0/Music/song.mp3"), when known. */
    val filePath: String? = null
) {
    /** True for YouTube tracks whose stream URL is resolved at play time by PlaybackService. */
    val isOnline: Boolean
        get() = isOnlineUri(contentUri)

    /** Media3 media ID: the MediaStore ID for local files, the stream URI for online tracks. */
    val mediaId: String
        get() = if (isOnline) "online:$contentUri" else id.toString()

    companion object {
        const val ONLINE_SCHEME = "wmp-yt"
        private const val ONLINE_PREFIX = "$ONLINE_SCHEME://stream?url="

        /** ID used for every song that isn't a MediaStore file (online streams). */
        const val ONLINE_SONG_ID = -1L

        /** Builds a queueable online song; PlaybackService turns its URI into a stream URL when it plays. */
        fun fromOnline(
            title: String,
            artist: String,
            webUrl: String,
            durationSeconds: Long,
            thumbnailUrl: String?
        ): Song = Song(
            id = ONLINE_SONG_ID,
            title = title,
            artist = artist,
            album = "YouTube Stream",
            duration = durationSeconds * 1000L,
            contentUri = onlineUri(webUrl),
            albumArtUri = thumbnailUrl
        )

        /** "wmp-yt://stream?url=<encoded web URL>", the same text Android's Uri.Builder produced. */
        fun onlineUri(webUrl: String): String = ONLINE_PREFIX + encodeUriComponent(webUrl)

        fun isOnlineUri(uri: String): Boolean = uri.startsWith("$ONLINE_SCHEME:")

        /** The YouTube page URL inside an online song URI, or null for any other URI. */
        fun webUrlOf(uri: String): String? {
            if (!isOnlineUri(uri)) return null
            val query = uri.substringAfter('?', "")
            val value = query.split('&').firstOrNull { it.startsWith("url=") }?.removePrefix("url=")
            return value?.let { decodeUriComponent(it) }
        }
    }
}

// Same characters Android's Uri.encode() leaves alone
private const val UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-!.~'()*"

internal fun encodeUriComponent(text: String): String = buildString {
    for (byte in text.encodeToByteArray()) {
        val c = byte.toInt().toChar()
        if (byte >= 0 && c in UNRESERVED) {
            append(c)
        } else {
            append('%')
            append("0123456789ABCDEF"[(byte.toInt() shr 4) and 0xF])
            append("0123456789ABCDEF"[byte.toInt() and 0xF])
        }
    }
}

internal fun decodeUriComponent(text: String): String {
    val bytes = ArrayList<Byte>(text.length)
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            c == '%' && i + 2 <= text.lastIndex -> {
                val hex = text.substring(i + 1, i + 3).toIntOrNull(16)
                if (hex != null) {
                    bytes += hex.toByte()
                    i += 3
                    continue
                }
                bytes += c.code.toByte()
            }
            c == '+' -> bytes += ' '.code.toByte()
            else -> c.toString().encodeToByteArray().forEach { bytes += it }
        }
        i++
    }
    return bytes.toByteArray().decodeToString()
}
