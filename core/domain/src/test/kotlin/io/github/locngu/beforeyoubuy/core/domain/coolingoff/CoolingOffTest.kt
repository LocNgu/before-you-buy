package io.github.locngu.beforeyoubuy.core.domain.coolingoff

import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.isCooledOff
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import java.time.Instant
import java.time.Period
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CoolingOffTest {
    private val rules = CoolingOffRules.defaults("EUR")
    private val utc: ZoneId = ZoneOffset.UTC
    private val berlin: ZoneId = ZoneId.of("Europe/Berlin")

    private val added = Instant.parse("2026-10-01T10:00:00Z")

    private fun eur(minorUnits: Long) = Money(minorUnits, "EUR")

    private fun daysAfterAdded(days: Long): Instant = added.plus(days, ChronoUnit.DAYS) // UTC: a day is 24 h

    /** A want at [price] parked since [added]: no slot, nothing waited ([waited] days in earlier stretches). */
    private fun parked(price: Money, kind: ItemKind = ItemKind.WANT, waited: Int = 0) = Item(
        id = ItemId(1),
        name = "Camera",
        price = price,
        addedAt = added,
        coolingOffDays = coolingOffDaysFor(price, rules),
        waitedDays = waited,
        kind = kind
    )

    /** A want at [price] that got priority slot 1 when it was added, so its wait runs from [added]. */
    private fun item(price: Money, kind: ItemKind = ItemKind.WANT): Item =
        if (kind == ItemKind.NEED) {
            parked(price, kind)
        } else {
            startWait(parked(price).copy(prioritySlot = 1), added, utc)
        }

    @Test
    fun `each default tier boundary`() {
        val cases = listOf(
            0L to 7,
            9_999L to 7,
            10_000L to 30,
            49_999L to 30,
            50_000L to 60,
            99_999L to 60,
            100_000L to 90,
            100_000_000L to 90
        )
        for ((price, days) in cases) {
            assertEquals("price $price", Period.ofDays(days), periodFor(eur(price), rules))
        }
    }

    @Test
    fun `periodFor rejects a price in another currency or below zero`() {
        assertThrows(IllegalArgumentException::class.java) { periodFor(Money(5_000, "USD"), rules) }
        assertThrows(IllegalArgumentException::class.java) { periodFor(eur(-1), rules) }
    }

    @Test
    fun `a want waits the days of its price's tier`() {
        assertEquals(7, coolingOffDaysFor(eur(5_000), rules))
        assertEquals(90, coolingOffDaysFor(eur(250_000), rules))
    }

    @Test
    fun `a new wait runs the days still to go from when the slot came`() {
        val at = daysAfterAdded(5)
        val started = startWait(parked(eur(12_000), waited = 10).copy(prioritySlot = 1), at, utc)
        assertEquals(at, started.waitingSince)
        assertEquals(daysAfterAdded(25), started.coolingEndsAt) // 30 days, 10 already waited
        assertEquals(10, started.waitedDays)
    }

    @Test
    fun `a want that has already waited its full period is complete as soon as it gets a slot`() {
        val at = daysAfterAdded(40)
        val started = startWait(parked(eur(12_000), waited = 30).copy(prioritySlot = 1), at, utc)
        assertEquals(at, started.coolingEndsAt)
        assertTrue(isCooledOff(started, at))
    }

    @Test
    fun `starting leaves a running wait, needs and ended items alone`() {
        val running = item(eur(12_000))
        assertSame(running, startWait(running, daysAfterAdded(3), utc))
        val need = parked(eur(12_000), ItemKind.NEED)
        assertSame(need, startWait(need, daysAfterAdded(3), utc))
        val ended = parked(eur(12_000)).copy(status = ItemStatus.PURCHASED)
        assertSame(ended, startWait(ended, daysAfterAdded(3), utc))
    }

    @Test
    fun `waits are calendar days across the spring daylight-saving change in Berlin`() {
        // 25 March 2026, 10:00 CET; clocks go forward on 29 March, so 7 calendar days are 7 × 24 h minus one hour.
        val at = Instant.parse("2026-03-25T09:00:00Z")
        assertEquals(
            Instant.parse("2026-04-01T08:00:00Z"), // 1 April, 10:00 CEST
            startWait(parked(eur(5_000)), at, berlin).coolingEndsAt
        )
    }

    @Test
    fun `waits are calendar days across the autumn daylight-saving change in Berlin`() {
        // 20 October 2026, 10:00 CEST; clocks go back on 25 October, so 7 calendar days are 7 × 24 h plus one hour.
        val at = Instant.parse("2026-10-20T08:00:00Z")
        assertEquals(
            Instant.parse("2026-10-27T09:00:00Z"), // 27 October, 10:00 CET
            startWait(parked(eur(5_000)), at, berlin).coolingEndsAt
        )
    }

    @Test
    fun `an end inside the skipped spring hour moves forward by the gap`() {
        // 22 March 2026, 02:30 CET; 29 March 02:30 doesn't exist in Berlin, so it ends at 03:30 CEST.
        val at = Instant.parse("2026-03-22T01:30:00Z")
        assertEquals(Instant.parse("2026-03-29T01:30:00Z"), startWait(parked(eur(5_000)), at, berlin).coolingEndsAt)
    }

    @Test
    fun `an end inside the repeated autumn hour takes the earlier one`() {
        // 18 October 2026, 02:30 CEST; 25 October 02:30 happens twice in Berlin, so it ends at the first, in CEST.
        val at = Instant.parse("2026-10-18T00:30:00Z")
        assertEquals(Instant.parse("2026-10-25T00:30:00Z"), startWait(parked(eur(5_000)), at, berlin).coolingEndsAt)
    }

    @Test
    fun `pausing keeps the whole days waited and stops the wait`() {
        val paused = pauseWait(item(eur(12_000)), daysAfterAdded(10), utc)
        assertEquals(10, paused.waitedDays)
        assertNull(paused.waitingSince)
        assertNull(paused.coolingEndsAt)
        assertEquals(30, paused.coolingOffDays)
    }

    @Test
    fun `pausing drops a part of a day, so it never counts time that hasn't passed`() {
        val paused = pauseWait(item(eur(12_000)), daysAfterAdded(10).minusSeconds(60), utc)
        assertEquals(9, paused.waitedDays)
    }

    @Test
    fun `a day only counts once the same time comes round, also on the 25-hour autumn day in Berlin`() {
        // 24 October 2026, 10:00 CEST. The next day is 25 hours long: after 24 h 30 min it's 09:30 CET, not 10:00.
        val since = Instant.parse("2026-10-24T08:00:00Z")
        val running = startWait(parked(eur(5_000)), since, berlin)
        assertEquals(0, pauseWait(running, Instant.parse("2026-10-25T08:30:00Z"), berlin).waitedDays)
        assertEquals(1, pauseWait(running, Instant.parse("2026-10-25T09:00:00Z"), berlin).waitedDays)
    }

    @Test
    fun `a paused wait continues where it stopped`() {
        val paused = pauseWait(item(eur(12_000)), daysAfterAdded(10), utc)
        val resumed = startWait(paused, daysAfterAdded(20), utc)
        assertEquals(daysAfterAdded(40), resumed.coolingEndsAt) // 20 more days from day 20
        assertFalse(isCooledOff(resumed, daysAfterAdded(39)))
        assertTrue(isCooledOff(resumed, daysAfterAdded(40)))
    }

    @Test
    fun `pausing a wait that doesn't run changes nothing`() {
        val parked = parked(eur(12_000), waited = 4)
        assertSame(parked, pauseWait(parked, daysAfterAdded(9), utc))
    }

    @Test
    fun `days waited count the earlier stretches and the running one`() {
        val resumed = startWait(parked(eur(12_000), waited = 10), daysAfterAdded(20), utc)
        assertEquals(10, daysWaited(resumed, daysAfterAdded(20), utc))
        assertEquals(13, daysWaited(resumed, daysAfterAdded(23).plusSeconds(3_600), utc))
        assertEquals(4, daysWaited(parked(eur(12_000), waited = 4), daysAfterAdded(50), utc))
    }

    @Test
    fun `a need's price is checked against the rules like a want's`() {
        assertThrows(IllegalArgumentException::class.java) { coolingOffDaysFor(Money(5_000, "USD"), rules) }
        assertThrows(IllegalArgumentException::class.java) {
            waitAfterPriceEdit(item(eur(5_000), ItemKind.NEED), eur(-1), rules, utc)
        }
        val usdRules = CoolingOffRules.defaults("USD")
        assertThrows(IllegalArgumentException::class.java) {
            changeKind(item(eur(5_000)), ItemKind.NEED, daysAfterAdded(1), usdRules, utc)
        }
    }

    @Test
    fun `a price edit up extends the wait, counted from when the slot came`() {
        val edited = waitAfterPriceEdit(item(eur(5_000)), eur(12_000), rules, utc)
        assertEquals(30, edited.coolingOffDays)
        assertEquals(daysAfterAdded(30), edited.coolingEndsAt)
        assertEquals(daysAfterAdded(90), waitAfterPriceEdit(item(eur(5_000)), eur(100_000), rules, utc).coolingEndsAt)
    }

    @Test
    fun `a price edit up on a parked want extends the days it will wait`() {
        val edited = waitAfterPriceEdit(parked(eur(5_000), waited = 3), eur(12_000), rules, utc)
        assertEquals(30, edited.coolingOffDays)
        assertEquals(3, edited.waitedDays)
        assertNull(edited.coolingEndsAt)
    }

    @Test
    fun `a price edit down keeps the wait`() {
        val running = item(eur(12_000))
        assertSame(running, waitAfterPriceEdit(running, eur(5_000), rules, utc))
    }

    @Test
    fun `a price edit within the same tier keeps the wait`() {
        val running = item(eur(12_000))
        assertSame(running, waitAfterPriceEdit(running, eur(40_000), rules, utc))
    }

    @Test
    fun `a price edit keeps a wait that is already longer than the new price's`() {
        // Captured under longer tiers, then the user shortened them: the item keeps its 45 days.
        val longer = parked(eur(5_000)).copy(coolingOffDays = 45)
        assertEquals(45, waitAfterPriceEdit(longer, eur(12_000), rules, utc).coolingOffDays)
    }

    @Test
    fun `a price edit on a need keeps its wait`() {
        val need = item(eur(5_000), ItemKind.NEED)
        assertSame(need, waitAfterPriceEdit(need, eur(250_000), rules, utc))
    }

    @Test
    fun `a price edit uses the rules it is given`() {
        val longer = (
            CoolingOffRules.from(listOf(CoolingOffTier(eur(0), Period.ofDays(14)))) as
                CoolingOffRulesResult.Valid
            ).rules
        assertEquals(daysAfterAdded(14), waitAfterPriceEdit(item(eur(5_000)), eur(6_000), longer, utc).coolingEndsAt)
    }

    @Test
    fun `a price edit to another currency is a programming error`() {
        assertThrows(IllegalArgumentException::class.java) {
            waitAfterPriceEdit(item(eur(5_000)), Money(5_000, "USD"), rules, utc)
        }
    }

    private fun tiers(vararg tiers: Pair<Long, Int>): CoolingOffRules = (
        CoolingOffRules.from(tiers.map { (min, days) -> CoolingOffTier(eur(min), Period.ofDays(days)) }) as
            CoolingOffRulesResult.Valid
        ).rules

    @Test
    fun `shorter tiers shorten a running wait`() {
        val shorter = tiers(0L to 3, 10_000L to 10)
        val changed = waitAfterRulesChange(item(eur(12_000)), shorter, daysAfterAdded(5), utc)
        assertEquals(10, changed.coolingOffDays)
        assertEquals(daysAfterAdded(10), changed.coolingEndsAt)
    }

    @Test
    fun `longer tiers extend a running wait`() {
        val longer = tiers(0L to 14, 10_000L to 45)
        assertEquals(
            daysAfterAdded(45),
            waitAfterRulesChange(item(eur(12_000)), longer, daysAfterAdded(5), utc).coolingEndsAt
        )
    }

    @Test
    fun `a running wait is complete at once when its new end has already passed`() {
        val shorter = tiers(0L to 3, 10_000L to 10)
        val now = daysAfterAdded(20)
        val changed = waitAfterRulesChange(item(eur(12_000)), shorter, now, utc)
        assertEquals(daysAfterAdded(10), changed.coolingEndsAt)
        assertTrue(isCooledOff(changed, now))
    }

    @Test
    fun `a tier change counts the time a parked want has already waited, in both directions`() {
        val parked = parked(eur(12_000), waited = 9)
        val shorter = waitAfterRulesChange(parked, tiers(0L to 3, 10_000L to 8), daysAfterAdded(50), utc)
        assertEquals(8, shorter.coolingOffDays)
        assertTrue(isCooledOff(shorter, daysAfterAdded(50)))
        val longer = waitAfterRulesChange(parked, tiers(0L to 14, 10_000L to 45), daysAfterAdded(50), utc)
        assertEquals(45, longer.coolingOffDays)
        assertEquals(9, longer.waitedDays)
        assertFalse(isCooledOff(longer, daysAfterAdded(50)))
    }

    @Test
    fun `a want whose wait is complete keeps it when the tiers get longer`() {
        val longer = tiers(0L to 14, 10_000L to 45)
        val running = item(eur(12_000))
        assertSame(running, waitAfterRulesChange(running, longer, daysAfterAdded(30), utc))
        assertSame(running, waitAfterRulesChange(running, longer, daysAfterAdded(31), utc))
        val waitedOut = parked(eur(12_000), waited = 30)
        assertSame(waitedOut, waitAfterRulesChange(waitedOut, longer, daysAfterAdded(31), utc))
    }

    @Test
    fun `a tier change recomputes from the current price`() {
        // Captured at 120 € (30 days), lowered to 50 € (days kept), then the tiers change: 50 € now means 3 days.
        val lowered = item(eur(12_000)).copy(price = eur(5_000))
        val shorter = tiers(0L to 3, 10_000L to 10)
        assertEquals(daysAfterAdded(3), waitAfterRulesChange(lowered, shorter, daysAfterAdded(1), utc).coolingEndsAt)
    }

    @Test
    fun `a tier change leaves needs and ended items alone`() {
        val longer = tiers(0L to 14, 10_000L to 45)
        val need = item(eur(12_000), ItemKind.NEED)
        assertSame(need, waitAfterRulesChange(need, longer, daysAfterAdded(1), utc))
        for (status in listOf(ItemStatus.PURCHASED, ItemStatus.REJECTED)) {
            val ended = item(eur(12_000)).copy(status = status)
            assertSame(ended, waitAfterRulesChange(ended, longer, daysAfterAdded(1), utc))
        }
    }

    @Test
    fun `a tier change in another currency is a programming error`() {
        assertThrows(IllegalArgumentException::class.java) {
            waitAfterRulesChange(item(eur(12_000)), CoolingOffRules.defaults("USD"), daysAfterAdded(1), utc)
        }
    }

    @Test
    fun `want to need is recorded, leaves the priorities and keeps what it waited`() {
        val at = daysAfterAdded(3)
        val need = changeKind(item(eur(12_000)), ItemKind.NEED, at, rules, utc)
        assertEquals(ItemKind.NEED, need.kind)
        assertEquals(at, need.markedAsNeedAt)
        assertNull(need.prioritySlot)
        assertNull(need.waitingSince)
        assertEquals(3, need.waitedDays)
    }

    @Test
    fun `need to want is parked and keeps the time it had waited as a want`() {
        val need = changeKind(item(eur(12_000)), ItemKind.NEED, daysAfterAdded(3), rules, utc)
        val want = changeKind(need, ItemKind.WANT, daysAfterAdded(9), rules, utc)
        assertEquals(ItemKind.WANT, want.kind)
        assertNull(want.prioritySlot)
        assertNull(want.coolingEndsAt)
        assertEquals(3, want.waitedDays)
        assertNull(want.markedAsNeedAt)
    }

    @Test
    fun `an item captured as a need has waited nothing when it turns into a want`() {
        val want = changeKind(item(eur(12_000), ItemKind.NEED), ItemKind.WANT, daysAfterAdded(3), rules, utc)
        assertEquals(0, want.waitedDays)
        assertEquals(30, want.coolingOffDays)
    }

    @Test
    fun `turning a want into a need and back waits as long as its current price calls for`() {
        // Captured at 1,200 € (90 days), price lowered to 50 € (days kept), then need and back to want: 7 days.
        val lowered = item(eur(120_000)).copy(price = eur(5_000))
        val need = changeKind(lowered, ItemKind.NEED, daysAfterAdded(1), rules, utc)
        val wantAgain = changeKind(need, ItemKind.WANT, daysAfterAdded(2), rules, utc)
        assertEquals(7, wantAgain.coolingOffDays)
    }

    @Test
    fun `changing to the same kind changes nothing`() {
        val want = item(eur(12_000))
        assertSame(want, changeKind(want, ItemKind.WANT, daysAfterAdded(1), rules, utc))
        val need = changeKind(want, ItemKind.NEED, daysAfterAdded(1), rules, utc)
        assertSame(need, changeKind(need, ItemKind.NEED, daysAfterAdded(2), rules, utc))
    }

    @Test
    fun `only active items change kind`() {
        for (status in listOf(ItemStatus.PURCHASED, ItemStatus.REJECTED)) {
            assertThrows(IllegalArgumentException::class.java) {
                changeKind(item(eur(12_000)).copy(status = status), ItemKind.NEED, daysAfterAdded(1), rules, utc)
            }
        }
    }
}
