package io.github.locngu.beforeyoubuy.core.domain.priorities

import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadToHeadTest {
    private val camera = item().copy(id = ItemId(1), name = "Camera", prioritySlot = 1)
    private val laptop = item().copy(id = ItemId(2), name = "Laptop", prioritySlot = 2)
    private val bike = item().copy(id = ItemId(3), name = "Bike", prioritySlot = 3)
    private val printer = item().copy(id = ItemId(4), name = "3D printer")
    private val priorities = listOf(camera, laptop, bike)

    private fun start() = HeadToHead.start(printer, priorities, slots = 3)

    /** Plays [wins] (true = the challenger wins) and returns the finished head-to-head. */
    private fun play(vararg wins: Boolean): HeadToHead = wins.fold(start()) { headToHead, challengerWins ->
        headToHead.choose(if (challengerWins) printer.id else headToHead.opponent!!.id)
    }

    private fun lines(headToHead: HeadToHead) =
        headToHead.outcome!!.lines.map { Triple(it.item.name, it.before, it.after) }

    @Test
    fun `comparisons start from the lowest-ranked priority and move up with each win`() {
        val first = start()
        assertEquals(bike, first.opponent)
        assertEquals(3, first.opponentRank)

        val second = first.choose(printer.id)
        assertEquals(laptop, second.opponent)
        assertEquals(2, second.opponentRank)
        assertEquals(2, second.comparisonsLeft)

        assertEquals(camera, second.choose(printer.id).opponent)
    }

    @Test
    fun `losing the first comparison changes nothing`() {
        val headToHead = play(false)

        assertTrue(headToHead.finished)
        assertNull(headToHead.opponent)
        val outcome = headToHead.outcome!!
        assertFalse(outcome.changed)
        assertEquals(listOf(camera.id, laptop.id, bike.id), outcome.after)
        assertEquals(
            listOf(
                Triple("Camera", 1, 1),
                Triple("Laptop", 2, 2),
                Triple("Bike", 3, 3),
                Triple("3D printer", null, null)
            ),
            lines(headToHead)
        )
    }

    @Test
    fun `winning every comparison makes the challenger #1 and moves the lowest back to the wishlist`() {
        val headToHead = play(true, true, true)

        assertTrue(headToHead.finished)
        assertEquals(listOf(printer.id, camera.id, laptop.id), headToHead.outcome!!.after)
        assertEquals(
            listOf(
                Triple("3D printer", null, 1),
                Triple("Camera", 1, 2),
                Triple("Laptop", 2, 3),
                Triple("Bike", 3, null)
            ),
            lines(headToHead)
        )
    }

    @Test
    fun `winning some takes the rank of the last one beaten`() {
        // "Camera stays #1, 3D printer is now #2, Laptop is now #3, Bike moved back to the wishlist"
        val headToHead = play(true, true, false)

        val outcome = headToHead.outcome!!
        assertTrue(outcome.changed)
        assertEquals(listOf(camera.id, printer.id, laptop.id), outcome.after)
        assertEquals(
            listOf(
                Triple("Camera", 1, 1),
                Triple("3D printer", null, 2),
                Triple("Laptop", 2, 3),
                Triple("Bike", 3, null)
            ),
            lines(headToHead)
        )
    }

    @Test
    fun `beating only the lowest swaps it out`() {
        assertEquals(listOf(camera.id, laptop.id, printer.id), play(true, false).outcome!!.after)
    }

    @Test
    fun `one slot is a single comparison`() {
        val headToHead = HeadToHead.start(printer, listOf(camera), slots = 1)

        assertEquals(1, headToHead.comparisonsLeft)
        assertEquals(listOf(printer.id), headToHead.choose(printer.id).outcome!!.after)
        assertEquals(listOf(camera.id), headToHead.choose(camera.id).outcome!!.after)
    }

    @Test
    fun `the same choices always give the same outcome`() {
        assertEquals(play(true, false).outcome, play(true, false).outcome)
    }

    @Test
    fun `only the two items on screen can be chosen, and only until it's over`() {
        val headToHead = start()

        assertThrows(IllegalArgumentException::class.java) { headToHead.choose(camera.id) }
        assertThrows(IllegalStateException::class.java) { headToHead.choose(bike.id).choose(printer.id) }
    }

    @Test
    fun `a head-to-head needs priorities and a challenger that isn't one of them`() {
        assertThrows(IllegalArgumentException::class.java) { HeadToHead.start(printer, emptyList(), slots = 3) }
        assertThrows(IllegalArgumentException::class.java) { HeadToHead.start(bike, priorities, slots = 3) }
    }

    @Test
    fun `a full set of slots is never taken without a comparison`() {
        // Every path to a new ranking that includes the challenger goes through at least one win.
        val unplayed = start()
        assertNull(unplayed.outcome)
        val all = listOf(play(false), play(true, false), play(true, true, false), play(true, true, true))
        all.forEach { headToHead ->
            val inRanking = printer.id in headToHead.outcome!!.after
            assertEquals(headToHead.wins > 0, inRanking)
            assertEquals(3, headToHead.outcome!!.after.size)
        }
    }
}
