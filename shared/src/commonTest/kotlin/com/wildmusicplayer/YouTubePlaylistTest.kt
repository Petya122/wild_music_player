package com.wildmusicplayer

import com.wildmusicplayer.data.OnlineTrack
import com.wildmusicplayer.data.PlaylistBackup
import com.wildmusicplayer.data.PlaylistRepository
import com.wildmusicplayer.data.playlistImportBlocker
import com.wildmusicplayer.data.updatedAgoText
import com.wildmusicplayer.data.youTubePlaylistId
import com.wildmusicplayer.data.youTubePlaylistUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class YouTubePlaylistTest {

    // A real-looking 11-character video ID per letter: "a" → "aaaaaaaaaaa"
    private fun v(key: String) = key.repeat(11).take(11)

    private fun track(key: String, title: String = "Song $key") =
        OnlineTrack("https://www.youtube.com/watch?v=${v(key)}", title, "Artist", 200, null, "https://www.youtube.com/watch?v=${v(key)}")

    @Test
    fun findsThePlaylistInEveryKindOfLink() {
        val id = "PLcOBp_f3oCvI1a2b3c4d5e6f7g8h9i0jk"
        assertEquals(id, youTubePlaylistId("https://music.youtube.com/playlist?list=$id"))
        assertEquals(id, youTubePlaylistId("https://www.youtube.com/playlist?list=$id&si=abc123"))
        assertEquals(id, youTubePlaylistId("https://youtube.com/watch?v=cvjIvVyuNno&list=$id&index=3"))
        assertEquals(id, youTubePlaylistId("https://youtu.be/cvjIvVyuNno?list=$id"))
        assertEquals(id, youTubePlaylistId("https://music.youtube.com/browse/VL$id"))
        assertEquals(id, youTubePlaylistId("  m.youtube.com/playlist?list=$id  "))
        assertNull(youTubePlaylistId("https://www.youtube.com/watch?v=cvjIvVyuNno"))
        assertNull(youTubePlaylistId("daft punk"))
        assertNull(youTubePlaylistId("https://example.com/page?list=$id"))
        assertEquals("https://www.youtube.com/playlist?list=$id", youTubePlaylistUrl(id))
    }

    @Test
    fun mixesAndPrivateListsCantBeImported() {
        assertNotNull(playlistImportBlocker("RDCLAK5uy_kmPRjHDECIcuVwnKsx2Ng7fyNgFKWNJFs"))
        assertNotNull(playlistImportBlocker("RDAMVMcvjIvVyuNno"))
        assertNotNull(playlistImportBlocker("LL"))
        assertNotNull(playlistImportBlocker("LM"))
        assertNotNull(playlistImportBlocker("WL"))
        assertNull(playlistImportBlocker("PLcOBp_f3oCvI1a2b3c4d5e6f7g8h9i0jk"))
        assertNull(playlistImportBlocker("OLAK5uy_lH3ESfz1KmeJVM5dLmNEXdg1i5HE4vQVc"))
    }

    @Test
    fun updateAddsOnlyNewSongsAndKeepsYourChanges() {
        val repo = PlaylistRepository(FakeKeyValueStore())
        val imported = repo.importYouTubePlaylist("Road Trip", "PL1", listOf(track("a"), track("b"), track("c"), track("a")), now = 1_000)
        assertEquals(listOf("a", "b", "c"), imported.onlineTracks.map { it.title.removePrefix("Song ") })
        assertEquals(setOf(v("a"), v("b"), v("c")), imported.source?.knownVideoIds)

        // You remove b and move c to the top; on YouTube d and e were added and a was removed
        repo.removeOnlineTrackFromPlaylist(imported.id, track("b").id)
        repo.reorderPlaylist(imported.id, emptyList(), listOf(track("c").id, track("a").id))
        val added = repo.applyYouTubeUpdate(imported.id, listOf(track("b"), track("c"), track("d"), track("e")), now = 2_000)

        assertEquals(2, added)
        val updated = repo.playlistFromYouTube("PL1")!!
        // b stays removed, a stays, your order is kept, the new songs go to the end
        assertEquals(listOf("c", "a", "d", "e"), updated.onlineTracks.map { it.title.removePrefix("Song ") })
        assertEquals(2_000L, updated.source?.updatedAt)

        // Nothing new: nothing changes but the time
        assertEquals(0, repo.applyYouTubeUpdate(imported.id, listOf(track("c"), track("d"), track("e")), now = 3_000))
        assertEquals(4, repo.playlistFromYouTube("PL1")!!.onlineTracks.size)
    }

    @Test
    fun aSongYouAlreadyAddedByHandIsntAddedTwice() {
        val repo = PlaylistRepository(FakeKeyValueStore())
        val playlist = repo.importYouTubePlaylist("Mix", "PL2", listOf(track("a")), now = 1)
        // Added from a search result, saved with the music.youtube.com address
        repo.toggleOnlineTrackInPlaylist(playlist.id, track("x").copy(id = "https://music.youtube.com/watch?v=${v("x")}", webUrl = "https://music.youtube.com/watch?v=${v("x")}"))
        assertEquals(1, repo.applyYouTubeUpdate(playlist.id, listOf(track("a"), track("x"), track("y")), now = 2))
        assertEquals(3, repo.playlistFromYouTube("PL2")!!.onlineTracks.size)
    }

    @Test
    fun theSourceSurvivesARestartAndABackup() {
        val store = FakeKeyValueStore()
        val repo = PlaylistRepository(store)
        repo.importYouTubePlaylist("Road Trip", "PL1", listOf(track("a"), track("b")), now = 1_000)

        // Saved and read back, as after an app restart
        val reopened = PlaylistRepository(store).playlistFromYouTube("PL1")
        assertEquals(setOf(v("a"), v("b")), reopened?.source?.knownVideoIds)
        assertEquals(1_000L, reopened?.source?.updatedAt)

        // A backup restored on another phone can still be updated, and removed songs stay removed
        val backup = PlaylistBackup.create(repo, emptyList(), now = 5_000)
        val otherPhone = PlaylistRepository(FakeKeyValueStore())
        PlaylistBackup.restore(backup, otherPhone, emptyList())
        val restored = otherPhone.playlistFromYouTube("PL1")
        assertNotNull(restored)
        assertEquals(setOf(v("a"), v("b")), restored.source?.knownVideoIds)
        otherPhone.removeOnlineTrackFromPlaylist(restored.id, track("a").id)
        assertEquals(1, otherPhone.applyYouTubeUpdate(restored.id, listOf(track("a"), track("b"), track("c")), now = 6_000))
    }

    @Test
    fun updatedAgo() {
        val hour = 60 * 60 * 1000L
        assertEquals("updated just now", updatedAgoText(10_000, 20_000))
        assertEquals("updated 5 min ago", updatedAgoText(0, 5 * 60_000L))
        assertEquals("updated 3 h ago", updatedAgoText(0, 3 * hour))
        assertEquals("updated yesterday", updatedAgoText(0, 30 * hour))
        assertEquals("updated 4 days ago", updatedAgoText(0, 4 * 24 * hour))
    }
}
