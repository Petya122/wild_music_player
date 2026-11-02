package com.wildmusicplayer.data

import java.text.Normalizer
import kotlin.math.log10

// Versions people usually don't want unless they typed the word themselves
private val unwantedVersionTerms = listOf(
    "sped up", "speed up", "slowed", "reverb", "nightcore", "8d", "bass boosted", "earrape",
    "cover", "karaoke", "instrumental", "live", "remix", "reaction",
    "1 hour", "10 hour", "10 hours"
)

private val officialTitleTerms = listOf("official video", "official music video", "official audio")

/**
 * "Best match" score for a search result: how well the title and artist/channel match
 * the query words comes first, then signs of an official release. Views only nudge the
 * order (log scale), so a huge unrelated hit can't outrank the song you typed.
 */
fun searchScore(track: YouTubeTrack, query: String): Double {
    val queryNorm = normalizeForSearch(query)
    val queryTokens = queryNorm.split(' ').filter { it.isNotEmpty() }.distinct()
    val titleNorm = normalizeForSearch(track.title)
    val uploaderLower = track.uploaderName.lowercase().trim()
    val uploaderNorm = normalizeForSearch(uploaderLower.removeSuffix(" - topic"))

    val titleTokens = titleNorm.split(' ').filter { it.isNotEmpty() }
    val candidateTokens = (titleTokens + uploaderNorm.split(' ')).filter { it.isNotEmpty() }.toSet()

    var score = 0.0

    // 1. Query words found in title + artist/channel
    if (queryTokens.isNotEmpty()) {
        val matched = queryTokens.count { q ->
            q in candidateTokens || (q.length >= 3 && candidateTokens.any { it.startsWith(q) })
        }
        val coverage = matched.toDouble() / queryTokens.size
        score += coverage * 1000
        if (matched == queryTokens.size) score += 300
        if (containsPhrase(titleNorm, queryNorm)) score += 200

        // Prefer "Promise" over "Promise (Live in Tokyo 2015 Full Concert)"
        val extraTitleWords = titleTokens.count { it !in queryTokens }
        score -= minOf(extraTitleWords * 8, 120).toDouble()
    }

    // 2. Official releases
    if (uploaderLower.endsWith(" - topic")) score += 120
    if (uploaderLower.contains("vevo") || uploaderLower.contains("official")) score += 80
    if (track.isUploaderVerified) score += 50
    if (officialTitleTerms.any { titleNorm.contains(it) }) score += 60

    // 3. Popularity, gently
    if (track.viewCount > 0) score += log10(track.viewCount.toDouble()) * 15

    // 4. Normal song length
    when {
        track.durationSeconds in 90..450 -> score += 40
        track.durationSeconds > 900 -> score -= 300
        track.durationSeconds in 1..59 -> score -= 100
    }

    // 5. Edits and derivative versions, unless asked for
    for (term in unwantedVersionTerms) {
        if (containsPhrase(titleNorm, term) && !containsPhrase(queryNorm, term)) {
            score -= 250
        }
    }

    return score
}

/**
 * Lowercase, accents removed, punctuation to spaces:
 * "Move On, Baby! (Official)" → "move on baby official", "Beyoncé" → "beyonce".
 */
internal fun normalizeForSearch(text: String): String =
    Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

// Whole-word phrase check on normalized text ("live" doesn't match "alive")
private fun containsPhrase(normalizedText: String, phrase: String): Boolean {
    val p = normalizeForSearch(phrase)
    if (p.isEmpty()) return false
    return " $normalizedText ".contains(" $p ")
}
