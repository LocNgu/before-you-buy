package io.github.locngu.beforeyoubuy.core.domain.settings

import io.github.locngu.beforeyoubuy.core.domain.money.Money
import java.util.Currency
import java.util.Locale

/** Used when the device region has no usable currency. */
const val FALLBACK_CURRENCY = "EUR"

/**
 * Whether [code] can be the app's currency: an ISO 4217 code [Money] accepts that is real money with a fixed number of
 * minor units. Rules out XXX ("no currency"), XTS (testing), gold and other units without minor units.
 */
fun isUsableCurrency(code: String): Boolean = runCatching {
    Money(0, code)
    Currency.getInstance(code).defaultFractionDigits >= 0
}.getOrDefault(false)

/**
 * The currency of [locale]'s region (`de-DE` → EUR, `en-US` → USD), or [FALLBACK_CURRENCY] when the locale has no
 * region or the region's currency isn't [usable][isUsableCurrency] (e.g. Antarctica has none).
 */
fun defaultCurrency(locale: Locale): String {
    val code = runCatching { Currency.getInstance(locale)?.currencyCode }.getOrNull()
    return code?.takeIf(::isUsableCurrency) ?: FALLBACK_CURRENCY
}
