package io.github.locngu.beforeyoubuy.core.domain.home

import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.Reflection
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.Stage
import io.github.locngu.beforeyoubuy.core.domain.pool.Savings
import io.github.locngu.beforeyoubuy.core.domain.wishlist.Priority
import io.github.locngu.beforeyoubuy.core.domain.wishlist.WishlistEntry
import io.github.locngu.beforeyoubuy.core.domain.wishlist.wishlistOf
import java.time.Instant
import java.time.ZoneId

/**
 * What Home answers about the wishlist (brief §6.1). The fun money is
 * [io.github.locngu.beforeyoubuy.core.domain.pool.funMoneyOf].
 *
 * @property toDecide "What can I decide on?": Ready items that aren't waiting for a "Wait" date (needs included,
 *   exactly those [io.github.locngu.beforeyoubuy.core.domain.lifecycle.isDueForDecision] accepts), then wants whose
 *   cooling-off is over but whose required answers are missing. Each part soonest cooling-off end first (a need by
 *   the day it was added). Never a parked want (ADR-0049). Empty: nothing to show.
 * @property activeCount "What am I considering?": every active item, waiting ones included.
 * @property nextCoolingEnd the item still cooling off whose cooling-off ends first, or null if none is.
 * @property priorities "What am I saving for?": every priority by rank, with its savings while fun money is set up
 *   (ADR-0047). Only priorities can have money set aside, so a parked want is never listed, not even one that still
 *   holds money from before it lost its slot (ADR-0049, ADR-0052). Empty without priorities.
 */
data class HomeOverview(
    val toDecide: List<WishlistEntry>,
    val activeCount: Int,
    val nextCoolingEnd: WishlistEntry?,
    val priorities: List<Priority> = emptyList()
)

/**
 * Home's view of the active [items] at [now] in [zone] (brief §6.1), derived from [wishlistOf] so both screens
 * always agree on stages and order. Never includes anything about decided-against items: their total stays off any
 * surface that shows Ready items (moral licensing, ADR-0012). Recompute after
 * [io.github.locngu.beforeyoubuy.core.domain.wishlist.untilWishlistChanges].
 */
fun homeOverviewOf(
    items: List<Item>,
    reflections: Map<ItemId, Reflection>,
    savings: Savings,
    now: Instant,
    zone: ZoneId
): HomeOverview {
    val wishlist = wishlistOf(items, reflections, savings, now, zone)
    // Only priorities and needs are ever to decide or count down (ADR-0049); soonest end first, as before.
    val deciding = (wishlist.priorities.map { it.entry } + wishlist.needs)
        .filter { it.recheckOn == null }
        .sortedWith(compareBy({ it.item.coolingEndsAt ?: it.item.addedAt }, { it.item.addedAt }, { it.item.id.value }))
    fun entries(stage: Stage) = deciding.filter { it.stage == stage }
    return HomeOverview(
        toDecide = entries(Stage.READY) + entries(Stage.NEEDS_REFLECTION),
        activeCount = wishlist.entries.size,
        nextCoolingEnd = entries(Stage.COOLING_OFF).firstOrNull(),
        priorities = wishlist.priorities
    )
}
