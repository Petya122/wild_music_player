package com.wildmusicplayer

import com.wildmusicplayer.data.PartialDownload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The note a paused download leaves next to its bytes: which stream, and how big the whole file is. */
class PartialDownloadTest {

    @Test
    fun survivesARoundTrip() {
        val note = PartialDownload("140", 71_310_276)
        assertEquals(note, PartialDownload.parse(note.format()))
    }

    @Test
    fun brokenOrMissingNotesMeanStartOver() {
        assertNull(PartialDownload.parse(null))
        assertNull(PartialDownload.parse(""))
        assertNull(PartialDownload.parse("140"))
        assertNull(PartialDownload.parse("140\nlots"))
        assertNull(PartialDownload.parse("140\n0"))
        assertNull(PartialDownload.parse("\n5000"))
    }
}
