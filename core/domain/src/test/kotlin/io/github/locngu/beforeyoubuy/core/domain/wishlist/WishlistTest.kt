package io.github.locngu.beforeyoubuy.core.domain.wishlist

import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
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

    @Test
    fun `groups by derived stage in the order ready, needs reflection, cooling off`() {
        val ready = wish(1, endsAt = Instant.parse("2026-10-10T10:00:00Z"))
        val unreflected = wish(2, endsAt = Instant.parse("2026-10-11T10:00:00Z"))
        val cooling = wish(3, endsAt = Instant.parse("2026-12-01T10:00:00Z"))

        val groups =
            wishlistOf(
                listOf(cooling, unreflected, ready),
                mapOf(ready.id to COMPLETE_REFLECTION),
                Savings.NONE,
                AFTER_COOLING,
                zone
            )

        assertEquals(listOf(Stage.READY, Stage.NEEDS_REFLECTION, Stage.COOLING_OFF), groups.map { it.stage })
        assertEquals(
            listOf(listOf(ready), listOf(unreflected), listOf(cooling)),
            groups.map { g ->
                g.entries.map { it.item }
            }
        )
    }

    @Test
    fun `priorities come first by rank and leave their stage group, which goes once empty`() {
        val second = wish(1).copy(prioritySlot = 2)
        val first = wish(2, endsAt = Instant.parse("2026-12-01T10:00:00Z")).copy(prioritySlot = 1)
        val parked = parkedWish(3)
        val need = wish(4, kind = ItemKind.NEED).copy(prioritySlot = 3)
        val groups = wishlistOf(
            listOf(second, first, parked, need),
            mapOf(second.id to COMPLETE_REFLECTION, parked.id to COMPLETE_REFLECTION),
            Savings.NONE,
            AFTER_COOLING,
            zone
        )

        val sections = wishlistSectionsOf(groups)

        assertEquals(listOf(1 to first, 2 to second), sections.priorities.map { it.rank to it.entry.item })
        assertEquals(Stage.COOLING_OFF, sections.priorities[0].entry.stage)
        // A need with a stored slot isn't a priority (ADR-0047); the cooling-off and needs-reflection groups had only
        // priorities and are gone.
        assertEquals(listOf(Stage.READY, Stage.PARKED), sections.groups.map { it.stage })
        assertEquals(listOf(listOf(need), listOf(parked)), sections.groups.map { g -> g.entries.map { it.item } })
        assertEquals(sections.priorities, prioritiesIn(groups))
    }

    @Test
    fun `without priorities the sections are the stage groups`() {
        val items = listOf(parkedWish(1), wish(2, kind = ItemKind.NEED))
        val groups = wishlistOf(items, emptyMap(), Savings.NONE, AFTER_COOLING, zone)

        assertEquals(WishlistSections(emptyList(), groups), wishlistSectionsOf(groups))
    }

    @Test
    fun `parked wants come last, after the waiting group, oldest first`() {
        val newer = parkedWish(1).copy(addedAt = ADDED.plusSeconds(60))
        val older = parkedWish(2)
        val waiting = wish(3, endsAt = Instant.parse("2026-10-20T10:00:00Z"))
            .copy(recheckAt = Instant.parse("2026-11-10T00:00:00Z"))
        val ready = wish(4, endsAt = Instant.parse("2026-10-20T10:00:00Z"))
        val answered = listOf(waiting, ready, newer, older).associate { it.id to COMPLETE_REFLECTION }

        val groups = wishlistOf(listOf(newer, waiting, older, ready), answered, Savings.NONE, AFTER_COOLING, zone)

        assertEquals(listOf(Stage.READY, Stage.READY, Stage.PARKED), groups.map { it.stage })
        assertEquals(listOf(false, true, false), groups.map { it.waiting })
        assertEquals(listOf(older, newer), groups.last().entries.map { it.item })
    }

    @Test
    fun `a parked want is never ready, however long it has waited, and its days don't count down`() {
        val waitedOut = parkedWish(1, waitedDays = 40)
        val halfway = parkedWish(2, waitedDays = 12)
        val answered = mapOf(waitedOut.id to COMPLETE_REFLECTION, halfway.id to COMPLETE_REFLECTION)

        val entries = wishlistOf(listOf(waitedOut, halfway), answered, Savings.NONE, AFTER_COOLING, zone)
            .flatMap { it.entries }

        assertEquals(listOf(Stage.PARKED, Stage.PARKED), entries.map { it.stage })
        assertEquals(listOf(0, 18), entries.map { it.daysLeft })
        assertEquals(18, daysLeft(halfway, AFTER_COOLING.plus(Duration.ofDays(9)), zone))
    }

    @Test
    fun `a parked want doesn't set the next change`() {
        val now = Instant.parse("2026-10-15T10:00:00Z")

        assertEquals(Duration.ofHours(14), untilWishlistChanges(listOf(parkedWish(1)), now, zone))
    }

    @Test
    fun `every entry carries the stage stageOf gives it`() {
        val items = listOf(wish(1), wish(2, endsAt = Instant.parse("2026-12-01T10:00:00Z")))

        val entries = wishlistOf(items, emptyMap(), Savings.NONE, AFTER_COOLING, zone).flatMap { it.entries }

        assertEquals(listOf(Stage.NEEDS_REFLECTION, Stage.COOLING_OFF), entries.map { it.stage })
    }

    @Test
    fun `empty groups are left out`() {
        assertEquals(
            emptyList<WishlistGroup>(),
            wishlistOf(emptyList(), emptyMap(), Savings.NONE, DURING_COOLING, zone)
        )
        assertEquals(
            listOf(Stage.COOLING_OFF),
            wishlistOf(listOf(wish(1)), emptyMap(), Savings.NONE, DURING_COOLING, zone).map {
                it.stage
            }
        )
    }

    @Test
    fun `soonest cooling end first, ties by age then id`() {
        val later = wish(1, endsAt = Instant.parse("2026-12-01T10:00:00Z"))
        val sooner = wish(2, endsAt = Instant.parse("2026-11-01T10:00:00Z"))
        val tieOlder = wish(
            4,
            endsAt = Instant.parse("2026-11-01T10:00:00Z")
        ).copy(addedAt = sooner.addedAt.minusSeconds(60))
        val tieHigherId = wish(3, endsAt = Instant.parse("2026-11-01T10:00:00Z"))

        val order =
            wishlistOf(listOf(later, tieHigherId, sooner, tieOlder), emptyMap(), Savings.NONE, DURING_COOLING, zone)
                .single()
                .entries
                .map { it.item.id.value }

        assertEquals(listOf(4L, 2L, 3L, 1L), order)
    }

    @Test
    fun `purchased and rejected items are not listed`() {
        val items =
            listOf(wish(1), wish(2).copy(status = ItemStatus.PURCHASED), wish(3).copy(status = ItemStatus.REJECTED))

        assertEquals(
            listOf(ItemId(1)),
            wishlistOf(items, emptyMap(), Savings.NONE, DURING_COOLING, zone).flatMap {
                it.entries
            }.map { it.item.id }
        )
    }

    @Test
    fun `a need is never cooling off and needs no reflection`() {
        val need = wish(1, kind = ItemKind.NEED)

        assertEquals(
            Stage.READY,
            wishlistOf(listOf(need), emptyMap(), Savings.NONE, DURING_COOLING, zone).single().stage
        )
    }

    @Test
    fun `an item changes group when its cooling off ends`() {
        val items = listOf(wish(1))

        assertEquals(
            Stage.COOLING_OFF,
            wishlistOf(items, emptyMap(), Savings.NONE, COOLING_ENDS.minusMillis(1), zone).single().stage
        )
        assertEquals(
            Stage.NEEDS_REFLECTION,
            wishlistOf(items, emptyMap(), Savings.NONE, COOLING_ENDS, zone).single().stage
        )
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
    fun `a ready item waiting for its re-check date is in a last, waiting group until then`() {
        val recheck = Instant.parse("2026-11-09T00:00:00Z")
        val waited = wish(1, endsAt = Instant.parse("2026-10-10T10:00:00Z")).copy(recheckAt = recheck)
        val ready = wish(2, endsAt = Instant.parse("2026-10-11T10:00:00Z"))
        val cooling = wish(3, endsAt = Instant.parse("2026-12-01T10:00:00Z"))
        val reflections = mapOf(waited.id to COMPLETE_REFLECTION, ready.id to COMPLETE_REFLECTION)

        val groups = wishlistOf(listOf(waited, ready, cooling), reflections, Savings.NONE, AFTER_COOLING, zone)

        assertEquals(listOf(false, false, true), groups.map { it.waiting })
        assertEquals(listOf(Stage.READY, Stage.COOLING_OFF, Stage.READY), groups.map { it.stage })
        assertEquals(listOf(ready.id), groups.first().entries.map { it.item.id })
        val waiting = groups.last().entries.single()
        assertEquals(waited.id, waiting.item.id)
        assertEquals(LocalDate.of(2026, 11, 9), waiting.recheckOn)

        val due = wishlistOf(listOf(waited), reflections, Savings.NONE, recheck, zone).single()
        assertFalse(due.waiting)
        assertNull(due.entries.single().recheckOn)
    }

    @Test
    fun `waiting items come soonest re-check first`() {
        val later = wish(1).copy(recheckAt = Instant.parse("2026-11-20T00:00:00Z"))
        val sooner = wish(2).copy(recheckAt = Instant.parse("2026-11-10T00:00:00Z"))
        val reflections = mapOf(later.id to COMPLETE_REFLECTION, sooner.id to COMPLETE_REFLECTION)

        val group = wishlistOf(listOf(later, sooner), reflections, Savings.NONE, AFTER_COOLING, zone).single()

        assertEquals(listOf(sooner.id, later.id), group.entries.map { it.item.id })
    }

    @Test
    fun `a re-check date only matters once the item is ready`() {
        val waited = wish(1).copy(recheckAt = Instant.parse("2026-11-20T00:00:00Z"))

        val group = wishlistOf(listOf(waited), emptyMap(), Savings.NONE, AFTER_COOLING, zone).single()

        assertEquals(Stage.NEEDS_REFLECTION, group.stage)
        assertFalse(group.waiting)
        assertNull(group.entries.single().recheckOn)
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
