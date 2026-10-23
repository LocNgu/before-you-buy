package io.github.locngu.beforeyoubuy.core.domain.pool

import io.github.locngu.beforeyoubuy.core.domain.item.Decision
import io.github.locngu.beforeyoubuy.core.domain.item.DecisionType
import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.AFTER_COOLING
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.COMPLETE_REFLECTION
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.DURING_COOLING
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.Funding
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.Stage
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.item
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.stageOf
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SavingsTest {
    private val at = Instant.parse("2026-10-23T10:00:00Z")
    private val allowance = AllowanceSettings(eur(30_000), 1, LocalDate.of(2026, 10, 1))
    private val camera = item() // 120 €
    private val bike = item().copy(id = ItemId(2), name = "Bike", price = eur(160_000))

    private fun eur(minor: Long) = Money(minor, "EUR")

    private fun put(item: Item, minor: Long) = Allocation(item.id, eur(minor), at)

    /** A balance with [pool] in the fun money and [committed] set aside. */
    private fun balance(pool: Long, committed: Long = 0) = PoolBalance(eur(pool), eur(committed))

    @Test
    fun `an item holds the sum of what was set aside and taken back`() {
        val savings =
            savingsOf(allowance, listOf(put(camera, 5_000), put(camera, 3_000), put(camera, -1_000)), listOf(camera))
        assertEquals(eur(7_000), savings.allocatedFor(camera))
        assertEquals(eur(0), savings.allocatedFor(bike))
        assertEquals(eur(7_000), savings.committed("EUR"))
    }

    @Test
    fun `committed is what all active wants hold`() {
        val savings = savingsOf(allowance, listOf(put(camera, 5_000), put(bike, 65_000)), listOf(camera, bike))
        assertEquals(eur(70_000), savings.committed("EUR"))
        val money = funMoneyOf(
            allowance,
            listOf(PoolEntry.StartingBalance(eur(100_000), at)),
            savings
        ) as FunMoney.SetUp
        assertEquals(PoolBalance(eur(100_000), eur(70_000)), money.balance)
        assertEquals(eur(30_000), money.balance.unallocated)
    }

    @Test
    fun `rejecting or buying an item releases what it held`() {
        val allocations = listOf(put(camera, 5_000), put(bike, 65_000))
        // Only active items are passed in: a decided item's allocations no longer count.
        assertEquals(eur(65_000), savingsOf(allowance, allocations, listOf(bike)).committed("EUR"))
        val ended = listOf(camera.copy(status = ItemStatus.REJECTED), bike.copy(status = ItemStatus.PURCHASED))
        assertEquals(eur(0), savingsOf(allowance, allocations, ended).committed("EUR"))
    }

    @Test
    fun `a need never holds money`() {
        val need = camera.copy(kind = ItemKind.NEED)
        val savings = savingsOf(allowance, listOf(put(camera, 5_000)), listOf(need))
        assertEquals(eur(0), savings.allocatedFor(need))
        assertEquals(eur(0), savings.committed("EUR"))
        assertNull(savings.progressOf(need))
    }

    @Test
    fun `wants need their price set aside only while fun money is set up and the switch is on`() {
        assertTrue(savingsOf(allowance, emptyList(), listOf(camera)).required)
        assertFalse(savingsOf(allowance.copy(wantsNeedFunding = false), emptyList(), listOf(camera)).required)
        val none = savingsOf(null, emptyList(), listOf(camera))
        assertFalse(none.required)
        assertFalse(none.setUp)
        assertEquals(Savings.NONE, none)
    }

    @Test
    fun `an item is Ready only when cooled, reflected and funded`() {
        val half = savingsOf(allowance, listOf(put(camera, 6_000)), listOf(camera))
        val full = savingsOf(allowance, listOf(put(camera, 12_000)), listOf(camera))
        assertEquals(Stage.COOLING_OFF, stageOf(camera, COMPLETE_REFLECTION, full.fundingOf(camera), DURING_COOLING))
        assertEquals(Stage.NEEDS_REFLECTION, stageOf(camera, null, full.fundingOf(camera), AFTER_COOLING))
        assertEquals(Stage.SAVING, stageOf(camera, COMPLETE_REFLECTION, half.fundingOf(camera), AFTER_COOLING))
        assertEquals(Stage.READY, stageOf(camera, COMPLETE_REFLECTION, full.fundingOf(camera), AFTER_COOLING))
    }

    @Test
    fun `without fun money or with the switch off, a want is Ready without savings`() {
        val off = savingsOf(allowance.copy(wantsNeedFunding = false), emptyList(), listOf(camera))
        assertEquals(Stage.READY, stageOf(camera, COMPLETE_REFLECTION, off.fundingOf(camera), AFTER_COOLING))
        assertEquals(Stage.READY, stageOf(camera, COMPLETE_REFLECTION, Savings.NONE.fundingOf(camera), AFTER_COOLING))
    }

    @Test
    fun `a need is Ready at once with nothing set aside`() {
        val need = camera.copy(kind = ItemKind.NEED)
        val savings = savingsOf(allowance, emptyList(), listOf(need))
        assertEquals(Stage.READY, stageOf(need, null, savings.fundingOf(need), camera.addedAt))
    }

    @Test
    fun `amounts in another currency are relabeled to the item's`() {
        val dollars = Allocation(camera.id, Money(5_000, "USD"), at)
        assertEquals(eur(5_000), savingsOf(allowance, listOf(dollars), listOf(camera)).allocatedFor(camera))
    }

    @Test
    fun `progress shows what is set aside and what is still to go`() {
        val progress = SavingsProgress(eur(65_000), eur(160_000))
        assertEquals(eur(95_000), progress.toGo)
        assertFalse(progress.complete)
        assertEquals(0.40625f, progress.fraction, 0.0001f)
        val over = SavingsProgress(eur(20_000), eur(12_000))
        assertEquals(eur(0), over.toGo)
        assertTrue(over.complete)
        assertEquals(1f, over.fraction)
        assertEquals(1f, SavingsProgress(eur(0), eur(0)).fraction)
    }

    @Test
    fun `setting aside is limited by what isn't set aside yet`() {
        assertEquals(
            AllocationResult.Allowed(Allocation(camera.id, eur(10_000), at)),
            setAside(camera, eur(10_000), balance(30_000, committed = 20_000), at)
        )
        assertEquals(AllocationResult.MoreThanNotSetAside, setAside(camera, eur(10_001), balance(30_000, 20_000), at))
        assertEquals(AllocationResult.MoreThanNotSetAside, setAside(camera, eur(1), balance(-500), at))
    }

    @Test
    fun `saving can start while the item cools off`() {
        // Nothing about the stage: an active want can be saved for from day 1 (brief §4.6).
        assertTrue(setAside(camera, eur(1_000), balance(30_000), camera.addedAt) is AllocationResult.Allowed)
    }

    @Test
    fun `setting aside is refused for a need, an ended item, nothing, or without fun money`() {
        val enough = balance(100_000)
        assertEquals(AllocationResult.IsNeed, setAside(camera.copy(kind = ItemKind.NEED), eur(1_000), enough, at))
        assertEquals(
            AllocationResult.NotActive,
            setAside(camera.copy(status = ItemStatus.REJECTED), eur(1_000), enough, at)
        )
        assertEquals(AllocationResult.NotPositive, setAside(camera, eur(0), enough, at))
        assertEquals(AllocationResult.NotPositive, setAside(camera, eur(-100), enough, at))
        assertEquals(AllocationResult.NotSetUp, setAside(camera, eur(1_000), null, at))
    }

    @Test
    fun `taking back is limited by what the item holds`() {
        assertEquals(
            AllocationResult.Allowed(Allocation(camera.id, eur(-5_000), at)),
            takeBack(camera, eur(5_000), eur(5_000), at)
        )
        assertEquals(AllocationResult.MoreThanSetAside, takeBack(camera, eur(5_001), eur(5_000), at))
        assertEquals(AllocationResult.NotPositive, takeBack(camera, eur(0), eur(5_000), at))
        assertEquals(
            AllocationResult.NotActive,
            takeBack(camera.copy(status = ItemStatus.PURCHASED), eur(1), eur(5_000), at)
        )
    }

    @Test
    fun `amounts in another currency than the item's throw`() {
        assertThrows(IllegalArgumentException::class.java) { setAside(camera, Money(1, "USD"), balance(100), at) }
        assertThrows(IllegalArgumentException::class.java) { takeBack(camera, Money(1, "USD"), eur(100), at) }
    }

    @Test
    fun `buying a want spends its actual price from the fun money`() {
        val buy = Decision(DecisionType.BUY, at, actualPrice = eur(11_000))
        assertEquals(PoolEntry.Purchase(camera.id, eur(11_000), at), poolPurchaseOf(camera, buy, funMoneySetUp = true))
        val early = Decision(DecisionType.BUY_EARLY, at, actualPrice = eur(13_000), earlyReason = "Trip next week")
        assertEquals(
            PoolEntry.Purchase(camera.id, eur(13_000), at),
            poolPurchaseOf(camera, early, funMoneySetUp = true)
        )
    }

    @Test
    fun `buying a need, without fun money, or not buying leaves the fun money alone`() {
        val need = camera.copy(kind = ItemKind.NEED)
        assertNull(poolPurchaseOf(need, Decision(DecisionType.BUY_NEED, at, actualPrice = eur(50_000)), true))
        assertNull(poolPurchaseOf(camera, Decision(DecisionType.BUY, at, actualPrice = eur(11_000)), false))
        assertNull(poolPurchaseOf(camera, Decision(DecisionType.REJECT, at), true))
        assertNull(poolPurchaseOf(camera, Decision(DecisionType.WAIT, at, waitUntil = at.plusSeconds(60)), true))
    }

    @Test
    fun `buying below what was set aside returns the rest, above takes the difference`() {
        // 300 € in the fun money, 120 € of it set aside for the camera: 180 € not set aside.
        val entries = listOf(PoolEntry.StartingBalance(eur(30_000), at))
        val before = poolBalanceOf(entries, committed = eur(12_000))
        assertEquals(eur(18_000), before.unallocated)
        // Paid 100 €: the camera's 120 € are released and 100 € spent.
        val cheaper = poolBalanceOf(entries + PoolEntry.Purchase(camera.id, eur(10_000), at), committed = eur(0))
        assertEquals(eur(20_000), cheaper.unallocated)
        // Paid 150 €: 30 € more than set aside come from what wasn't set aside.
        val dearer = poolBalanceOf(entries + PoolEntry.Purchase(camera.id, eur(15_000), at), committed = eur(0))
        assertEquals(eur(15_000), dearer.unallocated)
    }

    @Test
    fun `buying above what is set aside and not set aside asks first`() {
        val before = balance(pool = 15_000, committed = 12_000) // 30 € not set aside
        assertFalse(purchaseNeedsConfirmation(before, allocated = eur(12_000), paid = eur(10_000)))
        assertFalse(purchaseNeedsConfirmation(before, allocated = eur(12_000), paid = eur(15_000)))
        assertTrue(purchaseNeedsConfirmation(before, allocated = eur(12_000), paid = eur(15_001)))
        // Paying less than set aside never asks, even below zero already.
        assertFalse(purchaseNeedsConfirmation(balance(pool = -1_000), allocated = eur(5_000), paid = eur(4_000)))
    }

    @Test
    fun `marking a want with savings as a need takes them all back`() {
        val need = camera.copy(kind = ItemKind.NEED, markedAsNeedAt = at)
        assertEquals(Allocation(camera.id, eur(-6_000), at), releaseWhenMarkedAsNeed(camera, need, eur(6_000)))
        assertNull(releaseWhenMarkedAsNeed(camera, need, eur(0)))
        assertNull(releaseWhenMarkedAsNeed(camera, camera.copy(name = "Lens"), eur(6_000)))
        assertNull(releaseWhenMarkedAsNeed(need, camera, eur(6_000)))
    }

    @Test
    fun `money taken back when marked as a need doesn't return when it's a want again`() {
        val allocations = listOf(put(camera, 6_000), Allocation(camera.id, eur(-6_000), at))
        assertEquals(eur(0), savingsOf(allowance, allocations, listOf(camera)).allocatedFor(camera))
        assertEquals(
            Funding(eur(0), required = true),
            savingsOf(allowance, allocations, listOf(camera)).fundingOf(camera)
        )
    }
}
