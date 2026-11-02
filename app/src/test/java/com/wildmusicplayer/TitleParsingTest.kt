package com.wildmusicplayer

import com.wildmusicplayer.data.YouTubeTrack
import com.wildmusicplayer.data.cleanChannelName
import com.wildmusicplayer.data.parseVideoTitle
import com.wildmusicplayer.data.stripTitleNoise
import com.wildmusicplayer.data.trackMetadataFor
import com.wildmusicplayer.data.upscaleGoogleThumbnail
import org.junit.Assert.assertEquals
import org.junit.Test

class TitleParsingTest {

    @Test
    fun artistDashTitleWithNoise() {
        assertEquals(
            "Queen" to "Bohemian Rhapsody",
            parseVideoTitle("Queen - Bohemian Rhapsody (Official Video Remastered)", "Queen Official")
        )
    }

    @Test
    fun enDashAndLyricsTag() {
        assertEquals("Artist" to "Song", parseVideoTitle("Artist – Song [Lyrics]", "Lyrics Channel"))
    }

    @Test
    fun noSeparatorUsesTopicChannelAsArtist() {
        assertEquals(
            "Artist A" to "Song (feat. B)",
            parseVideoTitle("Song (feat. B) [Official Music Video]", "Artist A - Topic")
        )
    }

    @Test
    fun quotedTitleIsUnquoted() {
        assertEquals("Band" to "Hit", parseVideoTitle("Band - \"Hit\" (Official Audio)", "BandVEVO"))
    }

    @Test
    fun meaningfulBracketsAreKept() {
        assertEquals("Song (Live at Wembley)", stripTitleNoise("Song (Live at Wembley) [4K]"))
        assertEquals("Song (Remix)", stripTitleNoise("Song (Remix)"))
    }

    @Test
    fun trailingNoiseWithoutBrackets() {
        assertEquals("Artist - Song", stripTitleNoise("Artist - Song - Official Video"))
    }

    @Test
    fun channelNameCleanup() {
        assertEquals("Queen", cleanChannelName("Queen - Topic"))
        assertEquals("Queen", cleanChannelName("QueenVEVO"))
        assertEquals("Queen", cleanChannelName("Queen Official"))
    }

    @Test
    fun musicSongKeepsTitleAndArtist() {
        val track = YouTubeTrack(
            id = "x", title = "Move On Baby", uploaderName = "Some Artist", durationSeconds = 200,
            thumbnailUrl = "https://lh3.googleusercontent.com/abc=w120-h120-l90-rj",
            webUrl = "https://music.youtube.com/watch?v=x", isMusicSong = true
        )
        val meta = trackMetadataFor(track)
        assertEquals("Move On Baby", meta.title)
        assertEquals("Some Artist", meta.artist)
        assertEquals("Move On Baby", meta.album)
        assertEquals("https://lh3.googleusercontent.com/abc=w1000-h1000-l90-rj", meta.coverUrl)
    }

    @Test
    fun nonGoogleThumbnailUnchanged() {
        val url = "https://i.ytimg.com/vi/abc/hq720.jpg"
        assertEquals(url, upscaleGoogleThumbnail(url))
    }
}
