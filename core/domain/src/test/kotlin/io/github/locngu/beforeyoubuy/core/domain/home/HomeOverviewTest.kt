package io.github.locngu.beforeyoubuy.core.domain.home

import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.AFTER_COOLING
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.COMPLETE_REFLECTION
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.COOLING_ENDS
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.PRICE
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.Stage
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.isDueForDecision
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.item
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeOverviewTest {
    private val zone: ZoneId = ZoneOffset.UTC

    private fun wish(id: Long, endsAt: Instant = COOLING_ENDS, kind: ItemKind = ItemKind.WANT): Item =
        item(kind).copy(id = ItemId(id), coolingEndsAt = endsAt)

    @Test
    fun `nothing to decide, consider or count without items`() {
        assertEquals(HomeOverview(emptyList(), 0, null), homeOverviewOf(emptyList(), emptyMap(), AFTER_COOLING, zone))
    }

    @Test
    fun `ready items come first, then those whose cooling off ended without answers`() {
        val unreflected = wish(1, endsAt = Instant.parse("2026-10-10T10:00:00Z"))
        val ready = wish(2, endsAt = Instant.parse("2026-10-20T10:00:00Z"))
        val need = wish(3, kind = ItemKind.NEED).copy(coolingEndsAt = Instant.parse("2026-10-25T10:00:00Z"))
        val cooling = wish(4, endsAt = Instant.parse("2026-12-01T10:00:00Z"))

        val home = homeOverviewOf(
            listOf(cooling, need, unreflected, ready),
            mapOf(ready.id to COMPLETE_REFLECTION),
            AFTER_COOLING,
            zone
        )

        assertEquals(listOf(ready, need, unreflected), home.toDecide.map { it.item })
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

        val home = homeOverviewOf(items, reflections, AFTER_COOLING, zone)

        val expected = items.filter { isDueForDecision(it, reflections[it.id], PRICE, AFTER_COOLING) }
        assertEquals(expected, home.toDecide.map { it.item })
        assertEquals(listOf(due, recheckPassed), expected)
    }

    @Test
    fun `items still cooling off are never offered for a decision`() {
        val home = homeOverviewOf(listOf(wish(1)), emptyMap(), COOLING_ENDS.minusSeconds(1), zone)

        assertEquals(emptyList<Item>(), home.toDecide.map { it.item })
    }

    @Test
    fun `counts every active item, waiting ones included, and nothing ended`() {
        val waiting = wish(1, endsAt = Instant.parse("2026-10-20T10:00:00Z"))
            .copy(recheckAt = Instant.parse("2026-11-10T00:00:00Z"))
        val cooling = wish(2, endsAt = Instant.parse("2026-12-01T10:00:00Z"))
        val bought = wish(3).copy(status = ItemStatus.PURCHASED)
        val rejected = wish(4).copy(status = ItemStatus.REJECTED)

        val home = homeOverviewOf(
            listOf(waiting, cooling, bought, rejected),
            mapOf(waiting.id to COMPLETE_REFLECTION),
            AFTER_COOLING,
            zone
        )

        assertEquals(2, home.activeCount)
    }

    @Test
    fun `the next cooling-off end is the soonest one, with its days left`() {
        val later = wish(1, endsAt = Instant.parse("2026-12-01T10:00:00Z"))
        val sooner = wish(2, endsAt = Instant.parse("2026-11-14T10:00:00Z"))
        val ended = wish(3, endsAt = Instant.parse("2026-10-20T10:00:00Z"))

        val next = homeOverviewOf(listOf(later, ended, sooner), emptyMap(), AFTER_COOLING, zone).nextCoolingEnd

        assertEquals(sooner, next?.item)
        assertEquals(12, next?.daysLeft)
    }

    @Test
    fun `no next cooling-off end once every wait is over`() {
        val home = homeOverviewOf(listOf(wish(1)), emptyMap(), AFTER_COOLING, zone)

        assertNull(home.nextCoolingEnd)
    }
}
