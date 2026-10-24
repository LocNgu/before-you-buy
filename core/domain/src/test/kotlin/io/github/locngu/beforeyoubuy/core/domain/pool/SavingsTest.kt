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
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.coversPrice
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.isFunded
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
        assertEquals(
            AllocationResult.MoreThanNotSetAside,
            setAside(camera, eur(10_100), balance(30_000, 20_000), at)
        )
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
        assertEquals(
            AllocationResult.IsNeed,
            setAside(camera.copy(kind = ItemKind.NEED), eur(1_000), enough, at)
        )
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
        assertEquals(AllocationResult.MoreThanSetAside, takeBack(camera, eur(5_100), eur(5_000), at))
        assertEquals(AllocationResult.NotPositive, takeBack(camera, eur(0), eur(5_000), at))
        assertEquals(
            AllocationResult.NotActive,
            takeBack(camera.copy(status = ItemStatus.PURCHASED), eur(1), eur(5_000), at)
        )
    }

    @Test
    fun `amounts in another currency than the item's throw`() {
        assertThrows(IllegalArgumentException::class.java) {
            setAside(camera, Money(1, "USD"), balance(100), at)
        }
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
        // 0,50 € below zero reads −1 €: asks. One cent below reads 0 €: doesn't.
        assertTrue(purchaseNeedsConfirmation(before, allocated = eur(12_000), paid = eur(15_050)))
        assertFalse(purchaseNeedsConfirmation(before, allocated = eur(12_000), paid = eur(15_001)))
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

    @Test
    fun `what a purchase leaves is what wasn't set aside plus its savings minus the price`() {
        val before = balance(pool = 15_000, committed = 12_000)
        assertEquals(eur(5_000), leftAfterPurchase(before, allocated = eur(12_000), paid = eur(10_000)))
        assertEquals(eur(-1_000), leftAfterPurchase(before, allocated = eur(12_000), paid = eur(16_000)))
    }

    @Test
    fun `the set-aside limit is what isn't set aside, never below zero, whatever the price`() {
        assertEquals(eur(18_000), setAsideLimit(balance(30_000, committed = 12_000), "EUR"))
        assertEquals(eur(0), setAsideLimit(balance(-500), "EUR"))
    }

    @Test
    fun `the rest fills up to the price, at most the limit, and is nothing once covered`() {
        val progress = SavingsProgress(eur(6_000), eur(12_000))
        assertEquals(eur(6_000), restToSetAside(progress, eur(10_000)))
        assertEquals(eur(2_500), restToSetAside(progress, eur(2_500)))
        assertNull(restToSetAside(progress, eur(0)))
        assertNull(restToSetAside(SavingsProgress(eur(12_000), eur(12_000)), eur(10_000)))
    }

    @Test
    fun `setting aside can go beyond the price`() {
        // A holiday with a minimum budget: more doesn't hurt (ADR-0046).
        assertEquals(
            AllocationResult.Allowed(Allocation(camera.id, eur(50_000), at)),
            setAside(camera, eur(50_000), balance(100_000), at)
        )
    }

    @Test
    fun `an amount that reads as the limit moves exactly the limit`() {
        // 599,99 € not set aside reads "600 €": typing 600 sets aside 599,99 €; 601 is too much.
        val left = balance(59_999)
        assertEquals(
            AllocationResult.Allowed(Allocation(camera.id, eur(59_999), at)),
            setAside(camera, eur(60_000), left, at)
        )
        assertEquals(AllocationResult.MoreThanNotSetAside, setAside(camera, eur(60_100), left, at))
        // Taking back 650 € from 649,50 € takes back 649,50 €.
        assertEquals(
            AllocationResult.Allowed(Allocation(camera.id, eur(-64_950), at)),
            takeBack(camera, eur(65_000), eur(64_950), at)
        )
        assertEquals(eur(59_999), withinShown(eur(60_000), eur(59_999)))
        assertEquals(eur(100), withinShown(eur(100), eur(59_999)))
        assertNull(withinShown(eur(60_100), eur(59_999)))
        // Nothing to move: a few cents below zero aren't "0 €" to set aside.
        assertNull(withinShown(eur(40), eur(0)))
        assertEquals(AllocationResult.MoreThanNotSetAside, setAside(camera, eur(40), balance(-10), at))
    }

    @Test
    fun `an item is funded once what's set aside reads as its price`() {
        // 1.599,60 € of 1.600 € shows as "1.600 € of 1.600 €": funded, nothing shown to go.
        val almost = SavingsProgress(Money(159_960, "EUR"), Money(160_000, "EUR"))
        assertTrue(almost.complete)
        assertEquals(eur(0), almost.toGo)
        assertEquals(eur(0), almost.shownToGo)
        assertEquals(1f, almost.fraction)
        assertTrue(coversPrice(Money(159_950, "EUR"), Money(160_000, "EUR")))
        assertFalse(coversPrice(Money(159_949, "EUR"), Money(160_000, "EUR")))
        val bike = camera.copy(price = Money(160_000, "EUR"))
        assertTrue(isFunded(bike, Funding(Money(159_960, "EUR"), required = true)))
        assertFalse(isFunded(bike, Funding(Money(159_940, "EUR"), required = true)))
    }

    @Test
    fun `what's shown to go is the difference of the amounts as shown`() {
        // 18,40 € (18 €) of 19,60 € (20 €): "18 € of 20 € set aside · 2 € to go", never "1 €".
        val progress = SavingsProgress(eur(1_840), eur(1_960))
        assertEquals(eur(120), progress.toGo)
        assertEquals(eur(200), progress.shownToGo)
        assertEquals(eur(95_000), SavingsProgress(eur(65_000), eur(160_000)).shownToGo)
    }

    @Test
    fun `quick amounts are 50 and 100 in whole units, those that fit`() {
        assertEquals(listOf(eur(5_000), eur(10_000)), quickAmounts(eur(35_000)))
        assertEquals(listOf(eur(5_000)), quickAmounts(eur(9_999)))
        assertEquals(emptyList<Money>(), quickAmounts(eur(4_999)))
        assertEquals(listOf(Money(50, "JPY"), Money(100, "JPY")), quickAmounts(Money(100, "JPY")))
    }

    @Test
    fun `an amount is checked against the limit`() {
        assertNull(checkAmount(eur(5_000), eur(5_000)))
        assertEquals(AmountProblem.ZERO, checkAmount(eur(0), eur(5_000)))
        assertEquals(AmountProblem.MORE_THAN_LIMIT, checkAmount(eur(5_100), eur(5_000)))
        // Reads the same as the limit: allowed (it moves exactly the limit).
        assertNull(checkAmount(eur(60_000), eur(59_999)))
    }

    @Test
    fun `a price with cents is funded once the shown amounts match, less than one whole unit short`() {
        // 1.600,49 € shows as 1.600 €; 1.599,50 € set aside shows as 1.600 €: funded, 0,99 € short.
        val price = Money(160_049, "EUR")
        assertTrue(coversPrice(Money(159_950, "EUR"), price))
        assertFalse(coversPrice(Money(159_949, "EUR"), price))
        val progress = SavingsProgress(Money(159_950, "EUR"), price)
        assertTrue(progress.complete)
        assertEquals(eur(0), progress.shownToGo)
        // 1.600,50 € shows as 1.601 €: 1.600,49 € set aside (1.600 €) is still 1 € to go.
        val above = SavingsProgress(Money(160_049, "EUR"), Money(160_050, "EUR"))
        assertFalse(above.complete)
        assertEquals(eur(100), above.shownToGo)
    }

    @Test
    fun `a purchase a few cents below zero, which reads 0 €, doesn't ask`() {
        // 1.599,60 € set aside, 0,10 € not set aside, paid 1.600 €: −0,30 € left, shown as 0 €.
        val before = balance(pool = 159_970, committed = 159_960)
        assertEquals(eur(-30), leftAfterPurchase(before, allocated = eur(159_960), paid = eur(160_000)))
        assertFalse(purchaseNeedsConfirmation(before, allocated = eur(159_960), paid = eur(160_000)))
        // −0,50 € reads −1 €: asks.
        assertTrue(purchaseNeedsConfirmation(before, allocated = eur(159_960), paid = eur(160_020)))
    }
}
