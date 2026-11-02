package com.wildmusicplayer

import com.wildmusicplayer.data.isLikelyRecording
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingFilterTest {

    @Test
    fun regularSongIsKept() {
        assertFalse(isLikelyRecording("Bohemian Rhapsody", "/storage/emulated/0/Music/Queen/Bohemian Rhapsody.mp3", "Queen"))
    }

    @Test
    fun timestampTitleIsRecording() {
        assertTrue(isLikelyRecording("20240131_142530", "/storage/emulated/0/Music/20240131_142530.m4a", "Music"))
    }

    @Test
    fun timestampFileNameIsRecording() {
        assertTrue(isLikelyRecording("Untitled", "/storage/emulated/0/Music/20240131_142530.m4a", "Music"))
    }

    @Test
    fun messengerFoldersAreRecordings() {
        assertTrue(isLikelyRecording("PTT-001", "/storage/emulated/0/WhatsApp/Media/WhatsApp Voice Notes/PTT-001.opus", "WhatsApp Voice Notes"))
        assertTrue(isLikelyRecording("Note", null, "Telegram Audio"))
    }

    @Test
    fun callRecordingPathIsRecording() {
        assertTrue(isLikelyRecording("Mom", "/storage/emulated/0/CallRec/Mom.amr", "CallRec"))
    }

    @Test
    fun missingPathWithNormalFolderIsKept() {
        assertFalse(isLikelyRecording("Song", null, "Music"))
    }
}
