package com.wildmusicplayer

import com.wildmusicplayer.data.BrowseCatalog
import com.wildmusicplayer.data.BrowseKind
import com.wildmusicplayer.data.OnlineTrack
import com.wildmusicplayer.data.Playlist
import com.wildmusicplayer.model.Song
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrowseCatalogTest {

    private fun song(id: Long, title: String, artist: String, album: String, track: Int = 0) =
        Song(id, title, artist, album, 200_000, "content://media/$id", "content://media/external/audio/albumart/${album.length}", trackNumber = track)

    private val library = listOf(
        song(1, "Think About the Way", "Ice MC", "Ice'n'Green", 1),
        song(2, "It's a Rainy Day", "Ice MC", "Ice'n'Green", 2),
        song(3, "Promise Man", "Basic Element", "The Ultimate Collection", 1),
        song(4, "The Ride", "Basic Element", "The Ultimate Collection", 2),
        song(5, "What Is Love", "Haddaway", "The Album", 1)
    )
    private val carTest = Playlist(
        id = "car", name = "Car Test", songIds = listOf(5, 3),
        onlineTracks = listOf(OnlineTrack(id = "o", title = "Only Online", artist = "X", webUrl = "https://youtu.be/ooo"))
    )

    private fun catalog(offline: Boolean = true, continueTitle: String? = "What Is Love") = BrowseCatalog(
        library = library,
        favoriteIds = setOf(4),
        playCounts = mapOf(1L to 3, 5L to 9),
        playlists = listOf(carTest),
        recentIds = listOf(2, 1),
        offline = offline,
        continueTitle = continueTitle
    )

    private fun titles(parent: String) = catalog().children(parent)!!.map { it.title }

    @Test
    fun fourTabs() {
        assertEquals(listOf("Home", "Playlists", "Albums", "Artists"), titles(BrowseCatalog.ROOT))
        assertTrue(catalog().children(BrowseCatalog.ROOT)!!.all { it.isBrowsable })
    }

    @Test
    fun homeHasContinueShuffleRecentAndFavorites() {
        assertEquals(listOf("Continue", "Shuffle all", "Recently Played", "Favorites"), titles(BrowseCatalog.TAB_HOME))
        // Nothing to continue: no Continue row
        assertEquals("Shuffle all", catalog(continueTitle = null).children(BrowseCatalog.TAB_HOME)!!.first().title)
    }

    @Test
    fun playlistsTabHasTheAutomaticOnesAndYours() {
        assertEquals(listOf("Most Played", "Recently Added", "Car Test"), titles(BrowseCatalog.TAB_PLAYLISTS))
    }

    @Test
    fun aListStartsWithShuffleThenItsSongsInOrder() {
        val rows = catalog().children(BrowseCatalog.listId("most_played"))!!
        assertEquals(BrowseKind.SHUFFLE, rows.first().kind)
        assertEquals(listOf("What Is Love", "Think About the Way"), rows.drop(1).map { it.title })
        assertTrue(rows.drop(1).all { !it.isBrowsable && it.song != null })
    }

    @Test
    fun albumsAndArtists() {
        assertEquals(setOf("Ice'n'Green", "The Ultimate Collection", "The Album"), titles(BrowseCatalog.TAB_ALBUMS).toSet())
        assertEquals(setOf("Ice MC", "Basic Element", "Haddaway"), titles(BrowseCatalog.TAB_ARTISTS).toSet())
        val album = catalog().children(BrowseCatalog.TAB_ALBUMS)!!.first { it.title == "The Ultimate Collection" }
        // Album tracks in track order, after the Shuffle row
        assertEquals(listOf("Shuffle", "Promise Man", "The Ride"), catalog().children(album.id)!!.map { it.title })
    }

    @Test
    fun tappingASongPlaysItsWholeListFromThere() {
        val rows = catalog().children(BrowseCatalog.listId("playlist:car"))!!
        val promise = rows.first { it.title == "Promise Man" }
        val play = assertNotNull(catalog().play(promise.id))
        assertEquals(listOf(5L, 3L), play.songs.map { it.id })
        assertEquals(1, play.startIndex)
        // The same row found again by its ID, as the car remembers it
        assertEquals("Promise Man", catalog().item(promise.id)?.title)
    }

    @Test
    fun onlineSongsOfAMixedPlaylistOnlyWhenOnline() {
        assertEquals(2, catalog(offline = true).songsOf("playlist:car")!!.size)
        assertEquals(3, catalog(offline = false).songsOf("playlist:car")!!.size)
    }

    @Test
    fun shuffleRowsPlayEverySongShuffled() {
        val play = assertNotNull(catalog().play(BrowseCatalog.shuffleId("playlist:car")))
        assertTrue(play.shuffled)
        assertEquals(setOf(5L, 3L), play.songs.map { it.id }.toSet())
        val all = assertNotNull(catalog().play(catalog().children(BrowseCatalog.TAB_HOME)!!.first { it.title == "Shuffle all" }.id))
        assertEquals(5, all.songs.size)
    }

    @Test
    fun voiceFindsArtistsAlbumsPlaylistsAndSongs() {
        assertEquals(listOf(3L, 4L), catalog().playFromSearch("basic element")!!.songs.map { it.id })
        assertEquals(listOf(5L, 3L), catalog().playFromSearch("Car Test")!!.songs.map { it.id })
        assertEquals(listOf(1L, 2L), catalog().playFromSearch("Ice'n'Green")!!.songs.map { it.id })
        assertEquals(5L, catalog().playFromSearch("what is love")!!.songs.first().id)
        // "Play music on Wild Music": the player continues instead
        assertNull(catalog().playFromSearch(""))
        assertNull(catalog().playFromSearch("nothing like this"))
    }

    @Test
    fun searchRowsCanBeTapped() {
        val results = catalog().search("rainy")
        assertEquals(listOf("It's a Rainy Day"), results.map { it.title })
        assertEquals(2L, catalog().play(results.first().id)!!.songs.single().id)
    }

    @Test
    fun goneThingsAreNull() {
        assertNull(catalog().children(BrowseCatalog.listId("playlist:deleted")))
        assertNull(catalog().play("song|playlist:deleted|0|5"))
        assertNull(catalog().item("nonsense"))
    }
}
