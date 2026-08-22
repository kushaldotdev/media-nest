package com.example.medianest

import com.example.medianest.ui.screens.PlayerQueueItem
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerQueueReorderTest {

    private fun moveQueueItem(list: List<PlayerQueueItem>, fromIndex: Int, toIndex: Int): List<PlayerQueueItem> {
        if (fromIndex in list.indices && toIndex in list.indices && fromIndex != toIndex) {
            val updated = list.toMutableList()
            val item = updated.removeAt(fromIndex)
            updated.add(toIndex, item)
            return updated
        }
        return list
    }

    private fun getItemKey(item: PlayerQueueItem): String {
        return "${item.id}_${item.originalIndex ?: item.hashCode()}"
    }

    @Test
    fun testReorderMoveDown() {
        val initial = listOf(
            PlayerQueueItem(id = "vid1", title = "Video 1", originalIndex = 1),
            PlayerQueueItem(id = "vid2", title = "Video 2", originalIndex = 2),
            PlayerQueueItem(id = "vid3", title = "Video 3", originalIndex = 3)
        )

        val reordered = moveQueueItem(initial, 0, 1)

        assertEquals("vid2", reordered[0].id)
        assertEquals("vid1", reordered[1].id)
        assertEquals("vid3", reordered[2].id)
    }

    @Test
    fun testReorderMoveUp() {
        val initial = listOf(
            PlayerQueueItem(id = "vid1", title = "Video 1", originalIndex = 1),
            PlayerQueueItem(id = "vid2", title = "Video 2", originalIndex = 2),
            PlayerQueueItem(id = "vid3", title = "Video 3", originalIndex = 3)
        )

        val reordered = moveQueueItem(initial, 2, 0)

        assertEquals("vid3", reordered[0].id)
        assertEquals("vid1", reordered[1].id)
        assertEquals("vid2", reordered[2].id)
    }

    @Test
    fun testStableKeysAcrossReorder() {
        val itemA = PlayerQueueItem(id = "vid1", title = "Video 1", originalIndex = 1)
        val itemB = PlayerQueueItem(id = "vid2", title = "Video 2", originalIndex = 2)

        val initial = listOf(itemA, itemB)
        val keyABefore = getItemKey(initial[0])
        val keyBBefore = getItemKey(initial[1])

        val reordered = moveQueueItem(initial, 0, 1)
        val keyAAfter = getItemKey(reordered[1])
        val keyBAfter = getItemKey(reordered[0])

        assertEquals(keyABefore, keyAAfter)
        assertEquals(keyBBefore, keyBAfter)
        assertEquals("vid1_1", keyAAfter)
        assertEquals("vid2_2", keyBAfter)
    }

    @Test
    fun testInvalidIndicesNoOp() {
        val initial = listOf(
            PlayerQueueItem(id = "vid1", title = "Video 1", originalIndex = 1)
        )

        val sameFromTo = moveQueueItem(initial, 0, 0)
        assertEquals(initial, sameFromTo)

        val outOfBounds = moveQueueItem(initial, -1, 5)
        assertEquals(initial, outOfBounds)
    }
}
