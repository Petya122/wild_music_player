package com.wildmusicplayer

import com.wildmusicplayer.data.LyricsFolder
import com.wildmusicplayer.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsFolderTest {

    @Test
    fun lrcNamesFollowTheSongFileThenArtistAndTitle() {
        val song = Song(
            1, "Think About the Way (Radio Edit)", "Ice MC", "Ice'n'Green", 261_000, "content://1", null,
            filePath = "/storage/emulated/0/Music/Ice MC - Think About The Way.mp3"
        )
        assertEquals(
            listOf(
                "ice mc - think about the way",
                "ice mc - think about the way (radio edit)",
                "think about the way (radio edit)",
                "think about the way"
            ),
            LyricsFolder.namesFor(song)
        )
    }

    @Test
    fun unknownArtistIsNotPartOfTheName() {
        val song = Song(2, "Loud Test Tone", "Unknown Artist", "TestTones", 40_000, "content://2", null)
        assertEquals(listOf("loud test tone"), LyricsFolder.namesFor(song))
    }
}
