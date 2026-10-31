package io.github.locngu.beforeyoubuy.core.domain.capture

import io.github.locngu.beforeyoubuy.core.domain.coolingoff.coolingOffDaysFor
import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.money.MoneyParseResult
import io.github.locngu.beforeyoubuy.core.domain.money.parseMoney
import io.github.locngu.beforeyoubuy.core.domain.settings.Policy
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

/**
 * The quick-add form as the user typed it (brief §6.2). Only [name] and [price] are required; reflection is
 * deliberately not part of capture (ADR-0009). Check it with [checkCapture], store it as [capturedItem].
 */
data class CaptureForm(
    val name: String = "",
    val price: String = "",
    val link: String = "",
    val kind: ItemKind = ItemKind.WANT,
    val category: String = "",
    val notes: String = "",
    val targetDate: LocalDate? = null
)

/** Outcome of [checkCapture]: whether the name is missing and how the price reads. */
data class CaptureCheck(val nameMissing: Boolean, val price: MoneyParseResult) {
    val isValid: Boolean get() = !nameMissing && price is MoneyParseResult.Success
}

/**
 * Checks the required fields of [form]: a non-blank name and a price that [parseMoney] reads in [currency] and
 * [locale] (`1.234,56` and `1,234.56` both work, ADR-0027). The optional fields can't be wrong.
 */
fun checkCapture(form: CaptureForm, currency: String, locale: Locale): CaptureCheck =
    CaptureCheck(nameMissing = form.name.isBlank(), price = parseMoney(form.price, currency, locale))

/**
 * The new, active item for [form], added at [addedAt] (brief §4.1): text trimmed, blank optional fields left out,
 * the price in the policy's currency, and the days to wait from the policy's tiers. A new want is always parked on
 * the wishlist, even with a free slot: its wait starts only once it's made a priority (ADR-0049). A `NEED` doesn't
 * wait (ADR-0008). Its id is [ItemId.NEW]; store it with
 * [io.github.locngu.beforeyoubuy.core.domain.item.ItemRepository.add].
 *
 * Only call it for a form that [checkCapture] accepts; anything else is a programming error and throws.
 */
fun capturedItem(form: CaptureForm, policy: Policy, locale: Locale, addedAt: Instant): Item {
    val check = checkCapture(form, policy.currency, locale)
    val price = (check.price as? MoneyParseResult.Success)?.money
    require(!check.nameMissing && price != null) { "Only a valid form can be captured: $check" }
    return Item(
        id = ItemId.NEW,
        name = form.name.trim(),
        price = price,
        addedAt = addedAt,
        coolingOffDays = coolingOffDaysFor(price, policy.coolingOffRules),
        kind = form.kind,
        link = form.link.trimmedOrNull(),
        category = form.category.trimmedOrNull(),
        notes = form.notes.trimmedOrNull(),
        targetDate = form.targetDate
    )
}

/**
 * How many calendar days a captured item waits once it's a priority: what the snackbar says right after adding it.
 * 0 for a `NEED`, which doesn't wait (ADR-0008).
 */
fun capturedWaitDays(item: Item): Int = if (item.kind == ItemKind.NEED) 0 else item.coolingOffDays

private fun String.trimmedOrNull(): String? = trim().ifEmpty { null }
