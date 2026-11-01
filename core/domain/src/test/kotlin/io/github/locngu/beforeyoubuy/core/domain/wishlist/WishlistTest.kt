package io.github.locngu.beforeyoubuy.core.domain.wishlist

import io.github.locngu.beforeyoubuy.core.domain.coolingoff.WaitProgress
import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.item.Reflection
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.ADDED
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.AFTER_COOLING
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.COMPLETE_REFLECTION
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.COOLING_ENDS
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.DURING_COOLING
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.Stage
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.item
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.parked
import io.github.locngu.beforeyoubuy.core.domain.pool.Savings
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WishlistTest {
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

    private fun list(
        items: List<Item>,
        reflections: Map<ItemId, Reflection> = emptyMap(),
        now: Instant = AFTER_COOLING
    ) = wishlistOf(items, reflections, Savings.NONE, now, zone)

    @Test
    fun `priorities by rank, then needs, then parked wants, each with no other grouping`() {
        val second = wish(1).copy(prioritySlot = 2)
        val first = wish(2, endsAt = Instant.parse("2026-12-01T10:00:00Z")).copy(prioritySlot = 1)
        val parked = parkedWish(3)
        val need = wish(4, kind = ItemKind.NEED).copy(prioritySlot = 3)

        val wishlist = list(
            listOf(second, first, parked, need),
            mapOf(second.id to COMPLETE_REFLECTION, parked.id to COMPLETE_REFLECTION)
        )

        assertEquals(listOf(1 to first, 2 to second), wishlist.priorities.map { it.rank to it.entry.item })
        assertEquals(listOf(Stage.COOLING_OFF, Stage.READY), wishlist.priorities.map { it.entry.stage })
        // A need with a stored slot isn't a priority (ADR-0047).
        assertEquals(listOf(need), wishlist.needs.map { it.item })
        assertEquals(listOf(parked), wishlist.parked.map { it.item })
        assertEquals(listOf(first, second, need, parked), wishlist.entries.map { it.item })
    }

    @Test
    fun `every stage of a priority stays in the priorities, a waiting one too`() {
        val ready = wish(1, endsAt = Instant.parse("2026-10-10T10:00:00Z")).copy(prioritySlot = 1)
        val unreflected = wish(2, endsAt = Instant.parse("2026-10-11T10:00:00Z")).copy(prioritySlot = 2)
        val cooling = wish(3, endsAt = Instant.parse("2026-12-01T10:00:00Z")).copy(prioritySlot = 3)
        val waiting = wish(4, endsAt = Instant.parse("2026-10-10T10:00:00Z"))
            .copy(prioritySlot = 4, recheckAt = Instant.parse("2026-11-10T00:00:00Z"))
        val answered = listOf(ready, waiting).associate { it.id to COMPLETE_REFLECTION }

        val wishlist = list(listOf(cooling, waiting, unreflected, ready), answered)

        assertEquals(
            listOf(Stage.READY, Stage.NEEDS_REFLECTION, Stage.COOLING_OFF, Stage.READY),
            wishlist.priorities.map { it.entry.stage }
        )
        assertEquals(LocalDate.of(2026, 11, 10), wishlist.priorities[3].entry.recheckOn)
        assertTrue(wishlist.needs.isEmpty() && wishlist.parked.isEmpty())
    }

    @Test
    fun `needs and parked wants come oldest first, ties by id`() {
        val newer = parkedWish(1).copy(addedAt = ADDED.plusSeconds(60))
        val older = parkedWish(3)
        val tie = parkedWish(2)
        val laterNeed = wish(4, kind = ItemKind.NEED).copy(addedAt = ADDED.plusSeconds(60))
        val need = wish(5, kind = ItemKind.NEED)

        val wishlist = list(listOf(newer, laterNeed, older, need, tie))

        assertEquals(listOf(tie, older, newer), wishlist.parked.map { it.item })
        assertEquals(listOf(need, laterNeed), wishlist.needs.map { it.item })
        assertEquals(listOf(Stage.READY, Stage.READY), wishlist.needs.map { it.stage })
    }

    @Test
    fun `a parked want is never ready, however long it has waited, and its days don't count down`() {
        val waitedOut = parkedWish(1, waitedDays = 40)
        val halfway = parkedWish(2, waitedDays = 12)
        val answered = mapOf(waitedOut.id to COMPLETE_REFLECTION, halfway.id to COMPLETE_REFLECTION)

        val entries = list(listOf(waitedOut, halfway), answered).parked

        assertEquals(listOf(Stage.PARKED, Stage.PARKED), entries.map { it.stage })
        assertEquals(listOf(0, 18), entries.map { it.daysLeft })
        assertEquals(18, daysLeft(halfway, AFTER_COOLING.plus(Duration.ofDays(9)), zone))
    }

    @Test
    fun `only a parked want shows how much of its wait is done`() {
        val notStarted = parkedWish(1)
        val halfway = parkedWish(2, waitedDays = 12)
        val waitedOut = parkedWish(3, waitedDays = 30)

        val wishlist = list(listOf(notStarted, halfway, waitedOut, wish(4), wish(5, kind = ItemKind.NEED)))

        assertEquals(
            listOf(WaitProgress.NotStarted(30), WaitProgress.Partly(12, 30), WaitProgress.Done),
            wishlist.parked.map { it.waitProgress }
        )
        assertNull(wishlist.priorities.single().entry.waitProgress)
        assertNull(wishlist.needs.single().waitProgress)
    }

    @Test
    fun `a parked want doesn't set the next change`() {
        val now = Instant.parse("2026-10-15T10:00:00Z")

        assertEquals(Duration.ofHours(14), untilWishlistChanges(listOf(parkedWish(1)), now, zone))
    }

    @Test
    fun `every entry carries the stage stageOf gives it`() {
        val items =
            listOf(
                wish(1).copy(prioritySlot = 1),
                wish(2, endsAt = Instant.parse("2026-12-01T10:00:00Z")).copy(prioritySlot = 2)
            )

        val entries = list(items).entries

        assertEquals(listOf(Stage.NEEDS_REFLECTION, Stage.COOLING_OFF), entries.map { it.stage })
    }

    @Test
    fun `without items the wishlist is empty`() {
        assertEquals(Wishlist.EMPTY, list(emptyList(), now = DURING_COOLING))
        assertTrue(Wishlist.EMPTY.isEmpty())
        assertFalse(list(listOf(parkedWish(1))).isEmpty())
    }

    @Test
    fun `purchased and rejected items are not listed`() {
        val items =
            listOf(wish(1), wish(2).copy(status = ItemStatus.PURCHASED), wish(3).copy(status = ItemStatus.REJECTED))

        assertEquals(listOf(ItemId(1)), list(items, now = DURING_COOLING).entries.map { it.item.id })
    }

    @Test
    fun `a need is never cooling off and needs no reflection`() {
        val need = wish(1, kind = ItemKind.NEED)

        assertEquals(Stage.READY, list(listOf(need), now = DURING_COOLING).needs.single().stage)
    }

    @Test
    fun `a priority changes stage when its cooling off ends`() {
        val items = listOf(wish(1))

        assertEquals(
            Stage.COOLING_OFF,
            list(items, now = COOLING_ENDS.minusMillis(1)).priorities.single().entry.stage
        )
        assertEquals(Stage.NEEDS_REFLECTION, list(items, now = COOLING_ENDS).priorities.single().entry.stage)
    }

    @Test
    fun `days left counts calendar dates, at least 1 until the end`() {
        val item = wish(1, endsAt = Instant.parse("2026-10-31T10:00:00Z"))

        assertEquals(16, daysLeft(item, Instant.parse("2026-10-15T23:59:00Z"), zone))
        assertEquals(1, daysLeft(item, Instant.parse("2026-10-30T23:59:00Z"), zone))
        assertEquals(1, daysLeft(item, Instant.parse("2026-10-31T09:59:00Z"), zone))
        assertEquals(0, daysLeft(item, COOLING_ENDS, zone))
        assertEquals(0, daysLeft(item, AFTER_COOLING, zone))
    }

    @Test
    fun `days left follows the calendar across a daylight saving change`() {
        val berlin = ZoneId.of("Europe/Berlin")
        // Clocks go back on 2026-10-25; 48 hours would be 2 days, but the dates are 2 apart either way.
        val item = wish(1, endsAt = Instant.parse("2026-10-26T09:00:00Z"))

        assertEquals(2, daysLeft(item, Instant.parse("2026-10-24T09:00:00Z"), berlin))
    }

    @Test
    fun `days left uses the zone's date, not UTC's`() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        val item = wish(1, endsAt = Instant.parse("2026-10-31T20:00:00Z")) // 05:00 on 1 Nov in Tokyo

        // 23:30 UTC on 30 Oct is already 31 Oct in Tokyo, one date before the end.
        assertEquals(1, daysLeft(item, Instant.parse("2026-10-30T23:30:00Z"), tokyo))
    }

    @Test
    fun `changes at the next cooling end when that is before midnight`() {
        val now = Instant.parse("2026-10-15T10:00:00Z")
        val items = listOf(wish(1, endsAt = Instant.parse("2026-10-15T12:00:00Z")), wish(2))

        assertEquals(Duration.ofHours(2), untilWishlistChanges(items, now, zone))
    }

    @Test
    fun `changes at midnight when no cooling end comes sooner`() {
        val now = Instant.parse("2026-10-15T10:00:00Z")

        assertEquals(Duration.ofHours(14), untilWishlistChanges(listOf(wish(1)), now, zone))
        assertEquals(Duration.ofHours(14), untilWishlistChanges(emptyList(), now, zone))
    }

    @Test
    fun `ended and inactive items don't set the next change`() {
        val now = Instant.parse("2026-10-15T10:00:00Z")
        val items =
            listOf(
                wish(1, endsAt = Instant.parse("2026-10-15T09:00:00Z")),
                wish(2, endsAt = Instant.parse("2026-10-15T11:00:00Z")).copy(status = ItemStatus.REJECTED)
            )

        assertTrue(untilWishlistChanges(items, now, zone) == Duration.ofHours(14))
    }

    @Test
    fun `a need doesn't set the next change`() {
        val now = Instant.parse("2026-10-15T10:00:00Z")
        val need = wish(1, kind = ItemKind.NEED)

        assertEquals(Duration.ofHours(14), untilWishlistChanges(listOf(need), now, zone))
    }

    @Test
    fun `a ready priority waiting for its re-check date carries the date until then`() {
        val recheck = Instant.parse("2026-11-09T00:00:00Z")
        val waited = wish(1, endsAt = Instant.parse("2026-10-10T10:00:00Z")).copy(recheckAt = recheck)
        val reflections = mapOf(waited.id to COMPLETE_REFLECTION)

        val waiting = list(listOf(waited), reflections).priorities.single().entry
        assertEquals(Stage.READY, waiting.stage)
        assertEquals(LocalDate.of(2026, 11, 9), waiting.recheckOn)

        assertNull(list(listOf(waited), reflections, now = recheck).priorities.single().entry.recheckOn)
    }

    @Test
    fun `a re-check date only matters once the item is ready`() {
        val waited = wish(1).copy(recheckAt = Instant.parse("2026-11-20T00:00:00Z"))

        val entry = list(listOf(waited)).priorities.single().entry

        assertEquals(Stage.NEEDS_REFLECTION, entry.stage)
        assertNull(entry.recheckOn)
    }

    @Test
    fun `changes at the next re-check date when that comes first`() {
        val now = Instant.parse("2026-11-09T10:00:00Z")
        val waited = wish(1).copy(recheckAt = Instant.parse("2026-11-09T12:00:00Z"))
        val passed = wish(2).copy(recheckAt = Instant.parse("2026-11-09T09:00:00Z"))

        assertEquals(Duration.ofHours(2), untilWishlistChanges(listOf(waited, passed), now, zone))
        assertEquals(Duration.ofHours(14), untilWishlistChanges(listOf(passed), now, zone))
    }
}
