package com.wildmusicplayer

import com.wildmusicplayer.data.OnlineTrack
import com.wildmusicplayer.data.PlaylistRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaylistRepositoryTest {

    @Test
    fun readsDataSavedByOlderVersions() {
        // What org.json wrote up to v0.8.4: escaped slashes, "" for a missing thumbnail
        val prefs = FakeKeyValueStore().apply {
            putStringSet("key_favorite_songs", setOf("7", "42"))
            putString("key_recently_played_songs", "[42,7,3]")
            putString(
                "key_custom_playlists",
                """[{"id":"p1","name":"Car","createdAt":1700000000000,"songIds":[42,7],"onlineTracks":[""" +
                    """{"id":"t1","title":"Dragostea Din Tei","artist":"O-Zone","durationSeconds":214,""" +
                    """"thumbnailUrl":"","webUrl":"https:\/\/www.youtube.com\/watch?v=YnopHCL1Jk8"}]}]"""
            )
        }
        val repo = PlaylistRepository(prefs)

        assertEquals(setOf(7L, 42L), repo.favoriteSongIds.value)
        assertEquals(listOf(42L, 7L, 3L), repo.recentlyPlayedIds.value)
        val playlist = repo.customPlaylists.value.single()
        assertEquals("Car", playlist.name)
        assertEquals(1700000000000L, playlist.createdAt)
        assertEquals(listOf(42L, 7L), playlist.songIds)
        val track = playlist.onlineTracks.single()
        assertEquals("https://www.youtube.com/watch?v=YnopHCL1Jk8", track.webUrl)
        assertNull(track.thumbnailUrl)
    }

    @Test
    fun savedPlaylistsLoadBackUnchanged() {
        val prefs = FakeKeyValueStore()
        val repo = PlaylistRepository(prefs)
        val track = OnlineTrack("t1", "Halo", "Beyoncé", 261, "https://i.ytimg.com/x.jpg", "https://youtu.be/bnVUHWCynig")
        val created = repo.createPlaylist("Mix", listOf(1L, 2L), listOf(track))
        repo.toggleFavorite(5L)
        repo.addRecentlyPlayed(9L)

        val reloaded = PlaylistRepository(prefs)
        assertEquals(listOf(created), reloaded.customPlaylists.value)
        assertEquals(setOf(5L), reloaded.favoriteSongIds.value)
        assertEquals(listOf(9L), reloaded.recentlyPlayedIds.value)
    }

    @Test
    fun downloadedTrackMovesToLocalSongs() {
        val repo = PlaylistRepository(FakeKeyValueStore())
        val track = OnlineTrack("t1", "Halo", "Beyoncé", webUrl = "https://youtu.be/bnVUHWCynig")
        val playlist = repo.createPlaylist("Mix", onlineTracks = listOf(track))

        val migrated = repo.autoMigrateDownloadedTracks(setOf(77L)) { 77L }

        assertEquals(1, migrated)
        val updated = repo.customPlaylists.value.single { it.id == playlist.id }
        assertEquals(listOf(77L), updated.songIds)
        assertTrue(updated.onlineTracks.isEmpty())
    }

    @Test
    fun renameReorderAndUndoRemoval() {
        val prefs = FakeKeyValueStore()
        val repo = PlaylistRepository(prefs)
        val a = OnlineTrack("a", "A", "X")
        val b = OnlineTrack("b", "B", "Y")
        val id = repo.createPlaylist("Car", listOf(1, 2, 3), listOf(a, b)).id

        repo.renamePlaylist(id, "  Road trip ")
        repo.renamePlaylist(id, "   ") // blank names are ignored
        // 9 isn't in the playlist; 2 isn't mentioned and stays, at the end
        repo.reorderPlaylist(id, listOf(3, 9, 1), listOf("b", "a"))

        val reordered = PlaylistRepository(prefs).customPlaylists.value.single()
        assertEquals("Road trip", reordered.name)
        assertEquals(listOf(3L, 1L, 2L), reordered.songIds)
        assertEquals(listOf(b, a), reordered.onlineTracks)

        repo.removeSongFromPlaylist(id, 1)
        repo.insertSongInPlaylist(id, 1, 1)
        repo.removeOnlineTrackFromPlaylist(id, "b")
        repo.insertOnlineTrackInPlaylist(id, b, 0)
        val restored = repo.customPlaylists.value.single()
        assertEquals(listOf(3L, 1L, 2L), restored.songIds)
        assertEquals(listOf(b, a), restored.onlineTracks)
    }
}
