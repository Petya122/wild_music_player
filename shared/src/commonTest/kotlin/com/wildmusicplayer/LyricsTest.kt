package com.wildmusicplayer

import com.wildmusicplayer.lyrics.HttpResult
import com.wildmusicplayer.lyrics.LyricLine
import com.wildmusicplayer.lyrics.LyricsRepository
import com.wildmusicplayer.lyrics.LyricsResult
import com.wildmusicplayer.lyrics.currentLineIndex
import com.wildmusicplayer.lyrics.parseLrc
import com.wildmusicplayer.model.Song
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LyricsTest {

    @Test
    fun parsesTimesAndFractions() {
        val lines = parseLrc("[ar:Queen]\n[00:01.50]First\n[01:02.5]Second\n[02:00]Third\n[02:01.123]Fourth")
        assertEquals(
            listOf(
                LyricLine(1_500, "First"),
                LyricLine(62_500, "Second"),
                LyricLine(120_000, "Third"),
                LyricLine(121_123, "Fourth")
            ),
            lines
        )
    }

    @Test
    fun repeatedLinesAreSortedAndOffsetApplied() {
        val lines = parseLrc("[offset:+500]\n[00:10.00][00:30.00]Chorus\n[00:20.00]Verse\n[00:25.00]")
        assertEquals(
            listOf(LyricLine(9_500, "Chorus"), LyricLine(19_500, "Verse"), LyricLine(24_500, ""), LyricLine(29_500, "Chorus")),
            lines
        )
    }

    @Test
    fun currentLine() {
        val lines = parseLrc("[00:05.00]A\n[00:10.00]B\n[00:15.00]C")
        assertEquals(-1, currentLineIndex(lines, 4_999))
        assertEquals(0, currentLineIndex(lines, 5_000))
        assertEquals(1, currentLineIndex(lines, 14_999))
        assertEquals(2, currentLineIndex(lines, 600_000))
        assertEquals(-1, currentLineIndex(emptyList(), 1_000))
    }

    private val song = Song(1, "Move On Baby (Extended Mix)", "Cappella", "Move On Baby", 319_000, "content://1", null)

    private fun repository(storage: FakeFileStore, offline: Boolean = false, answer: (String) -> HttpResult?) =
        LyricsRepository(storage, { url -> answer(url) }, { offline }, { 1_000L })

    @Test
    fun extendedMixGetsPlainLyricsFromSearchAndIsSaved() = runTest {
        val storage = FakeFileStore()
        val urls = mutableListOf<String>()
        val repo = repository(storage) { url ->
            urls += url
            if ("/get?" in url) HttpResult(404, "") else HttpResult(
                200,
                """[{"duration":231.0,"instrumental":false,"plainLyrics":"Move on baby","syncedLyrics":"[00:12.00]Move on baby"}]"""
            )
        }

        // The radio edit is shorter, so its timings don't fit: plain text only
        assertEquals(LyricsResult.Plain("Move on baby"), repo.lyricsFor(song))
        assertTrue(urls.last().contains("track_name=Move%20On%20Baby&"))

        // Saved: no second lookup, and it works in Offline Mode
        val offlineRepo = repository(storage, offline = true) { error("no network calls expected") }
        assertEquals(LyricsResult.Plain("Move on baby"), offlineRepo.lyricsFor(song))
    }

    @Test
    fun exactMatchGivesSyncedLyrics() = runTest {
        val repo = repository(FakeFileStore()) {
            HttpResult(200, """{"duration":319,"plainLyrics":"Hey","syncedLyrics":"[00:01.00]Hey"}""")
        }
        assertEquals(LyricsResult.Synced(listOf(LyricLine(1_000, "Hey"))), repo.lyricsFor(song))
    }

    @Test
    fun offlineWithoutSavedLyricsAndNoConnection() = runTest {
        assertEquals(LyricsResult.Offline, repository(FakeFileStore(), offline = true) { null }.lyricsFor(song))
        assertEquals(LyricsResult.Failed, repository(FakeFileStore()) { null }.lyricsFor(song))
    }

    @Test
    fun busyServerIsAskedAgain() = runTest {
        var calls = 0
        val repo = repository(FakeFileStore()) {
            calls++
            if (calls == 1) HttpResult(503, "") else HttpResult(200, """{"duration":319,"plainLyrics":"Hey"}""")
        }
        assertEquals(LyricsResult.Plain("Hey"), repo.lyricsFor(song))
    }
}
