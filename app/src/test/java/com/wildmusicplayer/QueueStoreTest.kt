package com.wildmusicplayer

import com.wildmusicplayer.service.QueueStore
import com.wildmusicplayer.service.QueuedSong
import com.wildmusicplayer.service.SavedQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class QueueStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val queue = SavedQueue(
        songs = listOf(
            QueuedSong(42, "Bohemian Rhapsody", "Queen", "A Night at the Opera", 354_000, "content://media/external/audio/media/42", "content://media/external/audio/albumart/7"),
            QueuedSong(-1, "Move On Baby", "Some Artist", "YouTube Stream", 200_000, "wmp-yt://stream?url=https%3A%2F%2Fmusic.youtube.com%2Fwatch%3Fv%3Dx", null)
        ),
        index = 1,
        positionMs = 61_500,
        shuffle = true,
        repeatMode = 2
    )

    private fun store() = QueueStore(File(tmp.root, "last_queue.json"))

    @Test
    fun saveAndLoadRoundTrip() {
        val store = store()
        store.save(queue)
        assertEquals(queue, store.load())
    }

    @Test
    fun missingFileLoadsNothing() {
        assertNull(store().load())
    }

    @Test
    fun corruptFileLoadsNothing() {
        File(tmp.root, "last_queue.json").writeText("{ not json")
        assertNull(store().load())
    }

    @Test
    fun emptyQueueLoadsNothing() {
        assertNull(QueueStore.fromJson(QueueStore.toJson(queue.copy(songs = emptyList(), index = 0))))
    }

    @Test
    fun outOfRangeIndexIsClamped() {
        val loaded = QueueStore.fromJson(QueueStore.toJson(queue.copy(index = 99, positionMs = -5)))!!
        assertEquals(1, loaded.index)
        assertEquals(0L, loaded.positionMs)
    }

    private fun local(id: Long) = QueuedSong(id, "Song $id", "A", "B", 1000, "content://media/external/audio/media/$id", null)
    private fun online(id: Long) = QueuedSong(-1, "Online $id", "A", "B", 1000, "wmp-yt://stream?url=$id", null)

    @Test
    fun offlineFilterKeepsPlaceOnLocalSong() {
        val q = SavedQueue(listOf(online(1), local(2), online(3), local(4)), index = 3, positionMs = 5000, shuffle = false, repeatMode = 0)
        val filtered = q.withoutOnlineSongs()!!
        assertEquals(listOf(2L, 4L), filtered.songs.map { it.id })
        assertEquals(1, filtered.index)
        assertEquals(5000L, filtered.positionMs)
    }

    @Test
    fun offlineFilterMovesFromOnlineSongToNextLocalSong() {
        val q = SavedQueue(listOf(local(1), online(2), local(3)), index = 1, positionMs = 5000, shuffle = false, repeatMode = 0)
        val filtered = q.withoutOnlineSongs()!!
        assertEquals(3L, filtered.songs[filtered.index].id)
        assertEquals(0L, filtered.positionMs)
    }

    @Test
    fun offlineFilterOnAllOnlineQueueIsEmpty() {
        val q = SavedQueue(listOf(online(1), online(2)), index = 0, positionMs = 0, shuffle = false, repeatMode = 0)
        assertNull(q.withoutOnlineSongs())
    }

    @Test
    fun clearRemovesSavedQueue() {
        val store = store()
        store.save(queue)
        store.clear()
        assertNull(store.load())
    }
}
