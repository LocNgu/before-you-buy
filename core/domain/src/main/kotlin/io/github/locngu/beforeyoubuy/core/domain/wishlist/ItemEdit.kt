package io.github.locngu.beforeyoubuy.core.domain.wishlist

import io.github.locngu.beforeyoubuy.core.domain.capture.CaptureForm
import io.github.locngu.beforeyoubuy.core.domain.capture.checkCapture
import io.github.locngu.beforeyoubuy.core.domain.coolingoff.changeKind
import io.github.locngu.beforeyoubuy.core.domain.coolingoff.coolingEndAfterPriceEdit
import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.isCooledOff
import io.github.locngu.beforeyoubuy.core.domain.money.MoneyParseResult
import io.github.locngu.beforeyoubuy.core.domain.money.formatForInput
import io.github.locngu.beforeyoubuy.core.domain.settings.Policy
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * The edit form for [item], filled with what is stored. The price is exact (`19,99`, ADR-0034), so saving an
 * unchanged form keeps it. Edit uses the same fields and checks as quick add ([checkCapture]).
 */
fun editFormOf(item: Item, locale: Locale): CaptureForm = CaptureForm(
    name = item.name,
    price = item.price.formatForInput(locale),
    link = item.link.orEmpty(),
    kind = item.kind,
    category = item.category.orEmpty(),
    notes = item.notes.orEmpty(),
    targetDate = item.targetDate
)

/**
 * [item] after the user saves [form] at [at] (brief §6.3): text trimmed, blank optional fields cleared, as in quick
 * add. The cooling-off end follows the existing rules:
 *
 * - A new price can only extend it ([coolingEndAfterPriceEdit], ADR-0005), never shorten it. An unchanged price
 *   leaves it alone, so editing the name never applies tiers changed since (ADR-0031: a finished wait stays over).
 * - A new kind goes through [changeKind], after the price: a need that becomes a want cools off from
 *   [Item.addedAt] like a new want at the new price (ADR-0031); a want that becomes a need keeps its end.
 *
 * [imageRef] is the photo to keep: the stored one unless the user picked another or removed it.
 *
 * Only call it for an active [item] in the policy's currency and a form that [checkCapture] accepts; anything else
 * is a programming error and throws.
 */
fun editedItem(
    item: Item,
    form: CaptureForm,
    policy: Policy,
    locale: Locale,
    at: Instant,
    zone: ZoneId,
    imageRef: String? = item.imageRef
): Item {
    require(item.status == ItemStatus.ACTIVE) { "Only active items are edited; ${item.id} is ${item.status}" }
    val check = checkCapture(form, policy.currency, locale)
    val price = (check.price as? MoneyParseResult.Success)?.money
    require(!check.nameMissing && price != null) { "Only a valid form can be saved: $check" }
    val rules = policy.coolingOffRules
    val repriced = if (price == item.price) {
        item
    } else {
        item.copy(price = price, coolingEndsAt = coolingEndAfterPriceEdit(item, price, rules, zone))
    }
    return changeKind(repriced, form.kind, at, rules, zone).copy(
        name = form.name.trim(),
        link = form.link.trimmedOrNull(),
        category = form.category.trimmedOrNull(),
        notes = form.notes.trimmedOrNull(),
        targetDate = form.targetDate,
        imageRef = imageRef
    )
}

/** How an edit changes the wait, so the form can say so before saving ("Waiting period extended to …"). */
sealed interface WaitChange {
    /** The wait stays as it is, or there is none (a need, or a want whose end has passed). */
    data object None : WaitChange

    /** A want still waits, now until [endsAt], later than before. */
    data class Extended(val endsAt: Instant) : WaitChange

    /** A need becomes a want that waits until [endsAt]. */
    data class Starts(val endsAt: Instant) : WaitChange
}

/** How [edited] (from [editedItem]) changes the wait of [current] at [now]. */
fun waitChange(current: Item, edited: Item, now: Instant): WaitChange = when {
    isCooledOff(edited, now) -> WaitChange.None
    current.kind == ItemKind.NEED -> WaitChange.Starts(edited.coolingEndsAt)
    edited.coolingEndsAt > current.coolingEndsAt -> WaitChange.Extended(edited.coolingEndsAt)
    else -> WaitChange.None
}

private fun String.trimmedOrNull(): String? = trim().ifEmpty { null }
