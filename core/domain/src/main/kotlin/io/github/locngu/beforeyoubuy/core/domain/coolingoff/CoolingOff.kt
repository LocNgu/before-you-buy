package io.github.locngu.beforeyoubuy.core.domain.coolingoff

import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.isCooledOff
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import java.time.Instant
import java.time.Period
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * How long a want at [price] cools off: the period of the last tier whose threshold the price reaches. [price]
 * must be in the rules' currency and not negative; anything else is a programming error and throws.
 */
fun periodFor(price: Money, rules: CoolingOffRules): Period {
    requireFits(price, rules)
    return rules.tiers.last { it.minPriceInclusive <= price }.period
}

/**
 * How many calendar days a want at [price] waits (brief §4.2): the days of [periodFor]. Periods are whole days.
 * Like [periodFor], throws for a price that doesn't fit the rules.
 */
fun coolingOffDaysFor(price: Money, rules: CoolingOffRules): Int = periodFor(price, rules).days

/** Whether the item's wait runs: it got a priority slot and hasn't lost it (ADR-0049). */
val Item.waitRuns: Boolean get() = waitingSince != null

/**
 * Whole calendar days the item has waited at [now] in [zone] (ADR-0018): the earlier stretches plus the running one.
 * A part of a day doesn't count, so this never credits time that hasn't passed (ADR-0050).
 */
fun daysWaited(item: Item, now: Instant, zone: ZoneId): Int =
    item.waitedDays + (item.waitingSince?.let { wholeDaysBetween(it, now, zone) } ?: 0)

/**
 * The item with its wait running from [at] (ADR-0049): it got a priority slot. It ends once the days still to wait
 * have passed, in calendar days in [zone]; a want that has already waited its full period is complete at once. Only
 * active wants wait: anything else, or a wait already running, is returned unchanged.
 */
fun startWait(item: Item, at: Instant, zone: ZoneId): Item {
    if (item.status != ItemStatus.ACTIVE || item.kind != ItemKind.WANT || item.waitRuns) return item
    return item.copy(waitingSince = at, coolingEndsAt = runningEnd(at, item.coolingOffDays, item.waitedDays, zone))
}

/**
 * The item with its wait paused at [at] (ADR-0049): it lost its slot. The whole calendar days of the running stretch
 * are added to [Item.waitedDays]; the wait never resets. A part of a day is dropped ([daysWaited]). An item whose
 * wait doesn't run is returned unchanged.
 */
fun pauseWait(item: Item, at: Instant, zone: ZoneId): Item {
    if (!item.waitRuns) return item
    return item.copy(waitedDays = daysWaited(item, at, zone), waitingSince = null, coolingEndsAt = null)
}

/**
 * The item's wait after its price changes to [newPrice] (brief §4.2, ADR-0005): the days to wait become the larger of
 * the current ones and those for [newPrice], so a higher price can extend the wait and a lower one never shortens it.
 * A running wait ends later accordingly. A `NEED` keeps its wait. Only the wait changes; the caller sets the price.
 * [newPrice] must be in the item's currency.
 */
fun waitAfterPriceEdit(item: Item, newPrice: Money, rules: CoolingOffRules, zone: ZoneId): Item {
    require(newPrice.currency == item.price.currency) {
        "New price in ${newPrice.currency} doesn't match the item's currency ${item.price.currency}"
    }
    val days = coolingOffDaysFor(newPrice, rules)
    if (item.kind == ItemKind.NEED || days <= item.coolingOffDays) return item
    return withCoolingOffDays(item, days, zone)
}

/**
 * The item's wait after the user changes the tiers to [rules] at [now] (ADR-0031, ADR-0049): an active want whose
 * wait isn't complete gets the days its current price calls for under [rules], longer or shorter than before,
 * measured against the time it has already waited. If that is already enough, the wait is complete. Complete waits,
 * needs and purchased or rejected items keep theirs, so lengthening the tiers never puts a Ready item back into
 * cooling off. Apply it to every item when the tiers change.
 */
fun waitAfterRulesChange(item: Item, rules: CoolingOffRules, now: Instant, zone: ZoneId): Item {
    requireFits(item.price, rules)
    if (item.status != ItemStatus.ACTIVE || isCooledOff(item, now)) return item
    return withCoolingOffDays(item, coolingOffDaysFor(item.price, rules), zone)
}

/**
 * The item after the user changes its kind to [kind] at [at]. Only active items change kind; anything else throws.
 *
 * - Want → `NEED`: allowed any time (ADR-0008), recorded in [Item.markedAsNeedAt] for history. It leaves the
 *   priorities, since only wants hold a slot (ADR-0047), so its wait pauses at [at] in [zone]; what it waited is kept
 *   in case it turns back into a want.
 * - `NEED` → want: parked on the wishlist without a slot, with the days its current price calls for under the
 *   current rules (ADR-0031) and the time it had waited as a want (none if it was captured as a need, ADR-0049).
 *   Clears [Item.markedAsNeedAt].
 */
fun changeKind(item: Item, kind: ItemKind, at: Instant, rules: CoolingOffRules, zone: ZoneId): Item {
    require(item.status == ItemStatus.ACTIVE) { "Only active items change kind; ${item.id} is ${item.status}" }
    requireFits(item.price, rules)
    if (kind == item.kind) return item
    return when (kind) {
        // Only wants hold a priority slot (ADR-0047); the repository moves the others up.
        ItemKind.NEED -> pauseWait(item, at, zone).copy(kind = ItemKind.NEED, markedAsNeedAt = at, prioritySlot = null)

        ItemKind.WANT -> pauseWait(item, at, zone).copy(
            kind = ItemKind.WANT,
            markedAsNeedAt = null,
            coolingOffDays = coolingOffDaysFor(item.price, rules)
        )
    }
}

/** [item] waiting [days] in all; a running wait gets the end that matches, counted from its start. */
private fun withCoolingOffDays(item: Item, days: Int, zone: ZoneId): Item {
    val since = item.waitingSince ?: return item.copy(coolingOffDays = days)
    return item.copy(coolingOffDays = days, coolingEndsAt = runningEnd(since, days, item.waitedDays, zone))
}

/** When a wait running from [since] is complete: the days still to wait later, in calendar days in [zone]. */
private fun runningEnd(since: Instant, days: Int, waited: Int, zone: ZoneId): Instant =
    if (waited >= days) since else since.atZone(zone).plusDays((days - waited).toLong()).toInstant()

/**
 * Whole calendar days from [from] to [to] in [zone], on local date-times like `plusDays`: 1 only once the same time of
 * day comes round again, also across a daylight-saving change. 0 if [to] is before [from]. How waits count time
 * (ADR-0050).
 */
fun wholeDaysBetween(from: Instant, to: Instant, zone: ZoneId): Int =
    maxOf(0L, ChronoUnit.DAYS.between(from.atZone(zone), to.atZone(zone))).toInt()

/** Wiring bugs fail on every path, not only once an item turns into a want. */
private fun requireFits(price: Money, rules: CoolingOffRules) {
    require(price.currency == rules.currency) {
        "Price in ${price.currency} doesn't match the cooling-off rules' currency ${rules.currency}"
    }
    require(price.minorUnits >= 0) { "Price can't be negative, was $price" }
}
