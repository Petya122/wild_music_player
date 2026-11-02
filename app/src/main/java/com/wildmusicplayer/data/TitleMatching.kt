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

/**
 * Whether a local song is the downloaded copy of an online playlist track. Downloads are tagged
 * with the clean song title ("Halo"), while the playlist may hold the video title
 * ("Beyoncé - Halo (Official Video)"), so both forms are compared.
 */
fun isDownloadedCopyOf(localTitle: String, onlineTitle: String, onlineArtist: String): Boolean =
    titlesMatch(localTitle, onlineTitle) ||
        titlesMatch(localTitle, parseVideoTitle(onlineTitle, onlineArtist).second)
