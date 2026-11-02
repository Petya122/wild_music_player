package com.wildmusicplayer

import com.wildmusicplayer.data.sanitizeFileName
import com.wildmusicplayer.data.uniqueFileName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNameTest {

    @Test
    fun illegalCharactersAreReplaced() {
        assertEquals("AC_DC - Back In Black _Live_", sanitizeFileName("AC/DC - Back In Black *Live*"))
        assertEquals("a_b_c_d_e_f_g_h_i", sanitizeFileName("a\\b:c*d?e\"f<g>h|i"))
    }

    @Test
    fun longTitlesAreCapped() {
        assertTrue(sanitizeFileName("x".repeat(500)).length <= 120)
    }

    @Test
    fun blankTitleGetsFallbackName() {
        assertEquals("track", sanitizeFileName("   "))
    }

    @Test
    fun freeNameIsUsedAsIs() {
        assertEquals("Song.m4a", uniqueFileName("Song", "m4a") { false })
    }

    @Test
    fun takenNamesGetNumberSuffix() {
        val taken = setOf("Song.m4a", "Song (2).m4a")
        assertEquals("Song (3).m4a", uniqueFileName("Song", "m4a") { it in taken })
    }
}
