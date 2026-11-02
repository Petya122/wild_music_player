package com.wildmusicplayer

import com.wildmusicplayer.data.UpdateChecker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionCompareTest {

    @Test
    fun patchBumpIsNewer() {
        assertTrue(UpdateChecker.isNewerVersion("0.5.2", "0.5.1"))
    }

    @Test
    fun numericNotLexicalComparison() {
        assertTrue(UpdateChecker.isNewerVersion("0.10.0", "0.9.9"))
        assertFalse(UpdateChecker.isNewerVersion("0.9.9", "0.10.0"))
    }

    @Test
    fun equalVersionsAreNotNewer() {
        assertFalse(UpdateChecker.isNewerVersion("0.5.1", "0.5.1"))
        assertFalse(UpdateChecker.isNewerVersion("0.5", "0.5.0"))
    }

    @Test
    fun olderVersionIsNotNewer() {
        assertFalse(UpdateChecker.isNewerVersion("0.4.6", "0.5.1"))
    }

    @Test
    fun extraComponentCountsAsNewer() {
        assertTrue(UpdateChecker.isNewerVersion("0.5.1.1", "0.5.1"))
    }
}
