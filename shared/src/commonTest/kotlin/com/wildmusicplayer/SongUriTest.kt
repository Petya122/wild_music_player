package com.wildmusicplayer

import com.wildmusicplayer.model.Song
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SongUriTest {

    @Test
    fun onlineUriMatchesAndroidUriBuilder() {
        // What android.net.Uri.Builder().appendQueryParameter("url", …) produced in v0.8.4
        assertEquals(
            "wmp-yt://stream?url=https%3A%2F%2Fmusic.youtube.com%2Fwatch%3Fv%3DdQw4w9WgXcQ",
            Song.onlineUri("https://music.youtube.com/watch?v=dQw4w9WgXcQ")
        )
    }

    @Test
    fun webUrlRoundTripsIncludingNonAscii() {
        val url = "https://www.youtube.com/watch?v=abc&list=Árvíz tűrő"
        assertEquals(url, Song.webUrlOf(Song.onlineUri(url)))
    }

    @Test
    fun onlineSongsAreRecognized() {
        val song = Song.fromOnline("Halo", "Beyoncé", "https://youtu.be/x", 200, null)
        assertTrue(song.isOnline)
        assertEquals(Song.ONLINE_SONG_ID, song.id)
        assertEquals(200_000L, song.duration)
        assertEquals("online:${song.contentUri}", song.mediaId)
    }

    @Test
    fun localSongsAreNotOnline() {
        val song = Song(42, "Halo", "Beyoncé", "I Am", 1000, "content://media/external/audio/media/42", null)
        assertFalse(song.isOnline)
        assertEquals("42", song.mediaId)
        assertNull(Song.webUrlOf(song.contentUri))
    }
}
