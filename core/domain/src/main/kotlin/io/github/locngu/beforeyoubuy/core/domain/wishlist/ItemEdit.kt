package io.github.locngu.beforeyoubuy.core.domain.wishlist

import io.github.locngu.beforeyoubuy.core.domain.capture.CaptureForm
import io.github.locngu.beforeyoubuy.core.domain.capture.checkCapture
import io.github.locngu.beforeyoubuy.core.domain.coolingoff.changeKind
import io.github.locngu.beforeyoubuy.core.domain.coolingoff.waitAfterPriceEdit
import io.github.locngu.beforeyoubuy.core.domain.item.Item
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
 * add. The wait follows the existing rules:
 *
 * - A new price can only extend it ([waitAfterPriceEdit], ADR-0005), never shorten it. An unchanged price leaves it
 *   alone, so editing the name never applies tiers changed since (ADR-0031: a finished wait stays over).
 * - A new kind goes through [changeKind], after the price: a need that becomes a want is parked with the days its
 *   new price calls for; a want that becomes a need leaves the priorities and its wait pauses (ADR-0049).
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
        waitAfterPriceEdit(item, price, rules, zone).copy(price = price)
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
    /**
     * The running wait stays as it is, or there is no end date to show: a need, a want whose wait is complete, or a
     * parked want, whose wait has no end until it's a priority (ADR-0049).
     */
    data object None : WaitChange

    /** A priority still waits, now until [endsAt], later than before. */
    data class Extended(val endsAt: Instant) : WaitChange
}

/** How [edited] (from [editedItem]) changes the running wait of [current] at [now]. */
fun waitChange(current: Item, edited: Item, now: Instant): WaitChange {
    val before = current.coolingEndsAt
    val after = edited.coolingEndsAt
    return if (before != null && after != null && after > before && !isCooledOff(edited, now)) {
        WaitChange.Extended(after)
    } else {
        WaitChange.None
    }
}

private fun String.trimmedOrNull(): String? = trim().ifEmpty { null }
