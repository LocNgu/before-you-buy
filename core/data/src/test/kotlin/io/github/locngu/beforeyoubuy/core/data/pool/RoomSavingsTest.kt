package io.github.locngu.beforeyoubuy.core.data.pool

import io.github.locngu.beforeyoubuy.core.data.db.DatabaseTest
import io.github.locngu.beforeyoubuy.core.data.item.RoomItemRepository
import io.github.locngu.beforeyoubuy.core.data.item.newItem
import io.github.locngu.beforeyoubuy.core.domain.item.Decision
import io.github.locngu.beforeyoubuy.core.domain.item.DecisionType
import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.DecisionResult
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.Funding
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.applyDecision
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.core.domain.pool.Allocation
import io.github.locngu.beforeyoubuy.core.domain.pool.AllocationResult
import io.github.locngu.beforeyoubuy.core.domain.pool.FunMoney
import io.github.locngu.beforeyoubuy.core.domain.pool.PoolEntry
import io.github.locngu.beforeyoubuy.core.domain.pool.funMoneyOf
import io.github.locngu.beforeyoubuy.core.domain.pool.savingsOf
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Savings per item (#23): allocations, and what decisions and edits do to them and to the fun money. */
internal class RoomSavingsTest : DatabaseTest() {
    private lateinit var pool: RoomPoolRepository
    private lateinit var items: RoomItemRepository

    private val zone: ZoneId = ZoneId.of("Europe/Berlin")
    private val now: Instant = Instant.parse("2026-10-23T10:00:00Z")

    @Before
    fun createRepositories() {
        pool = RoomPoolRepository(database)
        items = RoomItemRepository(database)
    }

    private fun eur(minor: Long) = Money(minor, "EUR")

    /** Fun money set up today with [starting] in it and no monthly amount due yet (the 1st has passed). */
    private suspend fun setUp(starting: Long, wantsNeedFunding: Boolean = true) {
        assertTrue(pool.setUp(eur(30_000), 1, eur(starting), wantsNeedFunding, now, zone))
    }

    private suspend fun add(item: Item = newItem()): Item = items.observeById(items.add(item)).first()!!

    private suspend fun stored(item: Item): Item = items.observeById(item.id).first()!!

    /** What Home shows: the balance with what active wants hold. */
    private suspend fun funMoney(): FunMoney.SetUp {
        val active = items.observeActive().first()
        val savings = savingsOf(pool.allowance.first(), pool.allocations.first(), active)
        return funMoneyOf(pool.allowance.first(), pool.entries.first(), savings) as FunMoney.SetUp
    }

    private suspend fun allocatedFor(item: Item): Money {
        val active = items.observeActive().first()
        return savingsOf(pool.allowance.first(), pool.allocations.first(), active).allocatedFor(item)
    }

    private suspend fun decide(item: Item, decision: Decision): Boolean {
        val applied = applyDecision(item, null, Funding(item.price, required = false), decision)
        return items.recordDecision(item, decision, (applied as DecisionResult.Applied).item)
    }

    private fun buy(paid: Long) = Decision(DecisionType.BUY_EARLY, now, actualPrice = eur(paid), earlyReason = "Trip")

    @Test
    fun `setting aside moves money from not yet set aside to the item`() = runTest {
        setUp(starting = 30_000)
        val camera = add()

        assertTrue(pool.setAside(camera.id, eur(10_000), now) is AllocationResult.Allowed)

        assertEquals(listOf(Allocation(camera.id, eur(10_000), now)), pool.allocations.first())
        assertEquals(eur(10_000), funMoney().balance.committed)
        assertEquals(eur(20_000), funMoney().balance.unallocated)
        assertEquals(eur(30_000), funMoney().balance.pool)
    }

    @Test
    fun `setting aside never takes what isn't set aside below zero`() = runTest {
        setUp(starting = 10_000)
        val camera = add()
        val bike = add(newItem("Bike"))
        pool.setAside(camera.id, eur(6_000), now)

        assertEquals(AllocationResult.MoreThanNotSetAside, pool.setAside(bike.id, eur(4_001), now))
        assertTrue(pool.setAside(bike.id, eur(4_000), now) is AllocationResult.Allowed)
        assertEquals(eur(0), funMoney().balance.unallocated)
    }

    @Test
    fun `two at once can't both use the same money`() = runTest {
        setUp(starting = 10_000)
        val all = (1..4).map { add(newItem("Item $it")) }

        val results = withContext(Dispatchers.IO) {
            all.map { async { pool.setAside(it.id, eur(6_000), now) } }.awaitAll()
        }

        assertEquals(1, results.count { it is AllocationResult.Allowed })
        assertEquals(eur(4_000), funMoney().balance.unallocated)
    }

    @Test
    fun `taking back returns money, at most what the item holds`() = runTest {
        setUp(starting = 30_000)
        val camera = add()
        pool.setAside(camera.id, eur(10_000), now)

        assertEquals(AllocationResult.MoreThanSetAside, pool.takeBack(camera.id, eur(10_001), now))
        assertTrue(pool.takeBack(camera.id, eur(4_000), now) is AllocationResult.Allowed)

        assertEquals(eur(6_000), allocatedFor(camera))
        assertEquals(eur(24_000), funMoney().balance.unallocated)
    }

    @Test
    fun `nothing is set aside without fun money, for a need, or for a gone item`() = runTest {
        val camera = add()
        assertEquals(AllocationResult.NotSetUp, pool.setAside(camera.id, eur(1_000), now))

        setUp(starting = 30_000)
        val fridge = add(newItem("Fridge").copy(kind = ItemKind.NEED))
        assertEquals(AllocationResult.IsNeed, pool.setAside(fridge.id, eur(1_000), now))
        assertEquals(AllocationResult.NotActive, pool.setAside(ItemId(999), eur(1_000), now))
        assertEquals(emptyList<Allocation>(), pool.allocations.first())
    }

    @Test
    fun `an ended item gets nothing set aside`() = runTest {
        setUp(starting = 30_000)
        val camera = add()
        decide(camera, Decision(DecisionType.REJECT, now))

        assertEquals(AllocationResult.NotActive, pool.setAside(camera.id, eur(1_000), now))
    }

    @Test
    fun `rejecting releases what the item held`() = runTest {
        setUp(starting = 30_000)
        val camera = add()
        pool.setAside(camera.id, eur(12_000), now)

        assertTrue(decide(camera, Decision(DecisionType.REJECT, now)))

        assertEquals(eur(0), funMoney().balance.committed)
        assertEquals(eur(30_000), funMoney().balance.unallocated)
        assertEquals(emptyList<PoolEntry>(), pool.entries.first().filterIsInstance<PoolEntry.Purchase>())
    }

    @Test
    fun `buying below what was set aside spends the price and returns the rest`() = runTest {
        setUp(starting = 30_000)
        val camera = add()
        pool.setAside(camera.id, eur(12_000), now)

        assertTrue(decide(camera, buy(paid = 10_000)))

        assertEquals(PoolEntry.Purchase(camera.id, eur(10_000), now), pool.entries.first().last())
        assertEquals(eur(20_000), funMoney().balance.pool)
        assertEquals(eur(0), funMoney().balance.committed)
        assertEquals(eur(20_000), funMoney().balance.unallocated)
    }

    @Test
    fun `buying above what was set aside takes the difference, even below zero`() = runTest {
        setUp(starting = 15_000)
        val camera = add()
        pool.setAside(camera.id, eur(12_000), now)

        // 150 € paid, 120 € set aside, 30 € not set aside: exactly zero left.
        assertTrue(decide(camera, buy(paid = 15_000)))
        assertEquals(eur(0), funMoney().balance.unallocated)

        // The app asked first; the purchase itself is the only record.
        val bike = add(newItem("Bike"))
        assertTrue(decide(bike, buy(paid = 5_000)))
        assertEquals(eur(-5_000), funMoney().balance.unallocated)
        assertEquals(2, pool.entries.first().filterIsInstance<PoolEntry.Purchase>().size)
        assertEquals(emptyList<PoolEntry>(), pool.entries.first().filterIsInstance<PoolEntry.Adjustment>())
    }

    @Test
    fun `buying a need leaves the fun money unchanged`() = runTest {
        setUp(starting = 30_000)
        val fridge = add(newItem("Fridge").copy(kind = ItemKind.NEED))
        val before = funMoney().balance

        assertTrue(decide(fridge, Decision(DecisionType.BUY_NEED, now, actualPrice = eur(50_000))))

        assertEquals(before, funMoney().balance)
        assertEquals(listOf(PoolEntry.StartingBalance(eur(30_000), now)), pool.entries.first())
    }

    @Test
    fun `without fun money a purchase writes no entry`() = runTest {
        val camera = add()
        assertTrue(decide(camera, buy(paid = 12_000)))
        assertEquals(emptyList<PoolEntry>(), pool.entries.first())
    }

    @Test
    fun `marking a want with savings as a need releases them for good`() = runTest {
        setUp(starting = 30_000)
        val camera = add()
        pool.setAside(camera.id, eur(6_000), now)

        val need = camera.copy(kind = ItemKind.NEED, markedAsNeedAt = now)
        assertTrue(items.update(camera, need))

        assertEquals(eur(30_000), funMoney().balance.unallocated)
        assertEquals(Allocation(camera.id, eur(-6_000), now), pool.allocations.first().last())
        // Back to a want, the money doesn't come back.
        assertTrue(items.update(stored(camera), stored(camera).copy(kind = ItemKind.WANT, markedAsNeedAt = null)))
        assertEquals(eur(0), allocatedFor(stored(camera)))
        assertEquals(eur(30_000), funMoney().balance.unallocated)
    }

    @Test
    fun `an edit that keeps the kind leaves the savings alone`() = runTest {
        setUp(starting = 30_000)
        val camera = add()
        pool.setAside(camera.id, eur(6_000), now)

        assertTrue(items.update(camera, camera.copy(name = "Mirrorless camera")))

        assertEquals(1, pool.allocations.first().size)
        assertEquals(eur(6_000), allocatedFor(stored(camera)))
    }

    @Test
    fun `deleting an item from history removes its allocations but keeps its purchase`() = runTest {
        setUp(starting = 30_000)
        val camera = add()
        pool.setAside(camera.id, eur(6_000), now)
        decide(camera, buy(paid = 12_000))

        assertTrue(items.delete(stored(camera)))

        assertEquals(emptyList<Allocation>(), pool.allocations.first())
        assertEquals(PoolEntry.Purchase(null, eur(12_000), now), pool.entries.first().last())
        assertEquals(eur(18_000), funMoney().balance.unallocated)
    }

    @Test
    fun `the switch is stored with the allowance and can be changed`() = runTest {
        setUp(starting = 0, wantsNeedFunding = false)
        assertFalse(pool.allowance.first()!!.wantsNeedFunding)

        pool.change(eur(30_000), 1, eur(0), true, now, zone)
        assertTrue(pool.allowance.first()!!.wantsNeedFunding)
    }

    @Test
    fun `an allocation in an unknown currency is left out`() = runTest {
        setUp(starting = 30_000)
        val camera = add()
        pool.setAside(camera.id, eur(6_000), now)
        execSql(
            "INSERT INTO allocations (item_id, amount_minor, currency, at) VALUES (${camera.id.value}, 1, 'XXQ', 0)"
        )

        assertEquals(listOf(Allocation(camera.id, eur(6_000), now)), pool.allocations.first())
    }
}
