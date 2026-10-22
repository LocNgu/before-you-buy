package io.github.locngu.beforeyoubuy.core.domain.home

import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.Reflection
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.Stage
import io.github.locngu.beforeyoubuy.core.domain.wishlist.WishlistEntry
import io.github.locngu.beforeyoubuy.core.domain.wishlist.wishlistOf
import java.time.Instant
import java.time.ZoneId

/**
 * What Home answers about the wishlist (brief §6.1). The money questions come with M2.
 *
 * @property toDecide "What can I decide on?": Ready items that aren't waiting for a "Wait" date (needs included,
 *   exactly those [io.github.locngu.beforeyoubuy.core.domain.lifecycle.isDueForDecision] accepts), then wants whose
 *   cooling-off is over but whose required answers are missing. Each part in wishlist order. Empty: nothing to show.
 * @property activeCount "What am I considering?": every active item, waiting ones included.
 * @property nextCoolingEnd the item still cooling off whose cooling-off ends first, or null if none is.
 */
data class HomeOverview(val toDecide: List<WishlistEntry>, val activeCount: Int, val nextCoolingEnd: WishlistEntry?)

/**
 * Home's view of the active [items] at [now] in [zone] (brief §6.1), derived from [wishlistOf] so both screens
 * always agree on stages and order. Never includes anything about decided-against items: their total stays off any
 * surface that shows Ready items (moral licensing, ADR-0012). Recompute after
 * [io.github.locngu.beforeyoubuy.core.domain.wishlist.untilWishlistChanges].
 */
fun homeOverviewOf(items: List<Item>, reflections: Map<ItemId, Reflection>, now: Instant, zone: ZoneId): HomeOverview {
    val groups = wishlistOf(items, reflections, now, zone)
    fun entries(stage: Stage) = groups.firstOrNull { it.stage == stage && !it.waiting }?.entries.orEmpty()
    return HomeOverview(
        toDecide = entries(Stage.READY) + entries(Stage.NEEDS_REFLECTION),
        activeCount = groups.sumOf { it.entries.size },
        nextCoolingEnd = entries(Stage.COOLING_OFF).firstOrNull()
    )
}
