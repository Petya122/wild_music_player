package com.wildmusicplayer

import com.wildmusicplayer.data.isNewerVersion
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class VersionCompareTest {

    @Test
    fun patchBumpIsNewer() {
        assertTrue(isNewerVersion("0.5.2", "0.5.1"))
    }

    @Test
    fun numericNotLexicalComparison() {
        assertTrue(isNewerVersion("0.10.0", "0.9.9"))
        assertFalse(isNewerVersion("0.9.9", "0.10.0"))
    }

    @Test
    fun equalVersionsAreNotNewer() {
        assertFalse(isNewerVersion("0.5.1", "0.5.1"))
        assertFalse(isNewerVersion("0.5", "0.5.0"))
    }

    @Test
    fun olderVersionIsNotNewer() {
        assertFalse(isNewerVersion("0.4.6", "0.5.1"))
    }

    @Test
    fun extraComponentCountsAsNewer() {
        assertTrue(isNewerVersion("0.5.1.1", "0.5.1"))
    }
}
