package com.wildmusicplayer

import com.wildmusicplayer.data.VARIOUS_ARTISTS
import com.wildmusicplayer.data.albumsOf
import com.wildmusicplayer.data.artistsOf
import com.wildmusicplayer.data.foldersOf
import com.wildmusicplayer.data.readableFolderPath
import com.wildmusicplayer.model.Song
import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryGroupsTest {

    private fun song(
        id: Long,
        title: String,
        artist: String = "Queen",
        album: String = "A Night at the Opera",
        albumId: Long? = 7,
        track: Int = 0,
        year: Int = 0,
        path: String? = "/storage/emulated/0/Music/$title.mp3"
    ) = Song(
        id, title, artist, album, 200_000, "content://media/external/audio/media/$id",
        albumId?.let { "content://media/external/audio/albumart/$it" },
        folderName = path?.substringBeforeLast('/')?.substringAfterLast('/') ?: "Music",
        filePath = path, trackNumber = track, year = year
    )

    @Test
    fun albumSongsPlayInTrackOrderWithUntaggedSongsLast() {
        val album = albumsOf(
            listOf(
                song(1, "Untagged"),
                song(2, "Love of My Life", track = 1009),
                song(3, "Death on Two Legs", track = 1001),
                song(4, "Bohemian Rhapsody", track = 1011, year = 1975)
            )
        ).single()
        assertEquals(listOf("Death on Two Legs", "Love of My Life", "Bohemian Rhapsody", "Untagged"), album.songs.map { it.title })
        assertEquals("Queen", album.subtitle)
        assertEquals(1975, album.year)
    }

    @Test
    fun albumsAreAToZWithUnknownAlbumLast() {
        val albums = albumsOf(
            listOf(
                song(1, "a", album = "Unknown Album", albumId = 1),
                song(2, "b", album = "zebra", albumId = 2),
                song(3, "c", album = "Abbey Road", albumId = 3)
            )
        )
        assertEquals(listOf("Abbey Road", "zebra", "Unknown Album"), albums.map { it.title })
    }

    @Test
    fun compilationWithoutAMainArtistIsVariousArtists() {
        val album = albumsOf(
            listOf(song(1, "a", artist = "A"), song(2, "b", artist = "B"), song(3, "c", artist = "C"))
        ).single()
        assertEquals(VARIOUS_ARTISTS, album.subtitle)
    }

    @Test
    fun songsWithoutAlbumCoverEntryAreGroupedByAlbumName() {
        val albums = albumsOf(
            listOf(song(1, "a", album = "Mix", albumId = null), song(2, "b", album = "mix", albumId = null))
        )
        assertEquals(1, albums.size)
        assertEquals(2, albums.single().songs.size)
    }

    @Test
    fun artistsIgnoreCaseAndCountTheirAlbums() {
        val artists = artistsOf(
            listOf(
                song(1, "a", artist = "Queen", album = "Jazz"),
                song(2, "b", artist = "queen", album = "Innuendo"),
                song(4, "d", artist = "Queen", album = "Jazz"),
                song(3, "c", artist = "ABBA", album = "Arrival")
            )
        )
        assertEquals(listOf("ABBA", "Queen"), artists.map { it.title })
        assertEquals(2, artists[1].albumCount)
        // By album, A to Z
        assertEquals(listOf("b", "a", "d"), artists[1].songs.map { it.title })
    }

    @Test
    fun foldersWithTheSameNameInDifferentPlacesStayApart() {
        val folders = foldersOf(
            listOf(
                song(1, "a", path = "/storage/emulated/0/Music/a.mp3"),
                song(2, "b", path = "/storage/emulated/0/Download/Music/b.mp3"),
                song(3, "c", path = "/storage/emulated/0/Music/c.mp3")
            )
        )
        assertEquals(listOf("Download/Music", "Music"), folders.map { it.subtitle })
        assertEquals(listOf("Music", "Music"), folders.map { it.title })
        assertEquals(2, folders[1].songs.size)
    }

    @Test
    fun onlineStreamsAreNotPartOfTheLibraryGroups() {
        val online = Song.fromOnline("Stream", "Someone", "https://youtube.com/watch?v=x", 100, null)
        assertEquals(1, albumsOf(listOf(song(1, "a"), online)).single().songs.size)
        assertEquals(1, artistsOf(listOf(song(1, "a"), online)).size)
        assertEquals(1, foldersOf(listOf(song(1, "a"), online)).size)
    }

    @Test
    fun folderPathsAreShownWithoutTheStorageRoot() {
        assertEquals("Music/Rock", readableFolderPath("/storage/emulated/0/Music/Rock"))
        assertEquals("SD card/Music", readableFolderPath("/storage/1A2B-3C4D/Music"))
        assertEquals("Phone storage", readableFolderPath("/storage/emulated/0"))
    }
}
