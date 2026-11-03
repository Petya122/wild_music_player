package com.wildmusicplayer

import com.wildmusicplayer.data.OnlineTrack
import com.wildmusicplayer.data.PlaylistBackup
import com.wildmusicplayer.data.PlaylistRepository
import com.wildmusicplayer.data.RestoreSummary
import com.wildmusicplayer.model.Song
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaylistBackupTest {

    private fun song(id: Long, title: String, artist: String, durationMs: Long, path: String?) =
        Song(id, title, artist, "Album", durationMs, "content://media/external/audio/media/$id", null, filePath = path)

    private val oldPhone = listOf(
        song(1, "Move On Baby", "Cappella", 319_000, "/storage/emulated/0/Music/move.mp3"),
        song(2, "Bohemian Rhapsody", "Queen", 354_000, "/storage/emulated/0/Music/queen.mp3"),
        song(3, "Lost Song", "Nobody", 200_000, "/storage/emulated/0/Music/lost.mp3")
    )

    // After a reinstall every song has a new ID; one moved folder, one is gone
    private val newPhone = listOf(
        song(51, "Move On Baby", "Cappella", 319_000, "/storage/emulated/0/Music/move.mp3"),
        song(52, "Bohemian Rhapsody", "Queen", 354_500, "/storage/emulated/0/Download/queen.mp3")
    )

    @Test
    fun roundTripFindsSongsByPathThenNameAndAddsToExistingPlaylists() {
        val old = PlaylistRepository(FakeKeyValueStore())
        val track = OnlineTrack("yt1", "Online Song", "Someone", 180, null, "https://youtube.com/watch?v=yt1")
        old.createPlaylist("Car", listOf(1, 2, 3), listOf(track))
        old.toggleFavorite(2)
        old.addRecentlyPlayed(1)
        val backup = PlaylistBackup.create(old, oldPhone, now = 1_000)

        val restored = PlaylistRepository(FakeKeyValueStore())
        restored.createPlaylist("car", listOf(51))
        val summary = PlaylistBackup.restore(backup, restored, newPhone)

        assertEquals(RestoreSummary(playlists = 1, songsFound = 2, songsTotal = 3, favorites = 1), summary)
        val playlist = restored.customPlaylists.value.single()
        assertEquals(listOf(51L, 52L), playlist.songIds)
        assertEquals(listOf(track), playlist.onlineTracks)
        assertEquals(setOf(52L), restored.favoriteSongIds.value)
        assertEquals(listOf(51L), restored.recentlyPlayedIds.value)
    }

    @Test
    fun otherFilesAreNotBackups() {
        val repo = PlaylistRepository(FakeKeyValueStore())
        assertNull(PlaylistBackup.restore("{\"hello\":1}", repo, newPhone))
        assertNull(PlaylistBackup.restore("not json", repo, newPhone))
    }

    @Test
    fun m3u8UsesFilePathsAndYouTubeAddresses() {
        val track = OnlineTrack("yt1", "Online Song", "Someone", 180, null, "https://youtube.com/watch?v=yt1")
        assertEquals(
            "#EXTM3U\n#PLAYLIST:Car\n" +
                "#EXTINF:319,Cappella - Move On Baby\n/storage/emulated/0/Music/move.mp3\n" +
                "#EXTINF:180,Someone - Online Song\nhttps://youtube.com/watch?v=yt1\n",
            PlaylistBackup.toM3u8("Car", newPhone.take(1), listOf(track))
        )
    }
}
