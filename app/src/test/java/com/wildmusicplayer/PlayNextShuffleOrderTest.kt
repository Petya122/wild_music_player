package com.wildmusicplayer

import androidx.media3.common.C
import com.wildmusicplayer.service.PlayNextShuffleOrder
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class PlayNextShuffleOrderTest {

    private fun newQueue(size: Int) =
        PlayNextShuffleOrder(Random(42)).cloneAndInsert(0, size) as PlayNextShuffleOrder

    @Test
    fun newQueueIsAPermutation() {
        val order = newQueue(10).playOrder()
        assertEquals((0 until 10).toList(), order.sorted())
    }

    @Test
    fun playNextGoesRightAfterTheCurrentSong() {
        val queue = newQueue(6)
        val current = queue.playOrder()[2]
        // Play next inserts into the player's list right after the current item
        val updated = queue.cloneAndInsert(current + 1, 1) as PlayNextShuffleOrder
        val order = updated.playOrder()
        val currentNow = order.indexOf(current) // the current item keeps its index (it's before the insert)
        assertEquals(current + 1, order[currentNow + 1])
        assertEquals((0 until 7).toList(), order.sorted())
    }

    @Test
    fun addToQueuePlaysLast() {
        val queue = newQueue(5)
        val order = (queue.cloneAndInsert(5, 2) as PlayNextShuffleOrder).playOrder()
        assertEquals(listOf(5, 6), order.takeLast(2))
    }

    @Test
    fun removingKeepsTheRestInOrder() {
        val queue = newQueue(5)
        val before = queue.playOrder()
        val after = (queue.cloneAndRemove(1, 2) as PlayNextShuffleOrder).playOrder()
        val expected = before.filter { it != 1 }.map { if (it > 1) it - 1 else it }
        assertEquals(expected, after)
    }

    @Test
    fun navigationFollowsThePlayOrder() {
        val queue = newQueue(4)
        val order = queue.playOrder()
        assertEquals(order.first(), queue.firstIndex)
        assertEquals(order.last(), queue.lastIndex)
        assertEquals(order[2], queue.getNextIndex(order[1]))
        assertEquals(order[0], queue.getPreviousIndex(order[1]))
        assertEquals(C.INDEX_UNSET, queue.getNextIndex(order.last()))
    }
}
