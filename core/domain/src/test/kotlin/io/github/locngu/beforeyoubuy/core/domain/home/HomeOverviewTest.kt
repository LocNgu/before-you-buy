package io.github.locngu.beforeyoubuy.core.domain.home

import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.AFTER_COOLING
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.COMPLETE_REFLECTION
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.COOLING_ENDS
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.Funding
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.PRICE
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.Stage
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.isDueForDecision
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.item
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.parked
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.core.domain.pool.Allocation
import io.github.locngu.beforeyoubuy.core.domain.pool.AllowanceSettings
import io.github.locngu.beforeyoubuy.core.domain.pool.Savings
import io.github.locngu.beforeyoubuy.core.domain.pool.savingsOf
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeOverviewTest {
    private val zone: ZoneId = ZoneOffset.UTC

    /** A priority (slot 1) whose wait runs from [ADDED] until [endsAt]; a need, which doesn't wait. */
    private fun wish(id: Long, endsAt: Instant = COOLING_ENDS, kind: ItemKind = ItemKind.WANT): Item =
        if (kind == ItemKind.NEED) {
            item(kind).copy(id = ItemId(id))
        } else {
            item().copy(id = ItemId(id), coolingEndsAt = endsAt)
        }

    /** A want without a slot (ADR-0049). */
    private fun parkedWish(id: Long, waitedDays: Int = 0): Item = parked(waitedDays).copy(id = ItemId(id))

    @Test
    fun `nothing to decide, consider or count without items`() {
        assertEquals(
            HomeOverview(emptyList(), 0, null),
            homeOverviewOf(emptyList(), emptyMap(), Savings.NONE, AFTER_COOLING, zone)
        )
    }

    @Test
    fun `ready items come first, then those whose cooling off ended without answers`() {
        val unreflected = wish(1, endsAt = Instant.parse("2026-10-10T10:00:00Z"))
        val ready = wish(2, endsAt = Instant.parse("2026-10-20T10:00:00Z"))
        val need = wish(3, kind = ItemKind.NEED)
        val cooling = wish(4, endsAt = Instant.parse("2026-12-01T10:00:00Z"))
        val parked = parkedWish(5, waitedDays = 40)

        val home = homeOverviewOf(
            listOf(cooling, need, unreflected, ready, parked),
            mapOf(ready.id to COMPLETE_REFLECTION, parked.id to COMPLETE_REFLECTION),
            Savings.NONE,
            AFTER_COOLING,
            zone
        )

        // A need has no end, so it comes first in the Ready group; a parked want is never to decide (ADR-0049).
        assertEquals(listOf(need, ready, unreflected), home.toDecide.map { it.item })
        assertEquals(listOf(Stage.READY, Stage.READY, Stage.NEEDS_REFLECTION), home.toDecide.map { it.stage })
    }

    @Test
    fun `the ready part is exactly what isDueForDecision accepts`() {
        val due = wish(1, endsAt = Instant.parse("2026-10-20T10:00:00Z"))
        val waiting = wish(2, endsAt = Instant.parse("2026-10-20T10:00:00Z"))
            .copy(recheckAt = Instant.parse("2026-11-10T00:00:00Z"))
        val recheckPassed = wish(3, endsAt = Instant.parse("2026-10-20T10:00:00Z"))
            .copy(recheckAt = Instant.parse("2026-11-01T00:00:00Z"))
        val items = listOf(due, waiting, recheckPassed)
        val reflections = items.associate { it.id to COMPLETE_REFLECTION }

        val home = homeOverviewOf(items, reflections, Savings.NONE, AFTER_COOLING, zone)

        val expected = items.filter {
            isDueForDecision(it, reflections[it.id], Funding(PRICE, required = true), AFTER_COOLING)
        }
        assertEquals(expected, home.toDecide.map { it.item })
        assertEquals(listOf(due, recheckPassed), expected)
    }

    @Test
    fun `items still cooling off are never offered for a decision`() {
        val home = homeOverviewOf(listOf(wish(1)), emptyMap(), Savings.NONE, COOLING_ENDS.minusSeconds(1), zone)

        assertEquals(emptyList<Item>(), home.toDecide.map { it.item })
    }

    @Test
    fun `counts every active item, waiting and parked ones included, and nothing ended`() {
        val waiting = wish(1, endsAt = Instant.parse("2026-10-20T10:00:00Z"))
            .copy(recheckAt = Instant.parse("2026-11-10T00:00:00Z"))
        val cooling = wish(2, endsAt = Instant.parse("2026-12-01T10:00:00Z"))
        val bought = wish(3).copy(status = ItemStatus.PURCHASED)
        val rejected = wish(4).copy(status = ItemStatus.REJECTED)
        val parked = parkedWish(5)

        val home = homeOverviewOf(
            listOf(waiting, cooling, bought, rejected, parked),
            mapOf(waiting.id to COMPLETE_REFLECTION),
            Savings.NONE,
            AFTER_COOLING,
            zone
        )

        assertEquals(3, home.activeCount)
    }

    @Test
    fun `the next cooling-off end is the soonest one, with its days left`() {
        val later = wish(1, endsAt = Instant.parse("2026-12-01T10:00:00Z"))
        val sooner = wish(2, endsAt = Instant.parse("2026-11-14T10:00:00Z"))
        val ended = wish(3, endsAt = Instant.parse("2026-10-20T10:00:00Z"))
        val parked = parkedWish(4)

        val next = homeOverviewOf(
            listOf(later, ended, sooner, parked),
            emptyMap(),
            Savings.NONE,
            AFTER_COOLING,
            zone
        ).nextCoolingEnd

        assertEquals(sooner, next?.item)
        assertEquals(12, next?.daysLeft)
    }

    @Test
    fun `no next cooling-off end once every wait is over`() {
        val home = homeOverviewOf(listOf(wish(1)), emptyMap(), Savings.NONE, AFTER_COOLING, zone)

        assertNull(home.nextCoolingEnd)
    }

    @Test
    fun `saving for lists up to three wants without priorities, most set aside first, never needs`() {
        val allowance = AllowanceSettings(Money(30_000, "EUR"), 1, LocalDate.of(2026, 10, 1))
        val wants = (1L..5L).map { parkedWish(it) }
        val need = wish(9, kind = ItemKind.NEED)
        val active = wants + need
        fun put(item: Item, minor: Long) = Allocation(item.id, Money(minor, "EUR"), COOLING_ENDS)
        val savings = savingsOf(allowance, listOf(put(wants[1], 6_000), put(wants[2], 9_000)), active)

        val overview = homeOverviewOf(active, emptyMap(), savings, AFTER_COOLING, zone)

        assertEquals(listOf(3L, 2L, 1L), overview.savingFor.map { it.item.id.value })
        assertEquals(Money(9_000, "EUR"), overview.savingFor[0].progress.allocated)
        assertEquals(emptyList<Any>(), overview.priorities)
        assertEquals(
            emptyList<SavingFor>(),
            homeOverviewOf(active, emptyMap(), Savings.NONE, AFTER_COOLING, zone).savingFor
        )
    }

    @Test
    fun `with priorities, saving for becomes every priority by rank, with savings only once fun money is set up`() {
        val allowance = AllowanceSettings(Money(30_000, "EUR"), 1, LocalDate.of(2026, 10, 1))
        val second = wish(1).copy(prioritySlot = 2)
        val first = wish(2).copy(prioritySlot = 1)
        val other = parkedWish(3)
        val active = listOf(second, first, other)
        val savings = savingsOf(allowance, listOf(Allocation(second.id, Money(3_000, "EUR"), COOLING_ENDS)), active)

        val overview = homeOverviewOf(active, emptyMap(), savings, AFTER_COOLING, zone)

        assertEquals(listOf(1 to first.id, 2 to second.id), overview.priorities.map { it.rank to it.entry.item.id })
        assertEquals(Money(3_000, "EUR"), overview.priorities[1].entry.savings?.allocated)
        assertEquals(emptyList<SavingFor>(), overview.savingFor)
        val withoutFunMoney = homeOverviewOf(active, emptyMap(), Savings.NONE, AFTER_COOLING, zone)
        assertEquals(2, withoutFunMoney.priorities.size)
        assertNull(withoutFunMoney.priorities[0].entry.savings)
    }

    @Test
    fun `a want without its price set aside is saving, not to decide`() {
        val allowance = AllowanceSettings(Money(30_000, "EUR"), 1, LocalDate.of(2026, 10, 1))
        val camera = wish(1)
        val savings = savingsOf(allowance, emptyList(), listOf(camera))

        val overview =
            homeOverviewOf(listOf(camera), mapOf(camera.id to COMPLETE_REFLECTION), savings, AFTER_COOLING, zone)

        assertEquals(emptyList<Any>(), overview.toDecide)
        assertEquals(1, overview.activeCount)
    }
}
