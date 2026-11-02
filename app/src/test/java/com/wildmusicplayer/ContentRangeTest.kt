package com.wildmusicplayer

import com.wildmusicplayer.data.parseContentRangeTotal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContentRangeTest {

    @Test
    fun totalSizeFromHeader() {
        assertEquals(3547712L, parseContentRangeTotal("bytes 0-1048575/3547712"))
        assertEquals(3547712L, parseContentRangeTotal("bytes 3145728-3547711/3547712"))
    }

    @Test
    fun unknownOrMissingTotal() {
        assertNull(parseContentRangeTotal("bytes 0-1048575/*"))
        assertNull(parseContentRangeTotal(null))
        assertNull(parseContentRangeTotal("garbage"))
    }
}
