package io.github.locngu.beforeyoubuy.core.domain.home

import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.Reflection
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.Stage
import io.github.locngu.beforeyoubuy.core.domain.pool.Savings
import io.github.locngu.beforeyoubuy.core.domain.pool.SavingsProgress
import io.github.locngu.beforeyoubuy.core.domain.wishlist.Priority
import io.github.locngu.beforeyoubuy.core.domain.wishlist.WishlistEntry
import io.github.locngu.beforeyoubuy.core.domain.wishlist.prioritiesIn
import io.github.locngu.beforeyoubuy.core.domain.wishlist.wishlistOf
import java.time.Instant
import java.time.ZoneId

/**
 * What Home answers about the wishlist (brief §6.1). The fun money is
 * [io.github.locngu.beforeyoubuy.core.domain.pool.funMoneyOf].
 *
 * @property toDecide "What can I decide on?": Ready items that aren't waiting for a "Wait" date (needs included,
 *   exactly those [io.github.locngu.beforeyoubuy.core.domain.lifecycle.isDueForDecision] accepts), then wants whose
 *   cooling-off is over but whose required answers are missing. Each part in wishlist order. Empty: nothing to show.
 * @property activeCount "What am I considering?": every active item, waiting ones included.
 * @property nextCoolingEnd the item still cooling off whose cooling-off ends first, or null if none is.
 * @property priorities "What am I saving for?" once the user has priorities: every priority by rank, with its savings
 *   while fun money is set up (ADR-0047). Empty without priorities.
 * @property savingFor "What am I saving for?" without priorities: at most [SAVING_FOR_SHOWN] active wants with their
 *   savings, the most set aside relative to the price first, then the oldest; never needs (ADR-0044, ADR-0046).
 *   Empty while fun money isn't set up, while there are [priorities], or without wants.
 */
data class HomeOverview(
    val toDecide: List<WishlistEntry>,
    val activeCount: Int,
    val nextCoolingEnd: WishlistEntry?,
    val savingFor: List<SavingFor> = emptyList(),
    val priorities: List<Priority> = emptyList()
)

/** One row of "What am I saving for?": an active want and what is set aside for it. */
data class SavingFor(val item: Item, val progress: SavingsProgress)

/** How many wants "What am I saving for?" shows. */
const val SAVING_FOR_SHOWN = 3

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
    val groups = wishlistOf(items, reflections, savings, now, zone)
    fun entries(stage: Stage) = groups.firstOrNull { it.stage == stage && !it.waiting }?.entries.orEmpty()
    val priorities = prioritiesIn(groups)
    return HomeOverview(
        toDecide = entries(Stage.READY) + entries(Stage.NEEDS_REFLECTION),
        activeCount = groups.sumOf { it.entries.size },
        nextCoolingEnd = entries(Stage.COOLING_OFF).firstOrNull(),
        savingFor = if (priorities.isNotEmpty()) {
            emptyList()
        } else {
            groups.flatMap { it.entries }
                .mapNotNull { entry -> entry.savings?.let { SavingFor(entry.item, it) } }
                .sortedWith(
                    compareByDescending<SavingFor> { it.progress.fraction }
                        .thenBy { it.item.addedAt }
                        .thenBy { it.item.id.value }
                )
                .take(SAVING_FOR_SHOWN)
        },
        priorities = priorities
    )
}
