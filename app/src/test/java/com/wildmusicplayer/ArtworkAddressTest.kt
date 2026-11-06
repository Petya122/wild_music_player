package com.wildmusicplayer

import com.wildmusicplayer.service.albumIdFromPath
import com.wildmusicplayer.service.albumIdOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The car's cover addresses: only "art/<album number>" is ever opened. */
class ArtworkAddressTest {

    @Test
    fun phoneCoversBecomeAlbumNumbers() {
        assertEquals(3523865969751094743L, albumIdOf("content://media/external/audio/albumart/3523865969751094743"))
        assertEquals(42L, albumIdOf("content://media/external/audio/albumart/42"))
        assertNull(albumIdOf("content://media/external/audio/media/42"))
        assertNull(albumIdOf("content://media/external/audio/albumart/42/../../media/7"))
        assertNull(albumIdOf("https://i.ytimg.com/vi/abc/hqdefault.jpg"))
    }

    @Test
    fun onlyArtAndANumberAreAccepted() {
        assertEquals(42L, albumIdFromPath(listOf("art", "42")))
        assertNull(albumIdFromPath(listOf("art")))
        assertNull(albumIdFromPath(listOf("art", "42", "extra")))
        assertNull(albumIdFromPath(listOf("other", "42")))
        assertNull(albumIdFromPath(listOf("art", "-1")))
        assertNull(albumIdFromPath(listOf("art", "4 2")))
        assertNull(albumIdFromPath(listOf("art", "content://media/external/audio/media/1")))
        assertNull(albumIdFromPath(listOf("art", "99999999999999999999")))
    }
}
