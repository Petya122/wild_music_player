package com.wildmusicplayer

import com.wildmusicplayer.data.listeningStats
import com.wildmusicplayer.model.Song
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ListeningStatsTest {

    private fun song(id: Long, artist: String, album: String, minutes: Long = 3) =
        Song(id, "Song $id", artist, album, minutes * 60_000, "content://media/external/audio/media/$id", "content://media/external/audio/albumart/${album.hashCode()}")

    private val library = listOf(
        song(1, "Queen", "Jazz"),
        song(2, "Queen", "Innuendo", minutes = 6),
        song(3, "ABBA", "Arrival"),
        song(4, "ABBA", "Arrival")
    )

    @Test
    fun countsPlaysTimeAndSongsPlayed() {
        val stats = listeningStats(library, mapOf(1L to 2, 2L to 1, 3L to 4))
        assertEquals(7, stats.totalPlays)
        assertEquals((2 * 3 + 6 + 4 * 3) * 60_000L, stats.listenedMs)
        assertEquals(3, stats.songsPlayed)
        assertEquals(4, stats.librarySize)
    }

    @Test
    fun topListsAreMostPlayedFirst() {
        val stats = listeningStats(library, mapOf(1L to 2, 2L to 1, 3L to 4, 4L to 1))
        assertEquals(listOf("Song 3", "Song 1", "Song 2", "Song 4"), stats.topSongs.map { it.title })
        assertEquals(listOf("ABBA" to 5, "Queen" to 3), stats.topArtists.map { it.title to it.plays })
        assertEquals(listOf("Arrival", "Jazz", "Innuendo"), stats.topAlbums.map { it.title })
        // Tapping an artist plays their played songs, most played first
        assertEquals(listOf(3L, 4L), stats.topArtists.first().songs.map { it.id })
    }

    @Test
    fun songsNoLongerInTheLibraryAndUnplayedSongsAreLeftOut() {
        val stats = listeningStats(library, mapOf(99L to 10, 1L to 0))
        assertEquals(0, stats.totalPlays)
        assertTrue(stats.topSongs.isEmpty())
        assertTrue(stats.topArtists.isEmpty())
    }

    @Test
    fun topListsAreCutToTheirSize() {
        val many = (1L..30L).map { song(it, "Artist $it", "Album $it") }
        val stats = listeningStats(many, many.associate { it.id to it.id.toInt() }, topSize = 5)
        assertEquals(5, stats.topSongs.size)
        assertEquals("Song 30", stats.topSongs.first().title)
    }
}
