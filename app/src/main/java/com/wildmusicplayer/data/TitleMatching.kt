package com.wildmusicplayer.data

/**
 * Decides whether a local song title and an online track title refer to the same song:
 * equal ignoring case and surrounding spaces, or one contains the other when both are
 * at least 5 characters long (so very short titles like "Intro" don't match everything).
 */
fun titlesMatch(localTitle: String, onlineTitle: String): Boolean {
    val cleanLocal = localTitle.trim().lowercase()
    val cleanOnline = onlineTitle.trim().lowercase()
    if (cleanLocal.isEmpty() || cleanOnline.isEmpty()) return false
    return cleanLocal == cleanOnline ||
        (cleanLocal.length >= 5 && cleanOnline.length >= 5 &&
            (cleanLocal.contains(cleanOnline) || cleanOnline.contains(cleanLocal)))
}
