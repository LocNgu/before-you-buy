package io.github.locngu.beforeyoubuy.core.domain.wishlist

import io.github.locngu.beforeyoubuy.core.domain.coolingoff.WaitProgress
import io.github.locngu.beforeyoubuy.core.domain.coolingoff.waitProgressOf
import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.item.Reflection
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.Stage
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.isCooledOff
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.isParked
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.stageOf
import io.github.locngu.beforeyoubuy.core.domain.pool.Savings
import io.github.locngu.beforeyoubuy.core.domain.pool.SavingsProgress
import io.github.locngu.beforeyoubuy.core.domain.pool.progressOf
import io.github.locngu.beforeyoubuy.core.domain.priorities.prioritiesOf
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * One row of the wishlist: an active [item] with its derived [stage].
 *
 * @property daysLeft calendar days of cooling-off left (ADR-0018); at least 1 while [stage] is
 *   [Stage.COOLING_OFF] and 0 once the wait is complete. For a [Stage.PARKED] want, the days it still has to wait once
 *   it's a priority; they don't count down while it's parked (ADR-0049).
 * @property recheckOn the date a "Wait" decision set, while the item waits for it; null otherwise (see [isWaiting]).
 * @property savings what is set aside for a want while fun money is set up (brief §4.6); null for needs and without
 *   fun money.
 * @property waitProgress how much of its waiting period a parked want has done, which it shows instead of a stage;
 *   null for priorities and needs.
 */
data class WishlistEntry(
    val item: Item,
    val stage: Stage,
    val daysLeft: Int,
    val recheckOn: LocalDate? = null,
    val savings: SavingsProgress? = null,
    val waitProgress: WaitProgress? = null
)

/** A priority with its [rank] (1 first) and its wishlist [entry] (stage, countdown, savings). */
data class Priority(val rank: Int, val entry: WishlistEntry)

/**
 * The wishlist as the screen shows it (brief §6.3, ADR-0049): the [priorities] by rank, each with its stage; then the
 * [needs], Ready at once (ADR-0008), oldest first; then every [parked] want, the ones without a slot, oldest first.
 * Parked wants have no stage groups: none can be Ready and none counts down.
 */
data class Wishlist(
    val priorities: List<Priority>,
    val needs: List<WishlistEntry>,
    val parked: List<WishlistEntry>,
    val categories: List<String> = emptyList()
) {
    /** Every active item, in the order the screen shows them. */
    val entries: List<WishlistEntry> get() = priorities.map { it.entry } + needs + parked

    /** Nothing active: the empty state. */
    fun isEmpty(): Boolean = priorities.isEmpty() && needs.isEmpty() && parked.isEmpty()

    companion object {
        val EMPTY = Wishlist(emptyList(), emptyList(), emptyList())
    }
}

/** What the needs and the wishlist section are ordered by (#83). The priorities always stay in rank order. */
enum class SortField { ADDED, PRICE }

/**
 * How the needs and the parked wants are ordered: by [field], [descending] or not. Ties go to the older item, then the
 * lower id, in either direction. The default is oldest first.
 */
data class WishlistSort(val field: SortField = SortField.ADDED, val descending: Boolean = false) {
    companion object {
        val DEFAULT = WishlistSort()
    }
}

private fun String.categoryKey(): String = trim().lowercase()

/**
 * The distinct categories of [categories] (null and blank ignored), matched ignoring case and surrounding spaces; each
 * is spelled as first seen, trimmed. Sorted alphabetically, ignoring case.
 */
fun distinctCategories(categories: Iterable<String?>): List<String> = categories
    .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
    .distinctBy { it.categoryKey() }
    .sortedBy { it.categoryKey() }

/**
 * Categories to suggest while [typed] is entered: [existing] ones ([distinctCategories]) that contain it, ignoring
 * case, without the one already typed in full. All of them when nothing is typed.
 */
fun categorySuggestions(existing: Iterable<String?>, typed: String): List<String> {
    val key = typed.categoryKey()
    return distinctCategories(existing).filter {
        key.isEmpty() ||
            (it.categoryKey().contains(key) && it.categoryKey() != key)
    }
}

