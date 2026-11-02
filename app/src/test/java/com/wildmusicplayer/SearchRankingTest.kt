package com.wildmusicplayer

import com.wildmusicplayer.data.YouTubeTrack
import com.wildmusicplayer.data.normalizeForSearch
import com.wildmusicplayer.data.searchScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRankingTest {

    private fun track(
        title: String,
        uploader: String,
        views: Long = -1L,
        duration: Long = 210,
        verified: Boolean = false
    ) = YouTubeTrack(
        id = title,
        title = title,
        uploaderName = uploader,
        durationSeconds = duration,
        thumbnailUrl = null,
        webUrl = "https://www.youtube.com/watch?v=$title",
        viewCount = views,
        isUploaderVerified = verified
    )

    private fun best(query: String, vararg tracks: YouTubeTrack) =
        tracks.maxByOrNull { searchScore(it, query) }

    @Test
    fun exactSongBeatsHugeUnrelatedHit() {
        val song = track("Promise Man", "Some Artist - Topic")
        val megaHit = track("Unrelated Mega Hit (Official Video)", "Big Star", views = 2_000_000_000, verified = true)
        assertEquals(song, best("promise man", song, megaHit))
    }

    @Test
    fun spedUpVersionLosesToOriginal() {
        val original = track("Move On Baby", "Artist - Topic")
        val spedUp = track("Move On Baby (Sped Up)", "Edits Channel", views = 50_000_000)
        assertEquals(original, best("move on baby", original, spedUp))
    }

    @Test
    fun queryWordsCanBeSplitAcrossTitleAndArtist() {
        val right = track("Promise", "Man Band - Topic")
        val wrong = track("Promise", "Someone Else")
        assertEquals(right, best("man band promise", right, wrong))
    }

    @Test
    fun penaltySkippedWhenWordIsInQuery() {
        val original = track("Move On Baby", "Artist - Topic")
        val remix = track("Move On Baby (Remix)", "Artist - Topic")
        assertEquals(remix, best("move on baby remix", original, remix))
    }

    @Test
    fun liveDoesNotPenalizeAlive() {
        val alive = track("Alive", "Artist - Topic")
        val other = track("Alive", "Artist - Topic")
        assertEquals(searchScore(alive, "alive"), searchScore(other, "alive"), 0.0)
        val live = track("Alive (Live)", "Artist - Topic")
        assertTrue(searchScore(alive, "alive") > searchScore(live, "alive"))
    }

    @Test
    fun fullConcertLosesToSong() {
        val song = track("Promise", "Artist - Topic")
        val concert = track("Promise Live in Tokyo 2015 Full Concert", "Artist", duration = 5400)
        assertEquals(song, best("promise", song, concert))
    }

    @Test
    fun normalizationStripsPunctuation() {
        assertEquals("move on baby official", normalizeForSearch("Move On, Baby! (Official)"))
    }
}
