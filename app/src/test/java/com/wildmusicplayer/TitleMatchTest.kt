package com.wildmusicplayer

import com.wildmusicplayer.data.isDownloadedCopyOf
import com.wildmusicplayer.data.titlesMatch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
        assertTrue(isDownloadedCopyOf("Halo", "Beyoncé - Halo (Official Video)", "BeyonceVEVO"))
        assertFalse(isDownloadedCopyOf("Halo", "Beyoncé - Single Ladies (Official Video)", "BeyonceVEVO"))
    }

    @Test
    fun blankTitlesNeverMatch() {
        assertFalse(titlesMatch("", ""))
        assertFalse(titlesMatch("   ", "Something"))
    }
}
