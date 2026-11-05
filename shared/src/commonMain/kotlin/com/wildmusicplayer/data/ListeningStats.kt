package com.wildmusicplayer.data

import com.wildmusicplayer.model.Song

/** One entry in a top list: a song, an artist or an album, with how often it was played. */
data class RankedEntry(
    val key: String,
    val title: String,
    val subtitle: String,
    val artUri: String?,
    val plays: Int,
    /** What tapping it plays: its played songs, most played first. */
    val songs: List<Song>
)

/** What the play counts say about the user's listening. */
data class ListeningStats(
    val totalPlays: Int,
    /** Plays × song length: an estimate, since a song counts after 30 seconds. */
    val listenedMs: Long,
    val songsPlayed: Int,
    val librarySize: Int,
    val topSongs: List<RankedEntry>,
    val topArtists: List<RankedEntry>,
    val topAlbums: List<RankedEntry>
)

/** Builds the stats from [playCounts] (song ID → plays) for the songs still in the library. */
fun listeningStats(songs: List<Song>, playCounts: Map<Long, Int>, topSize: Int = 10): ListeningStats {
    val played = songs.mapNotNull { song -> playCounts[song.id]?.takeIf { it > 0 }?.let { song to it } }
        .sortedWith(compareByDescending<Pair<Song, Int>> { it.second }.thenBy { it.first.title.lowercase() })

    fun ranked(groups: Map<String, List<Pair<Song, Int>>>, title: (List<Song>) -> String, subtitle: (List<Song>) -> String) =
        groups.map { (key, entries) ->
            val groupSongs = entries.map { it.first }
            RankedEntry(
                key = key,
                title = title(groupSongs),
                subtitle = subtitle(groupSongs),
                artUri = groupSongs.firstNotNullOfOrNull { it.albumArtUri },
                plays = entries.sumOf { it.second },
                songs = groupSongs
            )
        }.sortedWith(compareByDescending<RankedEntry> { it.plays }.thenBy { it.title.lowercase() }).take(topSize)

    return ListeningStats(
        totalPlays = played.sumOf { it.second },
        listenedMs = played.sumOf { (song, plays) -> song.duration * plays },
        songsPlayed = played.size,
        librarySize = songs.size,
        topSongs = played.take(topSize).map { (song, plays) ->
            RankedEntry(song.id.toString(), song.title, song.artist, song.albumArtUri, plays, listOf(song))
        },
        topArtists = ranked(
            played.groupBy { it.first.artist.trim().lowercase() },
            title = { it.first().artist.ifBlank { UNKNOWN_ARTIST } },
            subtitle = { "${it.size} ${if (it.size == 1) "song" else "songs"}" }
        ),
        topAlbums = ranked(
            played.groupBy { it.first.albumArtUri ?: ("name:" + it.first.album.lowercase()) },
            title = { it.first().album.ifBlank { UNKNOWN_ALBUM } },
            subtitle = { it.first().artist }
        )
    )
}
