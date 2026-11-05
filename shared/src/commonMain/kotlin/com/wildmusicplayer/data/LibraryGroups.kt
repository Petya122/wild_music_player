package com.wildmusicplayer.data

import com.wildmusicplayer.model.Song

/** How the library is grouped in the Songs tab. */
enum class GroupKind { ALBUM, ARTIST, FOLDER }

/**
 * Songs that belong together: one album, one artist or one folder. [key] stays the same while the
 * library reloads, so an open group can be found again; [songs] are in playing order.
 */
data class SongGroup(
    val kind: GroupKind,
    val key: String,
    val title: String,
    /** The album's artist, or where the folder is ("Music/Rock"). */
    val subtitle: String,
    val artUri: String?,
    val songs: List<Song>,
    /** Release year of an album (the newest one tagged); 0 = unknown. */
    val year: Int = 0,
    /** How many albums an artist's songs come from. */
    val albumCount: Int = 0
) {
    val totalDurationMs: Long get() = songs.sumOf { it.duration }
}

const val UNKNOWN_ALBUM = "Unknown Album"
const val UNKNOWN_ARTIST = "Unknown Artist"
const val VARIOUS_ARTISTS = "Various Artists"

/** Album order: disc and track as tagged, untagged songs after them, then by title. */
private val albumOrder = compareBy<Song>({ if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }, { it.title.lowercase() })

/**
 * Albums, A to Z with "Unknown Album" last. Songs share an album when they have the same album cover
 * entry (Android's album ID); songs without one are grouped by album name.
 */
fun albumsOf(songs: List<Song>): List<SongGroup> =
    songs.filter { !it.isOnline }
        .groupBy { it.albumArtUri ?: ("name:" + it.album.lowercase()) }
        .map { (key, albumSongs) ->
            val sorted = albumSongs.sortedWith(albumOrder)
            val first = sorted.first()
            SongGroup(
                kind = GroupKind.ALBUM,
                key = key,
                title = first.album.ifBlank { UNKNOWN_ALBUM },
                subtitle = albumArtist(sorted),
                artUri = first.albumArtUri,
                songs = sorted,
                year = sorted.maxOf { it.year }
            )
        }
        .sortedWith(compareBy({ it.title == UNKNOWN_ALBUM }, { it.title.lowercase() }))

/** The album's artist: the one on most of its songs, or "Various Artists" when no one has the majority. */
private fun albumArtist(songs: List<Song>): String {
    val counts = songs.groupingBy { it.artist.lowercase() }.eachCount()
    val (top, count) = counts.maxBy { it.value }
    if (counts.size > 1 && count * 2 <= songs.size) return VARIOUS_ARTISTS
    return songs.first { it.artist.lowercase() == top }.artist.ifBlank { UNKNOWN_ARTIST }
}

/** Artists, A to Z with "Unknown Artist" last; each artist's songs by album, then in album order. */
fun artistsOf(songs: List<Song>): List<SongGroup> =
    songs.filter { !it.isOnline }
        .groupBy { it.artist.trim().lowercase() }
        .map { (key, artistSongs) ->
            val sorted = artistSongs.sortedWith(compareBy<Song> { it.album.lowercase() }.then(albumOrder))
            SongGroup(
                kind = GroupKind.ARTIST,
                key = key,
                title = mostCommonSpelling(artistSongs.map { it.artist.trim() }).ifBlank { UNKNOWN_ARTIST },
                subtitle = "",
                artUri = sorted.firstNotNullOfOrNull { it.albumArtUri },
                songs = sorted,
                albumCount = sorted.map { it.album.lowercase() }.distinct().size
            )
        }
        .sortedWith(compareBy({ it.title == UNKNOWN_ARTIST }, { it.title.lowercase() }))

/**
 * Folders on the phone, by their full path (two different folders both called "Music" stay apart),
 * in path order. Songs inside are A to Z by title.
 */
fun foldersOf(songs: List<Song>): List<SongGroup> =
    songs.filter { !it.isOnline }
        .groupBy { it.folderPath ?: ("name:" + it.folderName) }
        .map { (key, folderSongs) ->
            val first = folderSongs.first()
            SongGroup(
                kind = GroupKind.FOLDER,
                key = key,
                title = first.folderName,
                subtitle = first.folderPath?.let(::readableFolderPath) ?: first.folderName,
                artUri = null,
                songs = folderSongs.sortedBy { it.title.lowercase() }
            )
        }
        .sortedBy { it.subtitle.lowercase() }

/** "/storage/emulated/0/Music/Rock" → "Music/Rock"; a memory card's folders start with "SD card/". */
fun readableFolderPath(path: String): String {
    val internal = Regex("^/storage/emulated/\\d+/?")
    val card = Regex("^/storage/[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}/?")
    return when {
        internal.containsMatchIn(path) -> path.replace(internal, "").ifEmpty { "Phone storage" }
        card.containsMatchIn(path) -> "SD card/" + path.replace(card, "")
        else -> path.trimStart('/')
    }.trimEnd('/')
}

/** The group with [key] among [groups], e.g. to reopen an album after the library reloaded. */
fun List<SongGroup>.withKey(key: String?): SongGroup? = key?.let { k -> firstOrNull { it.key == k } }

/** "Queen" on 9 songs and "queen" on 1 → "Queen" (on a tie, the first one seen). */
private fun mostCommonSpelling(names: List<String>): String =
    names.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: ""
