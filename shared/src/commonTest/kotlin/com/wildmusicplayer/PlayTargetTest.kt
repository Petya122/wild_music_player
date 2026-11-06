package com.wildmusicplayer

import com.wildmusicplayer.data.PlayTarget
import com.wildmusicplayer.data.Playlist
import com.wildmusicplayer.data.OnlineTrack
import com.wildmusicplayer.data.playQueue
import com.wildmusicplayer.model.Song
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayTargetTest {

    private fun song(id: Long) = Song(id, "Song $id", "Artist", "Album", 180_000, "content://$id", null)

    private val library = (1L..5L).map(::song)
    private val playlists = listOf(Playlist(id = "p1", name = "Car", songIds = listOf(4, 99, 2)))

    private fun ids(target: PlayTarget, favorites: Set<Long> = emptySet(), counts: Map<Long, Int> = emptyMap()) =
        target.songs(library, favorites, counts, playlists).map { it.id }

    @Test
    fun eachTargetPicksItsSongs() {
        assertEquals(listOf(1L, 2, 3, 4, 5), ids(PlayTarget.ShuffleAll))
        assertEquals(listOf(2L, 5), ids(PlayTarget.Favorites, favorites = setOf(5, 2)))
        assertEquals(listOf(3L, 1), ids(PlayTarget.MostPlayed, counts = mapOf(1L to 2, 3L to 7, 4L to 0)))
        assertEquals(listOf(5L, 4, 3, 2, 1), ids(PlayTarget.RecentlyAdded))
        // Playlist order, songs no longer on the phone left out
        assertEquals(listOf(4L, 2), ids(PlayTarget.CustomPlaylist("p1")))
        assertEquals(emptyList(), ids(PlayTarget.CustomPlaylist("deleted")))
    }

    @Test
    fun keysSurviveARoundTrip() {
        listOf(PlayTarget.ShuffleAll, PlayTarget.Favorites, PlayTarget.MostPlayed, PlayTarget.RecentlyAdded, PlayTarget.CustomPlaylist("p1"))
            .forEach { assertEquals(it, PlayTarget.fromKey(it.key)) }
        assertNull(PlayTarget.fromKey("nonsense"))
        assertNull(PlayTarget.fromKey("playlist:"))
        assertNull(PlayTarget.fromKey(null))
    }

    @Test
    fun labels() {
        assertEquals("Car", PlayTarget.CustomPlaylist("p1").label(playlists))
        assertEquals("Playlist", PlayTarget.CustomPlaylist("gone").label(playlists))
        assertEquals("Most Played", PlayTarget.MostPlayed.label(playlists))
    }

    // Car Test: one song on the phone, one online track that was downloaded, one that wasn't
    private val mixed = Playlist(
        id = "mix", name = "Car Test", songIds = listOf(1),
        onlineTracks = listOf(
            OnlineTrack(id = "a", title = "Downloaded", artist = "X", webUrl = "https://youtu.be/aaa"),
            OnlineTrack(id = "b", title = "Only online", artist = "Y", webUrl = "https://youtu.be/bbb")
        )
    )
    private val downloaded = mapOf("https://youtu.be/aaa" to 5L)

    @Test
    fun mixedPlaylistPlaysLikeInTheApp() {
        // Online: phone song, the downloaded copy, then the stream
        val online = mixed.playQueue(library, { downloaded[it] }, offline = false)
        assertEquals(listOf(1L, 5L, Song.ONLINE_SONG_ID), online.map { it.id })
        assertEquals("Only online", online.last().title)
        assertTrue(online.last().isOnline)
        // Offline: the stream is left out, the downloaded copy stays
        assertEquals(listOf(1L, 5L), mixed.playQueue(library, { downloaded[it] }, offline = true).map { it.id })
        // The widget and the tile go through the same queue
        val widget = PlayTarget.CustomPlaylist("mix").songs(library, emptySet(), emptyMap(), listOf(mixed), { downloaded[it] }, offline = false)
        assertEquals(online.map { it.mediaId }, widget.map { it.mediaId })
    }
}

