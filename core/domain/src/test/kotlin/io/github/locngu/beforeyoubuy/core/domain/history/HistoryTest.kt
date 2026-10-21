package io.github.locngu.beforeyoubuy.core.domain.history

import io.github.locngu.beforeyoubuy.core.domain.coolingoff.CoolingOffRules
import io.github.locngu.beforeyoubuy.core.domain.coolingoff.initialCoolingEnd
import io.github.locngu.beforeyoubuy.core.domain.item.Decision
import io.github.locngu.beforeyoubuy.core.domain.item.DecisionType
import io.github.locngu.beforeyoubuy.core.domain.item.HistoryEntry
import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.ADDED
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.COMPLETE_REFLECTION
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.PRICE
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.item
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.core.domain.settings.Policy
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryTest {
    private val zone: ZoneId = ZoneOffset.UTC

    /** 2026-11-17, well after [ADDED] (2026-10-01). */
    private val now: Instant = Instant.parse("2026-11-17T09:00:00Z")

    private fun ended(id: Long, status: ItemStatus, price: Money = PRICE, kind: ItemKind = ItemKind.WANT) =
        item(kind).copy(id = ItemId(id), name = "Item $id", price = price, status = status)

    private fun bought(
        id: Long,
        at: Instant,
        paid: Money = PRICE,
        type: DecisionType = DecisionType.BUY,
        price: Money = PRICE
    ) = HistoryEntry(
        ended(id, ItemStatus.PURCHASED, price),
        Decision(type, at, actualPrice = paid, earlyReason = "Broke".takeIf { type == DecisionType.BUY_EARLY })
    )

    private fun rejected(id: Long, at: Instant, price: Money = PRICE, note: String? = null) =
        HistoryEntry(ended(id, ItemStatus.REJECTED, price), Decision(DecisionType.REJECT, at, note = note))

    private fun eur(euros: Long) = Money(euros * 100, "EUR")

    @Test
    fun `bought shows what was paid, the wishlist price only where it differs, the date and days considered`() {
        val boughtAt = Instant.parse("2026-11-17T08:00:00Z")
        val entries = listOf(bought(1, boughtAt, paid = eur(110)), bought(2, boughtAt, paid = PRICE))

        val (cheaper, same) = boughtOf(entries, "EUR", HistoryPeriod.ALL, now, zone)

        assertEquals(eur(110), cheaper.paid)
        assertEquals(PRICE, cheaper.wishlistPrice)
        assertNull(same.wishlistPrice)
        assertEquals(LocalDate.of(2026, 11, 17), cheaper.boughtOn)
        // 2026-10-01 to 2026-11-17 in calendar days.
        assertEquals(47, cheaper.consideredForDays)
    }

    @Test
    fun `bought on the day it was added was considered for 0 days`() {
        val entry = bought(1, ADDED.plusSeconds(60))

        assertEquals(0, boughtOf(listOf(entry), "EUR", HistoryPeriod.ALL, now, zone).single().consideredForDays)
    }

    @Test
    fun `days considered are calendar days in the zone`() {
        // Added 2026-10-01T10:00Z, which is still 2026-10-01 in Los Angeles; bought at 05:00Z on 2026-10-03,
        // which there is the evening of 2026-10-02.
        val losAngeles = ZoneId.of("America/Los_Angeles")
        val entry = bought(1, Instant.parse("2026-10-03T05:00:00Z"))

        val listed = boughtOf(listOf(entry), "EUR", HistoryPeriod.ALL, now, losAngeles).single()

        assertEquals(1, listed.consideredForDays)
        assertEquals(LocalDate.of(2026, 10, 2), listed.boughtOn)
    }

    @Test
    fun `bought flags early purchases and needs`() {
        val at = Instant.parse("2026-11-10T10:00:00Z")
        val entries = listOf(
            bought(1, at, type = DecisionType.BUY),
            bought(2, at, type = DecisionType.BUY_EARLY),
            bought(3, at, type = DecisionType.BUY_NEED)
        )

        val listed = boughtOf(entries, "EUR", HistoryPeriod.ALL, now, zone)

        assertEquals(listOf(false, true, false), listed.map { it.early })
        assertEquals(listOf(false, false, true), listed.map { it.need })
    }

    @Test
    fun `decided against lists price, date and note, and totals the prices exactly`() {
        val entries = listOf(
            rejected(1, Instant.parse("2026-11-16T10:00:00Z"), price = Money(1_950, "EUR"), note = "Not now"),
            rejected(2, Instant.parse("2026-11-15T10:00:00Z"), price = Money(1_950, "EUR"))
        )

        val decided = decidedAgainstOf(entries, "EUR", HistoryPeriod.ALL, now, zone)

        assertEquals(listOf(ItemId(1), ItemId(2)), decided.entries.map { it.item.id })
        assertEquals(listOf("Not now", null), decided.entries.map { it.note })
        assertEquals(LocalDate.of(2026, 11, 16), decided.entries.first().decidedOn)
        // 19,50 € + 19,50 €: one exact sum, shown as 39 €, never 20 € + 20 € (ADR-0034).
        assertEquals(Money(3_900, "EUR"), decided.total)
    }

    @Test
    fun `nothing decided against totals zero in the app's currency`() {
        val decided = decidedAgainstOf(emptyList(), "EUR", HistoryPeriod.ALL, now, zone)

        assertEquals(emptyList<DecidedAgainstEntry>(), decided.entries)
        assertEquals(Money(0, "EUR"), decided.total)
    }

    @Test
    fun `every amount must be in the app's currency`() {
        val at = Instant.parse("2026-11-10T10:00:00Z")
        val dollars = Money(12_000, "USD")

        assertThrows(IllegalArgumentException::class.java) {
            decidedAgainstOf(
                listOf(rejected(1, at), rejected(2, at, price = dollars)),
                "EUR",
                HistoryPeriod.ALL,
                now,
                zone
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            boughtOf(listOf(bought(1, at, paid = dollars, price = dollars)), "EUR", HistoryPeriod.ALL, now, zone)
        }
        assertThrows(IllegalArgumentException::class.java) {
            boughtOf(listOf(bought(1, at, paid = dollars)), "EUR", HistoryPeriod.ALL, now, zone)
        }
        assertTrue(allInCurrency(listOf(rejected(1, at), bought(2, at)), "EUR"))
        assertFalse(allInCurrency(listOf(rejected(1, at, price = dollars)), "EUR"))
        assertFalse(allInCurrency(listOf(bought(1, at, paid = dollars)), "EUR"))
    }

    @Test
    fun `entries that don't fit their list are left out`() {
        val at = Instant.parse("2026-11-10T10:00:00Z")
        val rejectedItemBought = HistoryEntry(ended(1, ItemStatus.REJECTED), bought(1, at).decision)
        val purchasedItemRejected = HistoryEntry(ended(2, ItemStatus.PURCHASED), rejected(2, at).decision)

        assertEquals(
            emptyList<BoughtEntry>(),
            boughtOf(listOf(rejectedItemBought, purchasedItemRejected), "EUR", HistoryPeriod.ALL, now, zone)
        )
        assertEquals(
            emptyList<DecidedAgainstEntry>(),
            decidedAgainstOf(listOf(rejectedItemBought, purchasedItemRejected), "EUR", HistoryPeriod.ALL, now, zone)
                .entries
        )
    }

    @Test
    fun `this month and this year are calendar periods of the decision date`() {
        val today = LocalDate.of(2026, 11, 17)

        assertTrue(HistoryPeriod.THIS_MONTH.includes(LocalDate.of(2026, 11, 1), today))
        assertFalse(HistoryPeriod.THIS_MONTH.includes(LocalDate.of(2026, 10, 31), today))
        assertFalse(HistoryPeriod.THIS_MONTH.includes(LocalDate.of(2025, 11, 17), today))
        assertTrue(HistoryPeriod.THIS_YEAR.includes(LocalDate.of(2026, 1, 1), today))
        assertFalse(HistoryPeriod.THIS_YEAR.includes(LocalDate.of(2025, 12, 31), today))
        assertTrue(HistoryPeriod.ALL.includes(LocalDate.of(2001, 1, 1), today))
    }

    @Test
    fun `a period filters both lists and the total covers only what's listed`() {
        val entries = listOf(
            rejected(1, Instant.parse("2026-11-02T10:00:00Z"), price = eur(100)),
            rejected(2, Instant.parse("2026-10-30T10:00:00Z"), price = eur(40)),
            rejected(3, Instant.parse("2025-12-31T10:00:00Z"), price = eur(7))
        )

        val month = decidedAgainstOf(entries, "EUR", HistoryPeriod.THIS_MONTH, now, zone)
        val year = decidedAgainstOf(entries, "EUR", HistoryPeriod.THIS_YEAR, now, zone)
        val all = decidedAgainstOf(entries, "EUR", HistoryPeriod.ALL, now, zone)

        assertEquals(listOf(ItemId(1)) to eur(100), month.entries.map { it.item.id } to month.total)
        assertEquals(listOf(ItemId(1), ItemId(2)) to eur(140), year.entries.map { it.item.id } to year.total)
        assertEquals(eur(147), all.total)
        val boughtThisMonth = boughtOf(
            listOf(bought(4, Instant.parse("2026-11-01T00:00:00Z")), bought(5, Instant.parse("2026-10-31T23:59:59Z"))),
            "EUR",
            HistoryPeriod.THIS_MONTH,
            now,
            zone
        )
        assertEquals(listOf(ItemId(4)), boughtThisMonth.map { it.item.id })
    }

    @Test
    fun `the period follows the zone - a decision late on the last of October in Berlin is October`() {
        val berlin = ZoneId.of("Europe/Berlin")
        // 22:30Z on 31 October is 23:30 in Berlin (CET, the clocks went back on 25 October): still October there.
        val entry = rejected(1, Instant.parse("2026-10-31T22:30:00Z"))
        val firstOfNovember = Instant.parse("2026-11-01T09:00:00Z")

        assertEquals(
            emptyList<DecidedAgainstEntry>(),
            decidedAgainstOf(listOf(entry), "EUR", HistoryPeriod.THIS_MONTH, firstOfNovember, berlin).entries
        )
        // In UTC+14 the same instant is already 1 November.
        val kiritimati = ZoneId.of("Pacific/Kiritimati")
        assertEquals(
            1,
            decidedAgainstOf(listOf(entry), "EUR", HistoryPeriod.THIS_MONTH, firstOfNovember, kiritimati).entries.size
        )
    }

    @Test
    fun `history changes at the next midnight in the zone`() {
        assertEquals(Duration.ofHours(15), untilHistoryChanges(now, zone))
    }

    @Test
    fun `detail is the bought or decided-against entry with the answers, by the last decision`() {
        val at = Instant.parse("2026-11-10T10:00:00Z")
        val waited = Decision(DecisionType.WAIT, ADDED.plusSeconds(60), waitUntil = ADDED.plusSeconds(86_400))
        val (purchasedItem, purchase) = bought(1, at)
        val (rejectedItem, reject) = rejected(2, at, note = "Too big")

        val boughtDetail = historyDetailOf(purchasedItem, listOf(waited, purchase), COMPLETE_REFLECTION, zone)
        val rejectedDetail = historyDetailOf(rejectedItem, listOf(reject), null, zone)

        assertEquals(
            HistoryDetail.Bought(
                boughtOf(listOf(bought(1, at)), "EUR", HistoryPeriod.ALL, now, zone).single(),
                COMPLETE_REFLECTION
            ),
            boughtDetail
        )
        assertEquals("Too big", (rejectedDetail as HistoryDetail.DecidedAgainst).entry.note)
        assertNull(rejectedDetail.reflection)
    }

    @Test
    fun `an active item or one without a fitting ending decision has no history detail`() {
        val at = Instant.parse("2026-11-10T10:00:00Z")
        val (_, reject) = rejected(1, at)

        assertNull(historyDetailOf(item(), listOf(reject), null, zone))
        assertNull(historyDetailOf(ended(1, ItemStatus.REJECTED), emptyList(), null, zone))
        assertNull(historyDetailOf(ended(1, ItemStatus.PURCHASED), listOf(reject), null, zone))
    }

    @Test
    fun `wanted again is a new want with the same facts that cools off from now, without answers`() {
        val policy = Policy("EUR", CoolingOffRules.defaults("EUR"))
        val old = ended(1, ItemStatus.REJECTED).copy(
            link = "https://shop.example/camera",
            imageRef = "photo.jpg",
            category = "Hobby",
            notes = "The silver one",
            targetDate = LocalDate.of(2026, 12, 24),
            recheckAt = Instant.parse("2026-11-01T00:00:00Z"),
            prioritySlot = 2
        )

        val again = wantedAgain(old, policy, now, zone)

        assertEquals(
            Item(
                id = ItemId.NEW,
                name = old.name,
                price = old.price,
                addedAt = now,
                coolingEndsAt = initialCoolingEnd(now, old.price, ItemKind.WANT, policy.coolingOffRules, zone),
                link = "https://shop.example/camera",
                imageRef = "photo.jpg",
                category = "Hobby",
                notes = "The silver one",
                targetDate = LocalDate.of(2026, 12, 24)
            ),
            again
        )
        assertTrue(again.coolingEndsAt > now)
    }

    @Test
    fun `a need decided against comes back as a want, and a past target date is dropped`() {
        val policy = Policy("EUR", CoolingOffRules.defaults("EUR"))
        val old = ended(1, ItemStatus.REJECTED, kind = ItemKind.NEED)
            .copy(markedAsNeedAt = ADDED, targetDate = LocalDate.of(2026, 11, 17))

        val again = wantedAgain(old, policy, now, zone)

        assertEquals(ItemKind.WANT, again.kind)
        assertNull(again.markedAsNeedAt)
        assertNull(again.targetDate)
        assertTrue(again.coolingEndsAt > now)
    }

    @Test
    fun `only decided-against items in the app's currency can be wanted again`() {
        val policy = Policy("EUR", CoolingOffRules.defaults("EUR"))

        assertThrows(IllegalArgumentException::class.java) { wantedAgain(item(), policy, now, zone) }
        assertThrows(IllegalArgumentException::class.java) {
            wantedAgain(ended(1, ItemStatus.PURCHASED), policy, now, zone)
        }
        assertThrows(IllegalArgumentException::class.java) {
            wantedAgain(ended(1, ItemStatus.REJECTED, price = Money(12_000, "USD")), policy, now, zone)
        }
    }
}