/**
 * The wishlist (brief §6.3, ADR-0049) of the active [items]: the priorities by rank ([prioritiesOf]), then the needs,
 * then the parked wants. Each entry carries the stage [stageOf] gives it; a parked want also how much of its wait is
 * done ([waitProgressOf]). A need with a stored slot isn't a priority (ADR-0047) and is listed with the needs.
 *
 * [reflections] holds the answers given so far; an item without an entry has none. [savings] holds what is set aside
 * per item and whether wants need it to be Ready (ADR-0046).
 *
 * [sort] orders the needs and the parked wants; the priorities stay in rank order (ADR-0011). [category] shows only
 * items of that category, ignoring case and surrounding spaces, in every section; a section left empty is then empty.
 * [Wishlist.categories] lists the categories in use among all active items, whatever the filter, but only when there
 * are two or more (the screen's chips).
 */
fun wishlistOf(
    items: List<Item>,
    reflections: Map<ItemId, Reflection>,
    savings: Savings,
    now: Instant,
    zone: ZoneId,
    sort: WishlistSort = WishlistSort.DEFAULT,
    category: String? = null
): Wishlist {
    val active = items.filter { it.status == ItemStatus.ACTIVE }
    fun entryOf(item: Item): WishlistEntry {
        val stage = stageOf(item, reflections[item.id], savings.fundingOf(item), now)
        return WishlistEntry(
            item,
            stage,
            daysLeft(item, now, zone),
            recheckOnOf(item, stage, now, zone),
            savings.progressOf(item).takeIf { savings.setUp },
            waitProgressOf(item, now, zone).takeIf { stage == Stage.PARKED }
        )
    }
    val priorities = prioritiesOf(active)
    val ids = priorities.map { it.id }.toSet()
    val byAdded = compareBy<Item>({ it.addedAt }, { it.id.value })
    // Ties go to the older item, then the lower id, in either direction.
    val order: Comparator<Item> = when (sort.field) {
        SortField.ADDED ->
            if (sort.descending) compareByDescending<Item> { it.addedAt }.thenBy { it.id.value } else byAdded

        SortField.PRICE ->
            (
                if (sort.descending) {
                    compareByDescending<Item> {
                        it.price.minorUnits
                    }
                } else {
                    compareBy { it.price.minorUnits }
                }
                )
                .then(byAdded)
    }
    val wanted = category?.categoryKey()?.takeIf { it.isNotEmpty() }
    fun Item.shown() = wanted == null || this.category?.categoryKey() == wanted
    val rest = active.filter { it.id !in ids && it.shown() }.sortedWith(order)
    val categories = distinctCategories(active.map { it.category }).takeIf { it.size >= 2 }.orEmpty()
    return Wishlist(
        // Ranks are the real ones, also when a filter hides some priorities.
        priorities = priorities.withIndex()
            .filter { (_, item) -> item.shown() }
            .map { (index, item) -> Priority(index + 1, entryOf(item)) },
        needs = rest.filter { it.kind == ItemKind.NEED }.map(::entryOf),
        parked = rest.filter { it.isParked }.map(::entryOf),
        categories = categories
    )
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
 * 0 once cooled off. A want whose wait doesn't run (parked) has no end date: the days it still has to wait.
 */
fun daysLeft(item: Item, now: Instant, zone: ZoneId): Int {
    if (isCooledOff(item, now)) return 0
    val end = item.coolingEndsAt ?: return item.coolingOffDays - item.waitedDays
    val days = ChronoUnit.DAYS.between(now.atZone(zone).toLocalDate(), end.atZone(zone).toLocalDate())
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
    val nextEnd = active.filter { !isCooledOff(it, now) }.mapNotNull { it.coolingEndsAt }.minOrNull()
    val nextRecheck = active.mapNotNull { it.recheckAt }.filter { it > now }.minOrNull()
    return Duration.between(now, listOfNotNull(midnight, nextEnd, nextRecheck).min())
}
