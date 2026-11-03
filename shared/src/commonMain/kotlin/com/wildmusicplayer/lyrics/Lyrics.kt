package com.wildmusicplayer.lyrics

/** One line of synced lyrics, shown from [timeMs] on. Empty text is an instrumental gap. */
data class LyricLine(val timeMs: Long, val text: String)

/** What the lyrics view shows for a song. */
sealed interface LyricsResult {
    data class Synced(val lines: List<LyricLine>) : LyricsResult
    data class Plain(val text: String) : LyricsResult
    data object Instrumental : LyricsResult
    data object NotFound : LyricsResult
    /** Offline Mode is on and this song's lyrics were never saved. */
    data object Offline : LyricsResult
    /** No connection, or the lyrics service didn't answer. */
    data object Failed : LyricsResult
}

private val timeTagRegex = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
private val offsetTagRegex = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)

/**
 * Parses LRC text: "[01:02.50]Words", several time tags on one line ("[00:10.00][01:20.00]Chorus"),
 * and an "[offset:+300]" tag (a positive offset shows lines earlier). Other tags like [ar:] are skipped.
 * Lines come back sorted by time.
 */
fun parseLrc(text: String): List<LyricLine> {
    val offsetMs = offsetTagRegex.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    val lines = mutableListOf<LyricLine>()
    for (rawLine in text.lines()) {
        var rest = rawLine.trim()
        val times = mutableListOf<Long>()
        while (true) {
            val match = timeTagRegex.find(rest)?.takeIf { it.range.first == 0 } ?: break
            val (minutes, seconds, fraction) = match.destructured
            val fractionMs = if (fraction.isEmpty()) 0L else fraction.padEnd(3, '0').take(3).toLong()
            times += minutes.toLong() * 60_000 + seconds.toLong() * 1000 + fractionMs
            rest = rest.substring(match.range.last + 1).trimStart()
        }
        times.forEach { lines += LyricLine((it - offsetMs).coerceAtLeast(0), rest) }
    }
    return lines.sortedBy { it.timeMs }
}

/** The line playing at [positionMs]: the last one that has started, or -1 before the first. */
fun currentLineIndex(lines: List<LyricLine>, positionMs: Long): Int {
    var low = 0
    var high = lines.size - 1
    var found = -1
    while (low <= high) {
        val mid = (low + high) / 2
        if (lines[mid].timeMs <= positionMs) {
            found = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return found
}
