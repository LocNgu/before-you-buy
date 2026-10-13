package io.github.locngu.beforeyoubuy.core.domain.settings

import io.github.locngu.beforeyoubuy.core.domain.money.Money
import java.util.Currency
import java.util.Locale

/** Used when the device region has no usable currency. */
const val FALLBACK_CURRENCY = "EUR"

/**
 * The currency of [locale]'s region (`de-DE` → EUR, `en-US` → USD), or [FALLBACK_CURRENCY] when the locale has no
 * region or the region has no currency [Money] accepts (e.g. Antarctica, or the "no currency" code XXX).
 */
fun defaultCurrency(locale: Locale): String {
    val code = runCatching { Currency.getInstance(locale)?.currencyCode }.getOrNull()
    return code?.takeIf { it != NO_CURRENCY && runCatching { Money(0, it) }.isSuccess } ?: FALLBACK_CURRENCY
}

/** ISO 4217 "transactions where no currency is involved". */
private const val NO_CURRENCY = "XXX"
