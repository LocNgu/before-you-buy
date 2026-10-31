package io.github.locngu.beforeyoubuy.core.data.item

import io.github.locngu.beforeyoubuy.core.data.db.DatabaseTest
import io.github.locngu.beforeyoubuy.core.data.settings.RoomPolicyRepository
import io.github.locngu.beforeyoubuy.core.data.testing.assertThrowsIllegalArgument
import io.github.locngu.beforeyoubuy.core.domain.coolingoff.CoolingOffRules
import io.github.locngu.beforeyoubuy.core.domain.coolingoff.changeKind
import io.github.locngu.beforeyoubuy.core.domain.item.Decision
import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.DecisionResult
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.Funding
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.applyDecision
import io.github.locngu.beforeyoubuy.core.domain.priorities.prioritiesOf
import java.time.Duration
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Priorities (#24, ADR-0047): storing a ranking, keeping ranks without gaps, and the slot count. */
internal class RoomPrioritiesTest : DatabaseTest() {
    private lateinit var items: RoomItemRepository
    private lateinit var policies: RoomPolicyRepository

    // Promoted when added, so a priority's 30-day wait is over by DECIDED and a plain Buy works.
    private val at = ADDED
    private val utc = ZoneOffset.UTC

    @Before
    fun createRepositories() {
        items = RoomItemRepository(database)
        policies = RoomPolicyRepository(database)
    }

    private suspend fun add(name: String, kind: ItemKind = ItemKind.WANT): Item =
        items.observeById(items.add(newItem(name).copy(kind = kind))).first()!!

    /** The stored priorities' names, rank 1 first, and their stored slots. */
    private suspend fun ranking(): List<Pair<String, Int?>> =
        prioritiesOf(items.observeActive().first()).map { it.name to it.prioritySlot }

    private suspend fun stored(item: Item): Item = items.observeById(item.id).first()!!

    private suspend fun decide(item: Item, decision: Decision): Boolean {
        val applied = applyDecision(item, FULL_REFLECTION, Funding(item.price, required = false), decision)
        return items.recordDecision(item, decision, (applied as DecisionResult.Applied).item)
    }

    /** Camera #1, Laptop #2, Bike #3, and a Printer that isn't a priority. */
    private suspend fun threePriorities(): List<Item> {
        val all = listOf(add("Camera"), add("Laptop"), add("Bike"), add("Printer"))
        assertTrue(items.setPriorities(emptyList(), all.take(3).map { it.id }, at, utc))
        return all.map { stored(it) }
    }

    @Test
    fun `a ranking is stored as slots 1 to n`() = runTest {
        threePriorities()

        assertEquals(listOf("Camera" to 1, "Laptop" to 2, "Bike" to 3), ranking())
    }

    @Test
    fun `a head-to-head result moves the challenger in and the lowest back to the wishlist`() = runTest {
        val (camera, laptop, bike, printer) = threePriorities()

        val before = listOf(camera.id, laptop.id, bike.id)
        assertTrue(items.setPriorities(before, listOf(camera.id, printer.id, laptop.id), at, utc))

        assertEquals(listOf("Camera" to 1, "Printer" to 2, "Laptop" to 3), ranking())
        assertEquals(null, stored(bike).prioritySlot)
    }

    @Test
    fun `getting a slot starts the wait in the same write, losing it pauses the wait`() = runTest {
        val (camera, laptop, bike, printer) = threePriorities()
        assertEquals(at, stored(camera).waitingSince)
        val later = at.plus(Duration.ofDays(10))

        val before = listOf(camera.id, laptop.id, bike.id)
        assertTrue(items.setPriorities(before, listOf(camera.id, printer.id, laptop.id), later, utc))

        assertEquals(later, stored(printer).waitingSince)
        assertEquals(later.plus(Duration.ofDays(30)), stored(printer).coolingEndsAt)
        assertEquals(stored(camera).coolingEndsAt, camera.coolingEndsAt) // moved nowhere, still running
        val paused = stored(bike)
        assertEquals(null, paused.waitingSince)
        assertEquals(10, paused.waitedDays)
    }

    @Test
    fun `fewer slots pause the waits of the priorities that go back`() = runTest {
        val (camera, laptop, bike) = threePriorities()

        assertTrue(
            policies.changePrioritySlots(1, listOf(camera.id, laptop.id, bike.id), at.plus(Duration.ofDays(4)), utc)
        )

        assertEquals(listOf(4, 4), listOf(stored(laptop), stored(bike)).map { it.waitedDays })
        assertEquals(listOf(null, null), listOf(stored(laptop), stored(bike)).map { it.coolingEndsAt })
    }

    @Test
    fun `a ranking based on stale priorities writes nothing`() = runTest {
        val (camera, laptop, bike, printer) = threePriorities()
        assertTrue(decide(laptop, reject()))

        val before = listOf(camera.id, laptop.id, bike.id)
        assertFalse(items.setPriorities(before, listOf(printer.id, camera.id, bike.id), at, utc))

        assertEquals(listOf("Camera" to 1, "Bike" to 2), ranking())
    }

    @Test
    fun `a ranking with a need, an ended item, a duplicate or more than the slots writes nothing`() = runTest {
        val (camera, laptop, bike, printer) = threePriorities()
        val expected = listOf(camera.id, laptop.id, bike.id)
        val need = add("Fridge", ItemKind.NEED)
        val lamp = add("Lamp")
        assertTrue(decide(printer, buyEarly())) // not a priority: bought early

        assertFalse(items.setPriorities(expected, listOf(camera.id, need.id, laptop.id), at, utc))
        assertFalse(items.setPriorities(expected, listOf(camera.id, printer.id, laptop.id), at, utc))
        assertFalse(items.setPriorities(expected, listOf(camera.id, camera.id), at, utc))
        assertFalse(items.setPriorities(expected, listOf(camera.id, laptop.id, bike.id, lamp.id), at, utc))
        assertEquals(listOf("Camera" to 1, "Laptop" to 2, "Bike" to 3), ranking())
    }

    @Test
    fun `buying or rejecting a priority frees its slot and the ones below move up`() = runTest {
        val (camera, laptop, _, printer) = threePriorities()

        assertTrue(decide(camera, buy()))
        assertEquals(listOf("Laptop" to 1, "Bike" to 2), ranking())
        assertEquals(null, stored(camera).prioritySlot)

        assertTrue(decide(stored(laptop), reject()))
        assertEquals(listOf("Bike" to 1), ranking())
        assertEquals(null, stored(printer).prioritySlot)
    }

    @Test
    fun `waiting keeps a priority's rank`() = runTest {
        val (_, laptop) = threePriorities()

        assertTrue(decide(laptop, waitAWeek()))

        assertEquals(listOf("Camera" to 1, "Laptop" to 2, "Bike" to 3), ranking())
    }

    @Test
    fun `a priority marked as a need leaves the priorities and the ones below move up`() = runTest {
        val (camera) = threePriorities()
        val need = changeKind(camera, ItemKind.NEED, DECIDED, CoolingOffRules.defaults("EUR"), ZoneOffset.UTC)

        assertTrue(items.update(camera, need))

        assertEquals(listOf("Laptop" to 1, "Bike" to 2), ranking())
    }

    @Test
    fun `remove from priorities moves the ones below up`() = runTest {
        val (camera, laptop, bike) = threePriorities()

        assertTrue(items.setPriorities(listOf(camera.id, laptop.id, bike.id), listOf(camera.id, bike.id), at, utc))

        assertEquals(listOf("Camera" to 1, "Bike" to 2), ranking())
    }

    @Test
    fun `fewer slots send the lowest-ranked back in the same transaction, more slots change nothing`() = runTest {
        val (camera, laptop, bike) = threePriorities()
        val shown = listOf(camera.id, laptop.id, bike.id)

        assertTrue(policies.changePrioritySlots(5, shown, at, utc))
        assertEquals(5, policies.policy.first().prioritySlots)
        assertEquals(listOf("Camera" to 1, "Laptop" to 2, "Bike" to 3), ranking())

        assertTrue(policies.changePrioritySlots(1, shown, at, utc))
        assertEquals(1, policies.policy.first().prioritySlots)
        assertEquals(listOf("Camera" to 1), ranking())
    }

    @Test
    fun `fewer slots based on priorities that changed meanwhile write nothing`() = runTest {
        val (camera, laptop, bike) = threePriorities()
        assertTrue(decide(laptop, reject()))

        assertFalse(policies.changePrioritySlots(1, listOf(camera.id, laptop.id, bike.id), at, utc))

        assertEquals(3, policies.policy.first().prioritySlots)
        assertEquals(listOf("Camera" to 1, "Bike" to 2), ranking())
    }

    @Test
    fun `the slot count is 3 at first, refuses anything outside 1 to 5 and survives a currency change`() = runTest {
        assertEquals(3, policies.policy.first().prioritySlots)
        assertThrowsIllegalArgument { policies.changePrioritySlots(0, emptyList(), at, utc) }
        assertThrowsIllegalArgument { policies.changePrioritySlots(6, emptyList(), at, utc) }

        assertTrue(policies.changePrioritySlots(2, emptyList(), at, utc))
        policies.changeCurrency("USD")

        assertEquals(2, policies.policy.first().prioritySlots)
    }

    @Test
    fun `a stored slot count outside 1 to 5 reads as the nearest`() = runTest {
        execSql("UPDATE policy SET priority_slots = 9")

        assertEquals(5, policies.policy.first().prioritySlots)
    }

    @Test
    fun `more priorities than slots can't be stored`() = runTest {
        val (camera, laptop, bike) = threePriorities()
        assertTrue(policies.changePrioritySlots(2, listOf(camera.id, laptop.id, bike.id), at, utc))

        assertFalse(items.setPriorities(listOf(camera.id, laptop.id), listOf(camera.id, laptop.id, bike.id), at, utc))
    }
}
