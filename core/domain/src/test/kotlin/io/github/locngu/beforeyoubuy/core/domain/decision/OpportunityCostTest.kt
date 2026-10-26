package io.github.locngu.beforeyoubuy.core.domain.decision

import io.github.locngu.beforeyoubuy.core.domain.decision.OpportunityCostLine.DelaysPriority
import io.github.locngu.beforeyoubuy.core.domain.decision.OpportunityCostLine.MonthsOfAllowance
import io.github.locngu.beforeyoubuy.core.domain.decision.OpportunityCostLine.ShareOfGoal
import io.github.locngu.beforeyoubuy.core.domain.decision.OpportunityCostLine.SmallerItemsCombined
import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.ADDED
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.item
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.core.domain.pool.AllowanceSettings
import io.github.locngu.beforeyoubuy.core.domain.pool.FunMoney
import io.github.locngu.beforeyoubuy.core.domain.pool.PoolBalance
import io.github.locngu.beforeyoubuy.core.domain.pool.Savings
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpportunityCostTest {
    private val camera = item().copy(price = eur(120_000)) // 1.200 €

    private fun eur(minor: Long) = Money(minor, "EUR")

    private fun other(id: Long, minor: Long, slot: Int? = null, kind: ItemKind = ItemKind.WANT) = item(kind).copy(
        id = ItemId(id),
        name = "Item $id",
        price = eur(minor),
        addedAt = ADDED.plusSeconds(id),
        prioritySlot = slot
    )

    /** Fun money of [monthly] a month with [unallocated] not yet set aside. */
    private fun funMoney(monthly: Long = 30_000, unallocated: Long = 0) = FunMoney.SetUp(
        allowance = AllowanceSettings(eur(monthly), 1, LocalDate.of(2026, 10, 1)),
        balance = PoolBalance(pool = eur(unallocated), committed = eur(0)),
        nextAccrual = LocalDate.of(2026, 12, 1),
        startingBalance = eur(0)
    )

    private fun savings(vararg held: Pair<Item, Long>) =
        Savings(setUp = true, required = true, byItem = held.associate { (item, minor) -> item.id to eur(minor) })

    private fun linesOf(
        item: Item = camera,
        active: List<Item> = emptyList(),
        savings: Savings = savings(),
        funMoney: FunMoney = funMoney()
    ) = opportunityCostOf(item, active, savings, funMoney)

    private inline fun <reified T : OpportunityCostLine> List<OpportunityCostLine>.only(): T? =
        filterIsInstance<T>().singleOrNull()

    // Months of fun money

    @Test
    fun `months of fun money are exact when the price is a whole number of monthly amounts`() {
        assertEquals(MonthsOfAllowance(4, exact = true), linesOf().only<MonthsOfAllowance>())
    }

    @Test
    fun `months of fun money round half up to about whole months`() {
        // 1.000 € / 300 € = 3,33; 1.050 € / 300 € = 3,5; 310 € / 300 € = 1,03.
        assertEquals(
            MonthsOfAllowance(3, exact = false),
            linesOf(camera.copy(price = eur(100_000))).only<MonthsOfAllowance>()
        )
        assertEquals(
            MonthsOfAllowance(4, exact = false),
            linesOf(camera.copy(price = eur(105_000))).only<MonthsOfAllowance>()
        )
        assertEquals(
            MonthsOfAllowance(1, exact = false),
            linesOf(camera.copy(price = eur(31_000))).only<MonthsOfAllowance>()
        )
    }

    @Test
    fun `less than one monthly amount is less than a month`() {
        assertEquals(
            MonthsOfAllowance(0, exact = false),
            linesOf(camera.copy(price = eur(29_999))).only<MonthsOfAllowance>()
        )
    }

    @Test
    fun `no months without fun money or with a monthly amount of zero`() {
        assertTrue(linesOf(funMoney = FunMoney.NotSetUp).isEmpty())
        assertTrue(linesOf(funMoney = funMoney(monthly = 0)).isEmpty())
    }

    // Share of another priority

    @Test
    fun `the share is of the highest-ranked other priority, rounded to whole percent`() {
        val bike = other(2, 160_000, slot = 1)
        val lamp = other(3, 10_000, slot = 2)

        assertEquals(ShareOfGoal(bike, 1, 75), linesOf(active = listOf(lamp, bike)).only<ShareOfGoal>())
        // 1.200 € of 1.700 € = 70,6 %.
        val dearer = bike.copy(price = eur(170_000))
        assertEquals(ShareOfGoal(dearer, 1, 71), linesOf(active = listOf(dearer)).only<ShareOfGoal>())
    }

    @Test
    fun `the item's own slot isn't another priority`() {
        val bike = other(2, 240_000, slot = 2)

        assertEquals(
            ShareOfGoal(bike, 2, 50),
            linesOf(camera.copy(prioritySlot = 1), listOf(camera.copy(prioritySlot = 1), bike)).only<ShareOfGoal>()
        )
    }

    @Test
    fun `less than half a percent is less than 1 percent, up to the goal's whole price`() {
        val house = other(2, 100_000_000, slot = 1)
        val same = other(3, 120_000, slot = 1)

        assertEquals(ShareOfGoal(house, 1, 0), linesOf(active = listOf(house)).only<ShareOfGoal>())
        assertEquals(ShareOfGoal(same, 1, 100), linesOf(active = listOf(same)).only<ShareOfGoal>())
    }

    @Test
    fun `no share of a cheaper goal, so don't buy doesn't keep the price for it`() {
        // 1.200 € for 400 € socks would read "300 %" and "keep 1.200 € for your Socks (#1)".
        val socks = other(2, 40_000, slot = 1)

        val lines = linesOf(active = listOf(socks), funMoney = FunMoney.NotSetUp)

        assertNull(lines.only<ShareOfGoal>())
        assertNull(lines.keepFor())
    }

    @Test
    fun `no share without another priority, or with a free one`() {
        assertNull(linesOf(active = listOf(other(2, 160_000))).only<ShareOfGoal>())
        assertNull(linesOf(active = listOf(other(2, 0, slot = 1))).only<ShareOfGoal>())
        assertNull(
            linesOf(active = listOf(other(2, 160_000, slot = 1).copy(status = ItemStatus.REJECTED)))
                .only<ShareOfGoal>()
        )
    }

    // Delay to a priority

    @Test
    fun `buying delays a priority by the monthly amounts it takes to make up the price`() {
        // Bike needs 600 €. Not buying: 2 months. Buying 1.200 € from nothing: 1.800 € to make up, 6 months.
        val bike = other(2, 60_000, slot = 1)

        assertEquals(DelaysPriority(bike, 1, 4), linesOf(active = listOf(bike)).only<DelaysPriority>())
    }

    @Test
    fun `the delay names the highest-ranked priority it pushes back by a month or more`() {
        // 2.000 € not set aside: Bike (600 €) is covered either way; Lamp (500 €) then needs 1.100 €, which buying
        // leaves 300 € short of, one month.
        val bike = other(2, 60_000, slot = 1)
        val lamp = other(3, 50_000, slot = 2)

        assertEquals(
            DelaysPriority(lamp, 2, 1),
            linesOf(active = listOf(bike, lamp), funMoney = funMoney(unallocated = 200_000)).only<DelaysPriority>()
        )
    }

    @Test
    fun `what the item holds comes back if it isn't bought, what a priority holds counts for it`() {
        val bike = other(2, 60_000, slot = 1)

        // The camera holds its price: not buying funds the bike at once, buying leaves it 2 months away.
        assertEquals(
            DelaysPriority(bike, 1, 2),
            linesOf(active = listOf(bike), savings = savings(camera to 120_000)).only<DelaysPriority>()
        )
        // The bike holds its price already: nothing to delay, even though buying goes below zero.
        assertNull(linesOf(active = listOf(bike), savings = savings(bike to 60_000)).only<DelaysPriority>())
        // A priority below a covered one is still delayed by the hole buying leaves.
        val lamp = other(3, 30_000, slot = 2)
        assertEquals(
            DelaysPriority(lamp, 2, 4),
            linesOf(active = listOf(bike, lamp), savings = savings(bike to 60_000)).only<DelaysPriority>()
        )
    }

    @Test
    fun `no delay when the priorities are covered either way, without priorities or without fun money`() {
        val bike = other(2, 60_000, slot = 1)

        assertNull(linesOf(active = listOf(bike), funMoney = funMoney(unallocated = 180_000)).only<DelaysPriority>())
        assertNull(linesOf(active = listOf(other(2, 60_000))).only<DelaysPriority>())
        assertNull(linesOf(active = listOf(bike), funMoney = FunMoney.NotSetUp).only<DelaysPriority>())
        assertNull(linesOf(active = listOf(bike), funMoney = funMoney(monthly = 0)).only<DelaysPriority>())
    }

    // The cheapest other items combined

    @Test
    fun `the cheapest other items that together cost no more than the price`() {
        val cheap = listOf(other(2, 30_000), other(3, 10_000), other(4, 20_000))
        val dear = other(5, 90_000)

        val line = linesOf(active = cheap + dear + camera).only<SmallerItemsCombined>()

        assertEquals(listOf(ItemId(3), ItemId(4), ItemId(2)), line?.items?.map { it.id })
        assertEquals(eur(60_000), line?.total)
        assertEquals(false, line?.all)
    }

    @Test
    fun `all the other items fit, a need counts, an ended one doesn't`() {
        val items = listOf(
            other(2, 30_000),
            other(3, 10_000, kind = ItemKind.NEED),
            other(5, 1_000).copy(status = ItemStatus.PURCHASED)
        )

        assertEquals(
            SmallerItemsCombined(listOf(items[1], items[0]), eur(40_000), all = true),
            linesOf(active = items).only<SmallerItemsCombined>()
        )
    }

    @Test
    fun `a free item isn't counted, and then they aren't all the other items`() {
        val items = listOf(other(2, 30_000), other(3, 10_000), other(4, 0))

        assertEquals(
            SmallerItemsCombined(listOf(items[1], items[0]), eur(40_000), all = false),
            linesOf(active = items).only<SmallerItemsCombined>()
        )
    }

    @Test
    fun `one item isn't a set`() {
        assertNull(linesOf(active = listOf(other(2, 30_000), other(3, 100_000))).only<SmallerItemsCombined>())
    }

    // Together

    @Test
    fun `at most one line of each kind, the delay first, then the share, the months and the items`() {
        val bike = other(2, 160_000, slot = 1)
        val small = listOf(other(3, 10_000), other(4, 20_000))

        val lines = linesOf(active = listOf(camera, bike) + small)

        assertEquals(
            listOf(
                DelaysPriority::class,
                ShareOfGoal::class,
                MonthsOfAllowance::class,
                SmallerItemsCombined::class
            ),
            lines.map { it::class }
        )
        assertEquals(bike, lines.keepFor()?.priority)
        assertEquals(1, lines.keepFor()?.rank)
    }

    @Test
    fun `don't buy names a priority only when the top line is about one`() {
        val bike = other(2, 160_000, slot = 1)

        assertEquals(
            ShareOfGoal(bike, 1, 75),
            linesOf(active = listOf(bike), funMoney = funMoney(unallocated = 300_000)).keepFor()
        )
        assertNull(linesOf().keepFor())
        assertNull(emptyList<OpportunityCostLine>().keepFor())
    }

    @Test
    fun `a need only gets the price comparisons, never the fun money ones`() {
        val bike = other(2, 160_000, slot = 1)
        val small = listOf(other(3, 10_000), other(4, 20_000))

        val lines = linesOf(item(ItemKind.NEED).copy(price = eur(120_000)), listOf(bike) + small)

        assertEquals(listOf(ShareOfGoal::class, SmallerItemsCombined::class), lines.map { it::class })
    }

    @Test
    fun `a free item has nothing to compare`() {
        assertTrue(linesOf(camera.copy(price = eur(0)), listOf(other(2, 160_000, slot = 1))).isEmpty())
    }

    @Test
    fun `nothing is invented without fun money, priorities or other items`() {
        assertTrue(linesOf(funMoney = FunMoney.NotSetUp).isEmpty())
    }

    @Test
    fun `amounts in another currency are relabeled to the item's`() {
        val bike = other(2, 160_000, slot = 1).copy(price = Money(160_000, "USD"))

        assertEquals(ShareOfGoal(bike, 1, 75), linesOf(active = listOf(bike)).only<ShareOfGoal>())
    }
}
