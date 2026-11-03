package com.wildmusicplayer.service

import androidx.media3.common.C
import androidx.media3.exoplayer.source.ShuffleOrder
import kotlin.random.Random

/**
 * Shuffle order in which songs added while shuffling play where you'd expect: "Play next" (inserted right
 * after the current song) comes next and "Add to queue" (appended) comes last. Media3's default shuffle
 * order would put them at a random place. A whole new queue is shuffled randomly as usual.
 *
 * [shuffled] lists the player's item indices in play order.
 */
class PlayNextShuffleOrder private constructor(
    private val shuffled: IntArray,
    private val random: Random
) : ShuffleOrder {

    constructor(random: Random = Random.Default) : this(IntArray(0), random)

    // positions[itemIndex] = where that item is in the play order
    private val positions = IntArray(shuffled.size).also { p ->
        shuffled.forEachIndexed { position, item -> p[item] = position }
    }

    override fun getLength(): Int = shuffled.size

    override fun getNextIndex(index: Int): Int =
        shuffled.getOrNull(positions[index] + 1) ?: C.INDEX_UNSET

    override fun getPreviousIndex(index: Int): Int =
        shuffled.getOrNull(positions[index] - 1) ?: C.INDEX_UNSET

    override fun getLastIndex(): Int = shuffled.lastOrNull() ?: C.INDEX_UNSET

    override fun getFirstIndex(): Int = shuffled.firstOrNull() ?: C.INDEX_UNSET

    override fun cloneAndInsert(insertionIndex: Int, insertionCount: Int): ShuffleOrder {
        val inserted = List(insertionCount) { insertionIndex + it }
        // Items at or after the insertion point move up in the player's list
        val existing = shuffled.map { if (it >= insertionIndex) it + insertionCount else it }
        val order = when {
            shuffled.isEmpty() -> inserted.shuffled(random) // a new queue
            insertionIndex >= shuffled.size -> existing + inserted // "Add to queue": plays last
            insertionIndex == 0 -> inserted + existing
            else -> {
                // "Play next": right after the item it was inserted behind (the current song)
                val after = positions[insertionIndex - 1] + 1
                existing.take(after) + inserted + existing.drop(after)
            }
        }
        return PlayNextShuffleOrder(order.toIntArray(), random)
    }

    override fun cloneAndRemove(indexFrom: Int, indexToExclusive: Int): ShuffleOrder {
        val removed = indexToExclusive - indexFrom
        val order = shuffled
            .filter { it < indexFrom || it >= indexToExclusive }
            .map { if (it >= indexToExclusive) it - removed else it }
        return PlayNextShuffleOrder(order.toIntArray(), random)
    }

    override fun cloneAndClear(): ShuffleOrder = PlayNextShuffleOrder(random)

    /** The play order as player item indices (for tests). */
    internal fun playOrder(): List<Int> = shuffled.toList()
}
