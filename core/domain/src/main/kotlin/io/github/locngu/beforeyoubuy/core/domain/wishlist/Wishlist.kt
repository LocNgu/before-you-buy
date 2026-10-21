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
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * One row of the wishlist: an active [item] with its derived [stage].
 *
 * @property daysLeft calendar days of cooling-off left (ADR-0018); at least 1 while [stage] is
 *   [Stage.COOLING_OFF] and 0 otherwise.
 * @property recheckOn the date a "Wait" decision set, while the item waits for it; null otherwise (see [isWaiting]).
 */
data class WishlistEntry(val item: Item, val stage: Stage, val daysLeft: Int, val recheckOn: LocalDate? = null)

/**
 * The entries in one [stage], soonest cooling-off end first. [waiting] marks the group of Ready items the user chose
 * to wait on: they aren't Ready to decide again until their re-check date, soonest first.
 */
data class WishlistGroup(val stage: Stage, val entries: List<WishlistEntry>, val waiting: Boolean = false)

/**
 * The order the wishlist shows its groups in (brief §6.3): what can be decided first, what waits last. The waiting
 * group comes after all of them.
 */
val WISHLIST_STAGE_ORDER: List<Stage> = listOf(Stage.READY, Stage.NEEDS_REFLECTION, Stage.SAVING, Stage.COOLING_OFF)

/**
 * The wishlist (brief §6.3): the active [items] grouped by derived stage in [WISHLIST_STAGE_ORDER], empty groups
 * left out. Within a group the soonest [Item.coolingEndsAt] comes first, then the oldest, so the order is stable.
 * Ready items with a "Wait" re-check date still ahead ([isWaiting]) form a last, [WishlistGroup.waiting] group
 * instead of being Ready, soonest re-check first.
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
    val entries =
        items
            .filter { it.status == ItemStatus.ACTIVE }
            .map { item ->
                val stage = stageOf(item, reflections[item.id], item.price, now)
                WishlistEntry(item, stage, daysLeft(item, now, zone), recheckOnOf(item, stage, now, zone))
            }
    val (waiting, deciding) = entries.partition { it.recheckOn != null }
    val byStage = deciding.groupBy { it.stage }
    val groups = WISHLIST_STAGE_ORDER.mapNotNull { stage ->
        val group = byStage[stage] ?: return@mapNotNull null
        WishlistGroup(
            stage,
            group.sortedWith(compareBy({ it.item.coolingEndsAt }, { it.item.addedAt }, { it.item.id.value }))
        )
    }
    if (waiting.isEmpty()) return groups
    val waitingGroup = WishlistGroup(
        Stage.READY,
        waiting.sortedWith(compareBy({ it.item.recheckAt }, { it.item.addedAt }, { it.item.id.value })),
        waiting = true
    )
    return groups + waitingGroup
}

/**
 * Whether an active item in [stage] waits for the re-check date of a "Wait" decision: it's Ready, but not due for a
 * decision before [Item.recheckAt] (see [io.github.locngu.beforeyoubuy.core.domain.lifecycle.isDueForDecision]).
 * Items in other stages show what they still need instead.
 */
fun isWaiting(item: Item, stage: Stage, now: Instant): Boolean =
    stage == Stage.READY && item.recheckAt != null && now < item.recheckAt

/** The date in [zone] of the re-check an item [isWaiting] for; null if it isn't waiting. */
fun recheckOnOf(item: Item, stage: Stage, now: Instant, zone: ZoneId): LocalDate? =
    item.recheckAt?.takeIf { isWaiting(item, stage, now) }?.atZone(zone)?.toLocalDate()

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
 * How long the wishlist stays as it is: until the next cooling-off end or "Wait" re-check date among [items], or the
 * next midnight in [zone] (the day count changes), whichever comes first. A screen showing [wishlistOf] recomputes
 * after this.
 */
fun untilWishlistChanges(items: List<Item>, now: Instant, zone: ZoneId): Duration {
    val midnight = now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
    val active = items.filter { it.status == ItemStatus.ACTIVE }
    val nextEnd = active.filter { !isCooledOff(it, now) }.minOfOrNull { it.coolingEndsAt }
    val nextRecheck = active.mapNotNull { it.recheckAt }.filter { it > now }.minOrNull()
    return Duration.between(now, listOfNotNull(midnight, nextEnd, nextRecheck).min())
}
