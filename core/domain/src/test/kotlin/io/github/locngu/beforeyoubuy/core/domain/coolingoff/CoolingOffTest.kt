package io.github.locngu.beforeyoubuy.core.domain.coolingoff

import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import java.time.Instant
import java.time.Period
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class CoolingOffTest {
    private val rules = CoolingOffRules.defaults("EUR")
    private val utc: ZoneId = ZoneOffset.UTC
    private val berlin: ZoneId = ZoneId.of("Europe/Berlin")

    private val added = Instant.parse("2026-10-01T10:00:00Z")

    private fun eur(minorUnits: Long) = Money(minorUnits, "EUR")

    private fun daysAfterAdded(days: Long): Instant = added.plus(days, ChronoUnit.DAYS) // UTC: a day is 24 h

    private fun item(price: Money, kind: ItemKind = ItemKind.WANT) = Item(
        id = ItemId(1),
        name = "Camera",
        price = price,
        addedAt = added,
        coolingEndsAt = initialCoolingEnd(added, price, kind, rules, utc),
        kind = kind
    )

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
    fun `a want cools off from when it was added`() {
        assertEquals(daysAfterAdded(7), initialCoolingEnd(added, eur(5_000), ItemKind.WANT, rules, utc))
        assertEquals(daysAfterAdded(90), initialCoolingEnd(added, eur(250_000), ItemKind.WANT, rules, utc))
    }

    @Test
    fun `a need has no wait`() {
        assertEquals(added, initialCoolingEnd(added, eur(250_000), ItemKind.NEED, rules, utc))
    }

    @Test
    fun `periods are calendar days across the spring daylight-saving change in Berlin`() {
        // 25 March 2026, 10:00 CET; clocks go forward on 29 March, so 7 calendar days are 7 × 24 h minus one hour.
        val addedAt = Instant.parse("2026-03-25T09:00:00Z")
        assertEquals(
            Instant.parse("2026-04-01T08:00:00Z"), // 1 April, 10:00 CEST
            initialCoolingEnd(addedAt, eur(5_000), ItemKind.WANT, rules, berlin)
        )
    }

    @Test
    fun `periods are calendar days across the autumn daylight-saving change in Berlin`() {
        // 20 October 2026, 10:00 CEST; clocks go back on 25 October, so 7 calendar days are 7 × 24 h plus one hour.
        val addedAt = Instant.parse("2026-10-20T08:00:00Z")
        assertEquals(
            Instant.parse("2026-10-27T09:00:00Z"), // 27 October, 10:00 CET
            initialCoolingEnd(addedAt, eur(5_000), ItemKind.WANT, rules, berlin)
        )
    }

    @Test
    fun `an end inside the skipped spring hour moves to just after it`() {
        // 22 March 2026, 02:30 CET; 29 March 02:30 doesn't exist in Berlin, so it ends at 03:30 CEST.
        val addedAt = Instant.parse("2026-03-22T01:30:00Z")
        assertEquals(
            Instant.parse("2026-03-29T01:30:00Z"),
            initialCoolingEnd(addedAt, eur(5_000), ItemKind.WANT, rules, berlin)
        )
    }

    @Test
    fun `a price edit up extends the wait, counted from when the item was added`() {
        assertEquals(daysAfterAdded(30), coolingEndAfterPriceEdit(item(eur(5_000)), eur(12_000), rules, utc))
        assertEquals(daysAfterAdded(90), coolingEndAfterPriceEdit(item(eur(5_000)), eur(100_000), rules, utc))
    }

    @Test
    fun `a price edit down keeps the end`() {
        assertEquals(daysAfterAdded(30), coolingEndAfterPriceEdit(item(eur(12_000)), eur(5_000), rules, utc))
    }

    @Test
    fun `a price edit within the same tier keeps the end`() {
        assertEquals(daysAfterAdded(30), coolingEndAfterPriceEdit(item(eur(12_000)), eur(40_000), rules, utc))
    }

    @Test
    fun `a price edit keeps an end that is already later than the new price's`() {
        // Captured under longer tiers, then the user shortened them: the item keeps its end.
        val extended = item(eur(5_000)).copy(coolingEndsAt = daysAfterAdded(45))
        assertEquals(daysAfterAdded(45), coolingEndAfterPriceEdit(extended, eur(12_000), rules, utc))
    }

    @Test
    fun `a price edit on a need keeps its end`() {
        assertEquals(added, coolingEndAfterPriceEdit(item(eur(5_000), ItemKind.NEED), eur(250_000), rules, utc))
    }

    @Test
    fun `a price edit uses the rules it is given`() {
        val longer = (
            CoolingOffRules.from(listOf(CoolingOffTier(eur(0), Period.ofDays(14)))) as
                CoolingOffRulesResult.Valid
            ).rules
        assertEquals(daysAfterAdded(14), coolingEndAfterPriceEdit(item(eur(5_000)), eur(6_000), longer, utc))
    }

    @Test
    fun `a price edit to another currency is a programming error`() {
        assertThrows(IllegalArgumentException::class.java) {
            coolingEndAfterPriceEdit(item(eur(5_000)), Money(5_000, "USD"), rules, utc)
        }
    }

    @Test
    fun `want to need is recorded and keeps the cooling-off end`() {
        val at = daysAfterAdded(3)
        val need = changeKind(item(eur(12_000)), ItemKind.NEED, at, rules, utc)
        assertEquals(ItemKind.NEED, need.kind)
        assertEquals(at, need.markedAsNeedAt)
        assertEquals(daysAfterAdded(30), need.coolingEndsAt)
    }

    @Test
    fun `need to want cools off from when it was added, like a new want`() {
        val want = changeKind(item(eur(12_000), ItemKind.NEED), ItemKind.WANT, daysAfterAdded(3), rules, utc)
        assertEquals(ItemKind.WANT, want.kind)
        assertEquals(daysAfterAdded(30), want.coolingEndsAt)
        assertNull(want.markedAsNeedAt)
    }

    @Test
    fun `turning a want into a need and back never shortens the wait`() {
        // Captured at 1,200 € (90 days), price lowered to 50 € (end kept), then need and back to want.
        val lowered = item(eur(120_000)).copy(price = eur(5_000))
        val need = changeKind(lowered, ItemKind.NEED, daysAfterAdded(1), rules, utc)
        val wantAgain = changeKind(need, ItemKind.WANT, daysAfterAdded(2), rules, utc)
        assertEquals(daysAfterAdded(90), wantAgain.coolingEndsAt)
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
