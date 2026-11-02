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
 *
 * The artist has to match too, so a same-titled cover or remix (Turbo's "Dragostea Din Tei")
 * isn't mistaken for the original (O-Zone's). Local songs without an artist (downloads from
 * before v0.6.0) still match on title alone.
 */
fun isDownloadedCopyOf(
    localTitle: String,
    localArtist: String,
    onlineTitle: String,
    onlineArtist: String
): Boolean {
    val (parsedArtist, parsedTitle) = parseVideoTitle(onlineTitle, onlineArtist)
    val titleMatches = titlesMatch(localTitle, onlineTitle) || titlesMatch(localTitle, parsedTitle)
    if (!titleMatches) return false
    if (isUnknownArtist(localArtist)) return true
    return artistsMatch(localArtist, onlineArtist) || artistsMatch(localArtist, parsedArtist)
}

/**
 * Same artist, allowing for channel suffixes ("O-Zone Official", "Queen - Topic"), accents and
 * features ("Peat Jr & Fernando" matches "Peat Jr").
 */
internal fun artistsMatch(localArtist: String, onlineArtist: String): Boolean {
    val local = normalizeForSearch(cleanChannelName(localArtist))
    val online = normalizeForSearch(cleanChannelName(onlineArtist))
    if (local.isEmpty() || online.isEmpty()) return false
    if (local.contains(online) || online.contains(local)) return true
    val localWords = local.split(' ').filter { it.length >= 3 }.toSet()
    return online.split(' ').any { it.length >= 3 && it in localWords }
}

internal fun isUnknownArtist(artist: String): Boolean =
    artist.isBlank() ||
        artist.equals("Unknown Artist", ignoreCase = true) ||
        artist.equals("<unknown>", ignoreCase = true)
