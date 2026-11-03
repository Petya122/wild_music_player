package com.wildmusicplayer

import com.wildmusicplayer.data.artistsMatch
import com.wildmusicplayer.data.isDownloadedCopyOf
import com.wildmusicplayer.data.titlesMatch
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class TitleMatchTest {

    @Test
    fun exactMatchIgnoresCaseAndSpaces() {
        assertTrue(titlesMatch("  Bohemian Rhapsody ", "bohemian rhapsody"))
    }

    @Test
    fun onlineTitleContainingLocalTitleMatches() {
        assertTrue(titlesMatch("Bohemian Rhapsody", "Queen - Bohemian Rhapsody (Official Video)"))
    }

    @Test
    fun shortTitlesOnlyMatchExactly() {
        assertTrue(titlesMatch("Halo", "halo"))
        assertFalse(titlesMatch("Halo", "Halo (Live at Wembley)"))
    }

    @Test
    fun differentSongsDoNotMatch() {
        assertFalse(titlesMatch("Yesterday", "Let It Be"))
    }

    @Test
    fun taggedDownloadMatchesItsVideoTitle() {
        assertTrue(isDownloadedCopyOf("Halo", "Beyoncé", "Beyoncé - Halo (Official Video)", "BeyonceVEVO"))
        assertFalse(isDownloadedCopyOf("Halo", "Beyoncé", "Beyoncé - Single Ladies (Official Video)", "BeyonceVEVO"))
    }

    @Test
    fun sameTitleByAnotherArtistIsNotTheDownload() {
        // Turbo's eurobeat remix must not stand in for O-Zone's original
        assertFalse(isDownloadedCopyOf("Dragostea Din Tei", "Turbo", "Dragostea Din Tei", "O-Zone"))
        assertFalse(isDownloadedCopyOf("Dragostea Din Tei", "Turbo", "O-Zone - Dragostea Din Tei (Official Video)", "O-Zone Official"))
        assertTrue(isDownloadedCopyOf("Dragostea Din Tei", "O-Zone", "Dragostea Din Tei", "O-Zone"))
    }

    @Test
    fun downloadWithoutArtistStillMatchesByTitle() {
        assertTrue(isDownloadedCopyOf("Six Days", "Unknown Artist", "Six Days", "Intermission"))
        assertTrue(isDownloadedCopyOf("Six Days", "<unknown>", "Six Days", "Intermission"))
    }

    @Test
    fun artistVariantsMatch() {
        assertTrue(artistsMatch("Queen", "Queen - Topic"))
        assertTrue(artistsMatch("Peat Jr & Fernando", "Peat Jr"))
        assertTrue(artistsMatch("Beyoncé", "BeyonceVEVO"))
        assertFalse(artistsMatch("Turbo", "O-Zone"))
    }

    @Test
    fun blankTitlesNeverMatch() {
        assertFalse(titlesMatch("", ""))
        assertFalse(titlesMatch("   ", "Something"))
    }
}
