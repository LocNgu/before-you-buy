package io.github.locngu.beforeyoubuy.core.domain.coolingoff

import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.isCooledOff
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import java.time.Instant
import java.time.Period
import java.time.ZoneId

/**
 * How long a want at [price] cools off: the period of the last tier whose threshold the price reaches. [price]
 * must be in the rules' currency and not negative; anything else is a programming error and throws.
 */
fun periodFor(price: Money, rules: CoolingOffRules): Period {
    requireFits(price, rules)
    return rules.tiers.last { it.minPriceInclusive <= price }.period
}

/**
 * When a new item's cooling-off ends (brief §4.2): [addedAt] plus the period for [price], counted in calendar days
 * in [zone] so a daylight-saving change doesn't move the end by an hour (ADR-0018). A `NEED` doesn't wait
 * (ADR-0008), so it ends at [addedAt]. Like [periodFor], throws for a price that doesn't fit the rules, whatever
 * the kind.
 */
fun initialCoolingEnd(addedAt: Instant, price: Money, kind: ItemKind, rules: CoolingOffRules, zone: ZoneId): Instant {
    val period = periodFor(price, rules)
    return when (kind) {
        ItemKind.NEED -> addedAt
        ItemKind.WANT -> addedAt.atZone(zone).plus(period).toInstant()
    }
}

/**
 * The item's cooling-off end after its price changes to [newPrice] (brief §4.2, ADR-0005): the later of the current
 * end and the end a new item at [newPrice] would have had on the same [Item.addedAt]. A higher price can extend
 * the wait; a lower one never shortens it. A `NEED` keeps its end. [newPrice] must be in the item's currency.
 */
fun coolingEndAfterPriceEdit(item: Item, newPrice: Money, rules: CoolingOffRules, zone: ZoneId): Instant {
    require(newPrice.currency == item.price.currency) {
        "New price in ${newPrice.currency} doesn't match the item's currency ${item.price.currency}"
    }
    return maxOf(item.coolingEndsAt, initialCoolingEnd(item.addedAt, newPrice, item.kind, rules, zone))
}

/**
 * The item's cooling-off end after the user changes the tiers to [rules] at [now] (ADR-0031): an active want that
 * is still cooling off gets the end a new item at its current price would have had on the same [Item.addedAt],
 * longer or shorter than before. If that end has already passed, the item has cooled off. Items whose wait has
 * already ended, needs, and purchased or rejected items keep their end, so lengthening the tiers never puts a
 * Ready item back into cooling off. Apply it to every item when the tiers change.
 */
fun coolingEndAfterRulesChange(item: Item, rules: CoolingOffRules, now: Instant, zone: ZoneId): Instant {
    requireFits(item.price, rules)
    if (item.status != ItemStatus.ACTIVE || isCooledOff(item, now)) return item.coolingEndsAt
    return initialCoolingEnd(item.addedAt, item.price, item.kind, rules, zone)
}

/**
 * The item after the user changes its kind to [kind] at [at]. Only active items change kind; anything else throws.
 *
 * - Want → `NEED`: allowed any time (ADR-0008), recorded in [Item.markedAsNeedAt] for history. The cooling-off end
 *   is kept, in case the item turns back into a want.
 * - `NEED` → want: cools off from [Item.addedAt] like a new want at its current price under the current rules,
 *   replacing any earlier end, so a want that was a need before gets the wait its price calls for now.
 *   Clears [Item.markedAsNeedAt].
 */
fun changeKind(item: Item, kind: ItemKind, at: Instant, rules: CoolingOffRules, zone: ZoneId): Item {
    require(item.status == ItemStatus.ACTIVE) { "Only active items change kind; ${item.id} is ${item.status}" }
    requireFits(item.price, rules)
    if (kind == item.kind) return item
    return when (kind) {
        ItemKind.NEED -> item.copy(kind = ItemKind.NEED, markedAsNeedAt = at)

        ItemKind.WANT -> item.copy(
            kind = ItemKind.WANT,
            markedAsNeedAt = null,
            coolingEndsAt = initialCoolingEnd(item.addedAt, item.price, ItemKind.WANT, rules, zone)
        )
    }
}

/** Wiring bugs fail on every path, not only once an item turns into a want. */
private fun requireFits(price: Money, rules: CoolingOffRules) {
    require(price.currency == rules.currency) {
        "Price in ${price.currency} doesn't match the cooling-off rules' currency ${rules.currency}"
    }
    require(price.minorUnits >= 0) { "Price can't be negative, was $price" }
}
