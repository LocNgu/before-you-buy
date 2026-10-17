package io.github.locngu.beforeyoubuy.core.domain.wishlist

import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.item.Reflection
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.Stage
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.isCooledOff
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.stageOf
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * One row of the wishlist: an active [item] with its derived [stage].
 *
 * @property daysLeft calendar days of cooling-off left (ADR-0018); at least 1 while [stage] is
 *   [Stage.COOLING_OFF] and 0 otherwise.
 */
data class WishlistEntry(val item: Item, val stage: Stage, val daysLeft: Int)

/** The entries in one [stage], soonest cooling-off end first. */
data class WishlistGroup(val stage: Stage, val entries: List<WishlistEntry>)

/** The order the wishlist shows its groups in (brief §6.3): what can be decided first, what waits last. */
val WISHLIST_STAGE_ORDER: List<Stage> = listOf(Stage.READY, Stage.NEEDS_REFLECTION, Stage.SAVING, Stage.COOLING_OFF)

/**
 * The wishlist (brief §6.3): the active [items] grouped by derived stage in [WISHLIST_STAGE_ORDER], empty groups
 * left out. Within a group the soonest [Item.coolingEndsAt] comes first, then the oldest, so the order is stable.
 *
 * Savings don't exist before M2, so every item counts as funded: its allocation is its own price (see
 * [stageOf]). [reflections] holds the answers given so far; an item without an entry has none.
 */
fun wishlistOf(
    items: List<Item>,
    reflections: Map<ItemId, Reflection>,
    now: Instant,
    zone: ZoneId
): List<WishlistGroup> {
    val byStage =
        items
            .filter { it.status == ItemStatus.ACTIVE }
            .map { WishlistEntry(it, stageOf(it, reflections[it.id], it.price, now), daysLeft(it, now, zone)) }
            .groupBy { it.stage }
    return WISHLIST_STAGE_ORDER.mapNotNull { stage ->
        val entries = byStage[stage] ?: return@mapNotNull null
        WishlistGroup(
            stage,
            entries.sortedWith(compareBy({ it.item.coolingEndsAt }, { it.item.addedAt }, { it.item.id.value }))
        )
    }
}

/**
 * Calendar days of cooling-off left at [now] in [zone] (ADR-0018): the number of dates between today and the end
 * date, and at least 1 until the end instant has passed, so the last day reads "1 day left", not "0 days left".
 * 0 once cooled off.
 */
fun daysLeft(item: Item, now: Instant, zone: ZoneId): Int {
    if (isCooledOff(item, now)) return 0
    val days = ChronoUnit.DAYS.between(now.atZone(zone).toLocalDate(), item.coolingEndsAt.atZone(zone).toLocalDate())
    return maxOf(1L, days).toInt()
}

/**
 * How long the wishlist stays as it is: until the next cooling-off end among [items], or the next midnight in
 * [zone] (the day count changes), whichever comes first. A screen showing [wishlistOf] recomputes after this.
 */
fun untilWishlistChanges(items: List<Item>, now: Instant, zone: ZoneId): Duration {
    val midnight = now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
    val nextEnd = items.filter {
        it.status == ItemStatus.ACTIVE && !isCooledOff(it, now)
    }.minOfOrNull { it.coolingEndsAt }
    return Duration.between(now, listOfNotNull(midnight, nextEnd).min())
}
