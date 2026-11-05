package com.wildmusicplayer

import com.wildmusicplayer.data.normalizeForSearch
import kotlin.test.Test
import kotlin.test.assertEquals

// Checks the accent stripping used for search and song matching
class TextNormalizationTest {

    @Test
    fun accentsAreRemoved() {
        assertEquals("beyonce", normalizeForSearch("Beyoncé"))
        assertEquals("arvizturo tukorfurogep", normalizeForSearch("Árvíztűrő tükörfúrógép"))
    }

    @Test
    fun punctuationBecomesSpaces() {
        assertEquals("move on baby official", normalizeForSearch("Move On, Baby! (Official)"))
    }
}
