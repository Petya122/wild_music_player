package com.wildmusicplayer

import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import com.wildmusicplayer.data.DownloadPause
import com.wildmusicplayer.data.DownloadQueue
import com.wildmusicplayer.data.DownloadState
import com.wildmusicplayer.data.YouTubeTrack
import com.wildmusicplayer.service.nextRepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DownloadQueueTest {

    private fun track(id: String) = YouTubeTrack(id = id, title = "Song $id", uploaderName = "Artist", durationSeconds = 200, thumbnailUrl = null, webUrl = "https://youtu.be/$id")

    private fun ids() = DownloadQueue.items.value.map { it.track.id }

    @Before
    fun clear() = DownloadQueue.reset()

    @Test
    fun songsAreDownloadedInOrderOneAtATime() {
        DownloadQueue.add(track("a"))
        DownloadQueue.add(track("b"))
        assertEquals("a", DownloadQueue.takeNext()?.track?.id)
        // Busy with "a": nothing else starts
        assertNull(DownloadQueue.takeNext())
        DownloadQueue.finished("a", saved = true)
        assertEquals(listOf("b"), ids())
        assertTrue("a" in DownloadQueue.completed.value)
        assertEquals("b", DownloadQueue.takeNext()?.track?.id)
    }

    @Test
    fun theSameSongIsQueuedOnce() {
        assertTrue(DownloadQueue.add(track("a")))
        assertFalse(DownloadQueue.add(track("a")))
        assertEquals(1, DownloadQueue.items.value.size)
    }

    @Test
    fun goingOfflineStopsTheDownloadAndItWaitsAtTheFront() {
        DownloadQueue.add(track("a"))
        DownloadQueue.add(track("b"))
        DownloadQueue.takeNext()
        DownloadQueue.pause(DownloadPause.OFFLINE)
        assertTrue(DownloadQueue.shouldStop("a"))
        DownloadQueue.finished("a", saved = false, error = "Download stopped")
        // Not failed: back in line, first
        assertEquals(listOf("a", "b"), ids())
        assertEquals(DownloadState.WAITING, DownloadQueue.items.value.first().state)
        assertNull(DownloadQueue.takeNext())
        assertFalse(DownloadQueue.canStart)
        // Online again: it continues
        assertTrue(DownloadQueue.resume(DownloadPause.OFFLINE))
        assertEquals("a", DownloadQueue.takeNext()?.track?.id)
    }

    @Test
    fun bothPausesMustBeLifted() {
        DownloadQueue.add(track("a"))
        DownloadQueue.pause(DownloadPause.OFFLINE)
        DownloadQueue.pause(DownloadPause.APP_IN_BACKGROUND)
        assertFalse(DownloadQueue.resume(DownloadPause.APP_IN_BACKGROUND))
        assertTrue(DownloadQueue.resume(DownloadPause.OFFLINE))
    }

    @Test
    fun removingTheRunningSongStopsIt() {
        DownloadQueue.add(track("a"))
        DownloadQueue.takeNext()
        assertFalse(DownloadQueue.shouldStop("a"))
        DownloadQueue.remove("a")
        assertTrue(DownloadQueue.shouldStop("a"))
        DownloadQueue.finished("a", saved = false)
        assertTrue(DownloadQueue.items.value.isEmpty())
    }

    @Test
    fun aFailedSongStaysUntilRetried() {
        DownloadQueue.add(track("a"))
        DownloadQueue.add(track("b"))
        DownloadQueue.takeNext()
        DownloadQueue.finished("a", saved = false, error = "No audio streams")
        assertEquals(DownloadState.FAILED, DownloadQueue.items.value.first { it.track.id == "a" }.state)
        // The next one goes ahead
        assertEquals("b", DownloadQueue.takeNext()?.track?.id)
        DownloadQueue.finished("b", saved = true)
        DownloadQueue.retry("a")
        assertEquals("a", DownloadQueue.takeNext()?.track?.id)
    }

    @Test
    fun anInterruptedDownloadWaitsAgain() {
        DownloadQueue.add(track("a"))
        DownloadQueue.add(track("b"))
        DownloadQueue.takeNext()
        DownloadQueue.interrupted()
        assertEquals(listOf("a", "b"), ids())
        assertEquals("a", DownloadQueue.takeNext()?.track?.id)
    }

    @Test
    fun widgetRepeatButtonGoesOffAllOne() {
        assertEquals(REPEAT_MODE_ALL, nextRepeatMode(REPEAT_MODE_OFF))
        assertEquals(REPEAT_MODE_ONE, nextRepeatMode(REPEAT_MODE_ALL))
        assertEquals(REPEAT_MODE_OFF, nextRepeatMode(REPEAT_MODE_ONE))
    }
}
