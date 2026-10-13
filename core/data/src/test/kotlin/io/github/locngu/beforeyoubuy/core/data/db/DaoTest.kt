package io.github.locngu.beforeyoubuy.core.data.db

import io.github.locngu.beforeyoubuy.core.data.item.DECIDED
import io.github.locngu.beforeyoubuy.core.data.item.buy
import io.github.locngu.beforeyoubuy.core.data.item.newItem
import io.github.locngu.beforeyoubuy.core.data.item.toEntity
import io.github.locngu.beforeyoubuy.core.data.item.waitAWeek
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.Reflection
import io.github.locngu.beforeyoubuy.core.domain.item.UsageFrequency
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class DaoTest : DatabaseTest() {
    private val items get() = database.itemDao()
    private val reflections get() = database.reflectionDao()
    private val decisions get() = database.decisionDao()

    @Test
    fun `there is no stage column in any table`() {
        val tables = queryStrings("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'")
        val columns = tables.flatMap { table -> queryStrings("SELECT name FROM pragma_table_info('$table')") }

        assertTrue("items" in tables && "reflections" in tables && "decisions" in tables)
        assertTrue(columns.none { "stage" in it.lowercase() })
    }

    @Test
    fun `insert assigns increasing ids and getById reads the row back`() = runTest {
        val first = items.insert(newItem().toEntity())
        val second = items.insert(newItem().toEntity())

        assertTrue(second > first)
        assertEquals(newItem().copy(id = ItemId(first)).toEntity(), items.getById(first))
        assertNull(items.getById(999))
    }

    @Test
    fun `update changes nothing for a missing item`() = runTest {
        assertEquals(0, items.update(newItem().copy(id = ItemId(999)).toEntity()))
    }

    @Test
    fun `observeByStatus filters by status, oldest first`() = runTest {
        val later = items.insert(newItem("Later", DECIDED).toEntity())
        val earlier = items.insert(newItem("Earlier").toEntity())
        execSql("UPDATE items SET status = 'REJECTED' WHERE id = $later")

        assertEquals(listOf(earlier), items.observeByStatus("ACTIVE").first().map { it.id })
        assertEquals(listOf(later), items.observeByStatus("REJECTED").first().map { it.id })
    }

    @Test
    fun `history pairs each item with its last appended decision`() = runTest {
        val id = items.insert(newItem().toEntity())
        decisions.insert(waitAWeek(DECIDED).toEntity(ItemId(id)))
        // Dated back before the wait, but appended after it: this is the decision that ended the item.
        val ending = buy(DECIDED.minusSeconds(86_400)).toEntity(ItemId(id))
        decisions.insert(ending)
        execSql("UPDATE items SET status = 'PURCHASED' WHERE id = $id")

        val history = items.observeHistory("PURCHASED").first()

        assertEquals(1, history.size)
        assertEquals("BUY", history.single().decision.type)
        assertEquals(ending.decidedAt, history.single().decision.decidedAt)
    }

    @Test
    fun `reflection upsert replaces the answers`() = runTest {
        val id = ItemId(items.insert(newItem().toEntity()))
        reflections.upsert(Reflection(why = "First").toEntity(id))
        reflections.upsert(Reflection(why = "Second", usageFrequency = UsageFrequency.DAILY).toEntity(id))

        val stored = reflections.observe(id.value).first()

        assertEquals("Second", stored?.why)
        assertEquals("DAILY", stored?.usageFrequency)
    }

    @Test
    fun `decisions are listed in the order they were appended`() = runTest {
        val id = ItemId(items.insert(newItem().toEntity()))
        decisions.insert(waitAWeek(DECIDED).toEntity(id))
        decisions.insert(buy(DECIDED.minusSeconds(60)).toEntity(id))

        assertEquals(listOf("WAIT", "BUY"), decisions.observeForItem(id.value).first().map { it.type })
    }

    @Test
    fun `deleting an item deletes its reflection and decisions`() = runTest {
        val id = ItemId(items.insert(newItem().toEntity()))
        reflections.upsert(Reflection(why = "x").toEntity(id))
        decisions.insert(waitAWeek().toEntity(id))

        execSql("DELETE FROM items WHERE id = ${id.value}")

        assertNull(reflections.observe(id.value).first())
        assertEquals(emptyList<DecisionEntity>(), decisions.observeForItem(id.value).first())
    }
}
