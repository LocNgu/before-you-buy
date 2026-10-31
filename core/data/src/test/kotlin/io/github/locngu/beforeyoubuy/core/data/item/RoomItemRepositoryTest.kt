package io.github.locngu.beforeyoubuy.core.data.item

import app.cash.turbine.test
import io.github.locngu.beforeyoubuy.core.data.db.DatabaseTest
import io.github.locngu.beforeyoubuy.core.data.testing.assertThrowsIllegalArgument
import io.github.locngu.beforeyoubuy.core.domain.item.Decision
import io.github.locngu.beforeyoubuy.core.domain.item.DecisionType
import io.github.locngu.beforeyoubuy.core.domain.item.HistoryEntry
import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.item.Reflection
import io.github.locngu.beforeyoubuy.core.domain.item.UsageFrequency
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.DecisionResult
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.Funding
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.applyDecision
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class RoomItemRepositoryTest : DatabaseTest() {
    private lateinit var repository: RoomItemRepository

    @Before
    fun createRepository() {
        repository = RoomItemRepository(database)
    }

    private suspend fun stored(id: ItemId): Item = repository.observeById(id).first()!!

    private suspend fun addAndRead(item: Item = newPriority()): Item = stored(repository.add(item))

    /** Records [decision] as the app would: checked by `applyDecision` against the stored state. */
    private suspend fun decide(current: Item, decision: Decision): Boolean {
        val applied = applyDecision(
            current,
            FULL_REFLECTION,
            Funding(current.price, required = true),
            decision
        ) as DecisionResult.Applied
        return repository.recordDecision(current, decision, applied.item)
    }

    @Test
    fun `add stores the item with a new id`() = runTest {
        val id = repository.add(FULL_ITEM)

        assertNotEquals(FULL_ITEM.id, id)
        assertEquals(FULL_ITEM.copy(id = id), stored(id))
    }

    @Test
    fun `imageRefs names the photo of every item, active or ended`() = runTest {
        val active = addAndRead(newPriority().copy(imageRef = "a.jpg"))
        val ended = addAndRead(newPriority().copy(imageRef = "b.jpg"))
        addAndRead(newPriority().copy(imageRef = null))
        decide(ended, reject())

        assertEquals(setOf("a.jpg", "b.jpg"), repository.imageRefs())
        assertEquals("a.jpg", active.imageRef)
    }

    @Test
    fun `add refuses an item that already ended`() = runTest {
        assertThrowsIllegalArgument { repository.add(newPriority().copy(status = ItemStatus.PURCHASED)) }
    }

    @Test
    fun `observeById emits edits and null once the item is gone`() = runTest {
        val item = addAndRead()

        repository.observeById(item.id).test {
            assertEquals("Camera", awaitItem()?.name)
            repository.update(item, item.copy(name = "Mirrorless camera"))
            assertEquals("Mirrorless camera", awaitItem()?.name)
            execSql("DELETE FROM items WHERE id = ${item.id.value}")
            assertNull(awaitItem())
        }
    }

    @Test
    fun `update saves edits`() = runTest {
        val item = addAndRead()
        val edited =
            item.copy(price = Money(60_000, "EUR"), coolingOffDays = 60, coolingEndsAt = DECIDED, notes = "Lens too")

        assertTrue(repository.update(item, edited))
        assertEquals(edited, stored(item.id))
    }

    @Test
    fun `update refuses to change the status or the id`() = runTest {
        val item = addAndRead()

        assertThrowsIllegalArgument { repository.update(item, item.copy(status = ItemStatus.PURCHASED)) }
        assertThrowsIllegalArgument { repository.update(item, item.copy(id = ItemId(999))) }
        assertEquals(item, stored(item.id))
    }

    @Test
    fun `an edit from before a decision doesn't undo it`() = runTest {
        val item = addAndRead()
        val decision = waitAWeek()
        assertTrue(decide(item, decision)) // e.g. "Wait" while the edit screen was open

        assertFalse(repository.update(item, item.copy(name = "Edited")))

        assertEquals(decision.waitUntil, stored(item.id).recheckAt)
        assertEquals("Camera", stored(item.id).name)
    }

    @Test
    fun `update writes nothing for a missing item or a status this version can't read`() = runTest {
        val missing = newPriority().copy(id = ItemId(999))
        assertFalse(repository.update(missing, missing.copy(name = "Edited")))

        val item = addAndRead()
        execSql("UPDATE items SET status = 'ARCHIVED' WHERE id = ${item.id.value}")
        val readBack = stored(item.id)

        assertEquals(ItemStatus.ACTIVE, readBack.status)
        assertFalse(repository.update(readBack, readBack.copy(name = "Edited")))
        assertEquals(listOf("ARCHIVED"), queryStrings("SELECT status FROM items WHERE id = ${item.id.value}"))
    }

    @Test
    fun `observeActive lists active items oldest first and follows changes`() = runTest {
        val newer = addAndRead(newPriority("Newer", ADDED.plusSeconds(60)))
        val older = addAndRead(newPriority("Older"))

        repository.observeActive().test {
            assertEquals(listOf(older, newer), awaitItem())
            decide(newer, reject())
            assertEquals(listOf(older), awaitItem())
        }
    }

    @Test
    fun `setReflection stores and replaces the answers`() = runTest {
        val id = repository.add(newPriority())
        assertNull(repository.observeReflection(id).first())

        repository.setReflection(id, Reflection(why = "Hikes"))
        assertEquals(Reflection(why = "Hikes"), repository.observeReflection(id).first())

        repository.setReflection(id, FULL_REFLECTION)
        assertEquals(FULL_REFLECTION, repository.observeReflection(id).first())
    }

    @Test
    fun `observeReflections holds the answers of every item that has some and follows changes`() = runTest {
        val withAnswers = repository.add(newPriority("Camera"))
        val without = repository.add(newPriority("Tent"))

        repository.observeReflections().test {
            assertEquals(emptyMap<ItemId, Reflection>(), awaitItem())

            repository.setReflection(withAnswers, Reflection(why = "Hikes"))
            assertEquals(mapOf(withAnswers to Reflection(why = "Hikes")), awaitItem())

            repository.setReflection(withAnswers, FULL_REFLECTION)
            assertEquals(mapOf(withAnswers to FULL_REFLECTION), awaitItem())
            assertFalse(without in repository.observeReflections().first())
        }
    }

    @Test
    fun `setReflection writes nothing for a missing item`() = runTest {
        assertFalse(repository.setReflection(ItemId(999), FULL_REFLECTION))
        assertEquals(emptyList<String>(), queryStrings("SELECT item_id FROM reflections"))
    }

    @Test
    fun `recordDecision stores the applied item and appends the decision`() = runTest {
        val item = addAndRead()
        val reflection = Reflection("Hikes", UsageFrequency.WEEKLY, listOf("Heavy"))
        repository.setReflection(item.id, reflection)
        val decision = buy()
        val applied = applyDecision(
            item,
            reflection,
            Funding(item.price, required = true),
            decision
        ) as DecisionResult.Applied

        assertTrue(repository.recordDecision(item, decision, applied.item))

        assertEquals(ItemStatus.PURCHASED, stored(item.id).status)
        assertEquals(listOf(decision), repository.observeDecisions(item.id).first())
    }

    @Test
    fun `wait keeps the item active with a re-check date`() = runTest {
        val item = addAndRead()
        val decision = waitAWeek()

        assertTrue(decide(item, decision))

        assertEquals(ItemStatus.ACTIVE, stored(item.id).status)
        assertEquals(decision.waitUntil, stored(item.id).recheckAt)
        assertEquals(listOf(item.id), repository.observeActive().first().map { it.id })
    }

    @Test
    fun `recordDecision writes nothing for an item that already ended`() = runTest {
        val item = addAndRead()
        assertTrue(decide(item, reject()))

        // e.g. "Buy" in the app confirmed just after "Let it go" in the notification.
        assertFalse(decide(item, buy()))

        assertEquals(ItemStatus.REJECTED, stored(item.id).status)
        assertEquals(listOf(DecisionType.REJECT), repository.observeDecisions(item.id).first().map { it.type })
    }

    @Test
    fun `of two decisions at the same moment, exactly one is recorded`() = runTest {
        // Repeated so that, without the check inside the transaction, both would very likely get through at least once.
        val items = List(20) { addAndRead() }

        val results = withContext(Dispatchers.IO) {
            items.map { item -> listOf(async { decide(item, reject()) }, async { decide(item, buy()) }) }
                .map { it.awaitAll() }
        }

        results.forEach { assertEquals(listOf(true, false), it.sorted().reversed()) }
        items.forEach { assertEquals(1, repository.observeDecisions(it.id).first().size) }
    }

    @Test
    fun `a decision checked against an older state writes nothing`() = runTest {
        val item = addAndRead()
        val repriced = item.copy(
            price = Money(150_000, "EUR"),
            coolingOffDays = 90,
            coolingEndsAt = DECIDED.plusSeconds(3_600)
        )
        assertTrue(repository.update(item, repriced))

        // Ready under the old cooling-off end, not under the extended one.
        assertFalse(decide(item, buy()))

        assertEquals(repriced, stored(item.id))
        assertEquals(emptyList<Decision>(), repository.observeDecisions(item.id).first())
    }

    @Test
    fun `recordDecision writes nothing for a missing item and refuses inconsistent arguments`() = runTest {
        val missing = newPriority().copy(id = ItemId(999))
        assertFalse(decide(missing, reject()))
        assertEquals(emptyList<String>(), queryStrings("SELECT id FROM decisions"))

        val item = addAndRead()
        val ended = item.copy(status = ItemStatus.REJECTED)
        assertThrowsIllegalArgument { repository.recordDecision(ended, reject(), ended) }
        assertThrowsIllegalArgument { repository.recordDecision(item, reject(), ended.copy(id = ItemId(999))) }
    }

    @Test
    fun `recordDecision is atomic - a failed item update keeps the decision out too`() = runTest {
        val item = addAndRead()
        execSql("CREATE TRIGGER fail_item_update BEFORE UPDATE ON items BEGIN SELECT RAISE(ABORT, 'boom'); END")

        val failure = runCatching { decide(item, reject()) }.exceptionOrNull()

        assertNotNull(failure)
        assertEquals(emptyList<Decision>(), repository.observeDecisions(item.id).first())
        assertEquals(item, stored(item.id))
    }

    @Test
    fun `history lists ended items with their ending decision, most recently decided first`() = runTest {
        val camera = addAndRead(newPriority("Camera"))
        val bike = addAndRead(newPriority("Bike"))
        val lamp = addAndRead(newPriority("Lamp"))
        repository.add(newPriority("Still active"))
        val earlier = buy(DECIDED.minusSeconds(3_600))
        val later = buy(DECIDED)
        decide(camera, earlier)
        decide(bike, later)
        decide(lamp, reject())

        val bought = repository.observeHistory(ItemStatus.PURCHASED).first()

        assertEquals(listOf(bike.id to later, camera.id to earlier), bought.map { it.item.id to it.decision })
        assertEquals(
            listOf(HistoryEntry(lamp.copy(status = ItemStatus.REJECTED), reject())),
            repository.observeHistory(ItemStatus.REJECTED).first()
        )
    }

    @Test
    fun `history is for ended items only`() {
        assertThrows(IllegalArgumentException::class.java) { repository.observeHistory(ItemStatus.ACTIVE) }
    }

    @Test
    fun `history skips an item whose ending decision this version can't read`() = runTest {
        val item = addAndRead()
        decide(item, reject())
        execSql("UPDATE decisions SET type = 'GIFT'")

        assertEquals(emptyList<HistoryEntry>(), repository.observeHistory(ItemStatus.REJECTED).first())
        assertEquals(emptyList<Decision>(), repository.observeDecisions(item.id).first())
    }

    @Test
    fun `history keeps the note of a reject`() = runTest {
        val item = addAndRead()
        val rejected = Decision(DecisionType.REJECT, DECIDED, note = "Too heavy for trips")
        decide(item, rejected)

        assertEquals(
            listOf(HistoryEntry(item.copy(status = ItemStatus.REJECTED), rejected)),
            repository.observeHistory(ItemStatus.REJECTED).first()
        )
    }

    @Test
    fun `delete removes an ended item with its answers and decisions, and nothing else`() = runTest {
        val item = addAndRead()
        val other = addAndRead(newPriority("Bike"))
        repository.setReflection(item.id, FULL_REFLECTION)
        repository.setReflection(other.id, FULL_REFLECTION)
        decide(item, reject())
        val ended = stored(item.id)

        assertTrue(repository.delete(ended))

        assertNull(repository.observeById(item.id).first())
        assertNull(repository.observeReflection(item.id).first())
        assertEquals(emptyList<Decision>(), repository.observeDecisions(item.id).first())
        assertEquals(emptyList<HistoryEntry>(), repository.observeHistory(ItemStatus.REJECTED).first())
        assertEquals(other, stored(other.id))
        assertEquals(FULL_REFLECTION, repository.observeReflection(other.id).first())
    }

    @Test
    fun `delete writes nothing if the item changed or is gone`() = runTest {
        val item = addAndRead()
        decide(item, reject())
        val ended = stored(item.id)
        execSql("UPDATE items SET name = 'Renamed' WHERE id = ${item.id.value}")

        assertFalse(repository.delete(ended))
        assertEquals("Renamed", stored(item.id).name)

        val renamed = stored(item.id)
        assertTrue(repository.delete(renamed))
        assertFalse(repository.delete(renamed))
    }

    @Test
    fun `delete refuses an active item`() = runTest {
        val item = addAndRead()

        assertThrowsIllegalArgument { repository.delete(item) }
        assertEquals(item, stored(item.id))
    }
}
