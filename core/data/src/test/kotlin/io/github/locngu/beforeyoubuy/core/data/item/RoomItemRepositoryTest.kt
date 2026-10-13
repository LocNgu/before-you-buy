package io.github.locngu.beforeyoubuy.core.data.item

import app.cash.turbine.test
import io.github.locngu.beforeyoubuy.core.data.db.DatabaseTest
import io.github.locngu.beforeyoubuy.core.domain.item.Decision
import io.github.locngu.beforeyoubuy.core.domain.item.DecisionType
import io.github.locngu.beforeyoubuy.core.domain.item.HistoryEntry
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.item.Reflection
import io.github.locngu.beforeyoubuy.core.domain.item.UsageFrequency
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.DecisionResult
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.applyDecision
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class RoomItemRepositoryTest : DatabaseTest() {
    private lateinit var repository: RoomItemRepository

    @Before
    fun createRepository() {
        repository = RoomItemRepository(database)
    }

    @Test
    fun `add stores the item with a new id`() = runTest {
        val id = repository.add(FULL_ITEM)

        assertNotEquals(FULL_ITEM.id, id)
        assertEquals(FULL_ITEM.copy(id = id), repository.observeById(id).first())
    }

    @Test
    fun `observeById emits edits and null once the item is gone`() = runTest {
        val id = repository.add(newItem())

        repository.observeById(id).test {
            assertEquals("Camera", awaitItem()?.name)
            repository.update(newItem("Mirrorless camera").copy(id = id))
            assertEquals("Mirrorless camera", awaitItem()?.name)
            execSql("DELETE FROM items WHERE id = ${id.value}")
            assertNull(awaitItem())
        }
    }

    @Test
    fun `update saves edits`() = runTest {
        val id = repository.add(newItem())
        val edited = newItem().copy(id = id, price = Money(60_000, "EUR"), coolingEndsAt = DECIDED, notes = "Lens too")

        assertTrue(repository.update(edited))
        assertEquals(edited, repository.observeById(id).first())
    }

    @Test
    fun `update never changes the status`() = runTest {
        val id = repository.add(newItem())

        assertFalse(repository.update(newItem().copy(id = id, status = ItemStatus.PURCHASED)))
        assertEquals(ItemStatus.ACTIVE, repository.observeById(id).first()?.status)
    }

    @Test
    fun `update writes nothing once a decision ended the item meanwhile`() = runTest {
        val id = repository.add(newItem())
        val editedOnScreen = newItem("Edited").copy(id = id)
        repository.recordDecision(newItem().copy(id = id, status = ItemStatus.REJECTED), reject())

        assertFalse(repository.update(editedOnScreen))
        assertEquals("Camera", repository.observeById(id).first()?.name)
        assertEquals(ItemStatus.REJECTED, repository.observeById(id).first()?.status)
    }

    @Test
    fun `update writes nothing for a missing item or a status this version can't read`() = runTest {
        assertFalse(repository.update(newItem().copy(id = ItemId(999))))

        val id = repository.add(newItem())
        execSql("UPDATE items SET status = 'ARCHIVED' WHERE id = ${id.value}")
        val readBack = repository.observeById(id).first()!!

        assertEquals(ItemStatus.ACTIVE, readBack.status)
        assertFalse(repository.update(readBack.copy(name = "Edited")))
        assertEquals(listOf("ARCHIVED"), queryStrings("SELECT status FROM items WHERE id = ${id.value}"))
    }

    @Test
    fun `observeActive lists active items oldest first and follows changes`() = runTest {
        val newer = repository.add(newItem("Newer", ADDED.plusSeconds(60)))
        val older = repository.add(newItem("Older"))

        repository.observeActive().test {
            assertEquals(listOf(older, newer), awaitItem().map { it.id })
            repository.recordDecision(newItem().copy(id = newer, status = ItemStatus.REJECTED), reject())
            assertEquals(listOf(older), awaitItem().map { it.id })
        }
    }

    @Test
    fun `setReflection stores and replaces the answers`() = runTest {
        val id = repository.add(newItem())
        assertNull(repository.observeReflection(id).first())

        repository.setReflection(id, Reflection(why = "Hikes"))
        assertEquals(Reflection(why = "Hikes"), repository.observeReflection(id).first())

        repository.setReflection(id, FULL_REFLECTION)
        assertEquals(FULL_REFLECTION, repository.observeReflection(id).first())
    }

    @Test
    fun `recordDecision stores the applied item and appends the decision`() = runTest {
        val id = repository.add(newItem())
        val reflection = Reflection("Hikes", UsageFrequency.WEEKLY, listOf("Heavy"))
        repository.setReflection(id, reflection)
        val stored = repository.observeById(id).first()!!
        val decision = buy()
        val applied = applyDecision(stored, reflection, stored.price, decision) as DecisionResult.Applied

        assertTrue(repository.recordDecision(applied.item, decision))

        assertEquals(ItemStatus.PURCHASED, repository.observeById(id).first()?.status)
        assertEquals(listOf(decision), repository.observeDecisions(id).first())
    }

    @Test
    fun `wait keeps the item active with a re-check date`() = runTest {
        val id = repository.add(newItem())
        val decision = waitAWeek()

        assertTrue(repository.recordDecision(newItem().copy(id = id, recheckAt = decision.waitUntil), decision))

        val item = repository.observeById(id).first()!!
        assertEquals(ItemStatus.ACTIVE, item.status)
        assertEquals(decision.waitUntil, item.recheckAt)
        assertEquals(listOf(id), repository.observeActive().first().map { it.id })
    }

    @Test
    fun `recordDecision writes nothing for an item that already ended`() = runTest {
        val id = repository.add(newItem())
        assertTrue(repository.recordDecision(newItem().copy(id = id, status = ItemStatus.REJECTED), reject()))

        // e.g. "Buy" in the app confirmed just after "Let it go" in the notification.
        assertFalse(repository.recordDecision(newItem().copy(id = id, status = ItemStatus.PURCHASED), buy()))

        assertEquals(ItemStatus.REJECTED, repository.observeById(id).first()?.status)
        assertEquals(listOf(DecisionType.REJECT), repository.observeDecisions(id).first().map { it.type })
    }

    @Test
    fun `recordDecision writes nothing for a missing item`() = runTest {
        assertFalse(repository.recordDecision(newItem().copy(id = ItemId(999), status = ItemStatus.REJECTED), reject()))
        assertEquals(emptyList<String>(), queryStrings("SELECT id FROM decisions"))
    }

    @Test
    fun `recordDecision is atomic - a failed item update keeps the decision out too`() = runTest {
        val id = repository.add(newItem())
        execSql("CREATE TRIGGER fail_item_update BEFORE UPDATE ON items BEGIN SELECT RAISE(ABORT, 'boom'); END")

        val failure = runCatching {
            repository.recordDecision(newItem().copy(id = id, status = ItemStatus.REJECTED), reject())
        }.exceptionOrNull()

        assertNotNull(failure)
        assertEquals(emptyList<Decision>(), repository.observeDecisions(id).first())
        assertEquals(ItemStatus.ACTIVE, repository.observeById(id).first()?.status)
    }

    @Test
    fun `history lists ended items with their ending decision, most recently decided first`() = runTest {
        val camera = repository.add(newItem("Camera"))
        val bike = repository.add(newItem("Bike"))
        val lamp = repository.add(newItem("Lamp"))
        repository.add(newItem("Still active"))
        val earlier = buy(DECIDED.minusSeconds(3_600))
        val later = buy(DECIDED)
        repository.recordDecision(newItem().copy(id = camera, status = ItemStatus.PURCHASED), earlier)
        repository.recordDecision(newItem("Bike").copy(id = bike, status = ItemStatus.PURCHASED), later)
        repository.recordDecision(newItem("Lamp").copy(id = lamp, status = ItemStatus.REJECTED), reject())

        val bought = repository.observeHistory(ItemStatus.PURCHASED).first()

        assertEquals(listOf(bike to later, camera to earlier), bought.map { it.item.id to it.decision })
        assertEquals(
            listOf(HistoryEntry(newItem("Lamp").copy(id = lamp, status = ItemStatus.REJECTED), reject())),
            repository.observeHistory(ItemStatus.REJECTED).first()
        )
    }

    @Test
    fun `history skips an item whose ending decision this version can't read`() = runTest {
        val id = repository.add(newItem())
        repository.recordDecision(newItem().copy(id = id, status = ItemStatus.REJECTED), reject())
        execSql("UPDATE decisions SET type = 'GIFT'")

        assertEquals(emptyList<HistoryEntry>(), repository.observeHistory(ItemStatus.REJECTED).first())
        assertEquals(emptyList<Decision>(), repository.observeDecisions(id).first())
    }
}
