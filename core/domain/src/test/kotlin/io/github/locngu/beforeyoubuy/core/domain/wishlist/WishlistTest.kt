package io.github.locngu.beforeyoubuy.core.domain.wishlist

import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.AFTER_COOLING
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.COMPLETE_REFLECTION
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.COOLING_ENDS
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.DURING_COOLING
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.Stage
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.item
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WishlistTest {
    private val zone: ZoneId = ZoneOffset.UTC

    private fun wish(id: Long, endsAt: Instant = COOLING_ENDS, kind: ItemKind = ItemKind.WANT): Item =
        item(kind).copy(id = ItemId(id), coolingEndsAt = endsAt)

    @Test
    fun `groups by derived stage in the order ready, needs reflection, cooling off`() {
        val ready = wish(1, endsAt = Instant.parse("2026-10-10T10:00:00Z"))
        val unreflected = wish(2, endsAt = Instant.parse("2026-10-11T10:00:00Z"))
        val cooling = wish(3, endsAt = Instant.parse("2026-12-01T10:00:00Z"))

        val groups =
            wishlistOf(listOf(cooling, unreflected, ready), mapOf(ready.id to COMPLETE_REFLECTION), AFTER_COOLING, zone)

        assertEquals(listOf(Stage.READY, Stage.NEEDS_REFLECTION, Stage.COOLING_OFF), groups.map { it.stage })
        assertEquals(
            listOf(listOf(ready), listOf(unreflected), listOf(cooling)),
            groups.map { g ->
                g.entries.map { it.item }
            }
        )
    }

    @Test
    fun `every entry carries the stage stageOf gives it`() {
        val items = listOf(wish(1), wish(2, endsAt = Instant.parse("2026-12-01T10:00:00Z")))

        val entries = wishlistOf(items, emptyMap(), AFTER_COOLING, zone).flatMap { it.entries }

        assertEquals(listOf(Stage.NEEDS_REFLECTION, Stage.COOLING_OFF), entries.map { it.stage })
    }

    @Test
    fun `empty groups are left out`() {
        assertEquals(emptyList<WishlistGroup>(), wishlistOf(emptyList(), emptyMap(), DURING_COOLING, zone))
        assertEquals(
            listOf(Stage.COOLING_OFF),
            wishlistOf(listOf(wish(1)), emptyMap(), DURING_COOLING, zone).map {
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
            wishlistOf(listOf(later, tieHigherId, sooner, tieOlder), emptyMap(), DURING_COOLING, zone)
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
            wishlistOf(items, emptyMap(), DURING_COOLING, zone).flatMap {
                it.entries
            }.map { it.item.id }
        )
    }

    @Test
    fun `a need is never cooling off and needs no reflection`() {
        val need = wish(1, kind = ItemKind.NEED)

        assertEquals(Stage.READY, wishlistOf(listOf(need), emptyMap(), DURING_COOLING, zone).single().stage)
    }

    @Test
    fun `an item changes group when its cooling off ends`() {
        val items = listOf(wish(1))

        assertEquals(Stage.COOLING_OFF, wishlistOf(items, emptyMap(), COOLING_ENDS.minusMillis(1), zone).single().stage)
        assertEquals(Stage.NEEDS_REFLECTION, wishlistOf(items, emptyMap(), COOLING_ENDS, zone).single().stage)
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
    fun `a need's stored end doesn't set the next change`() {
        val now = Instant.parse("2026-10-15T10:00:00Z")
        val need = wish(1, endsAt = Instant.parse("2026-10-15T12:00:00Z"), kind = ItemKind.NEED)

        assertEquals(Duration.ofHours(14), untilWishlistChanges(listOf(need), now, zone))
    }
}
