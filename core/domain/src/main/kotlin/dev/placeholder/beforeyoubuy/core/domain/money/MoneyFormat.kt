package dev.placeholder.beforeyoubuy.core.domain.money

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * This amount as [locale] writes it, always with the currency's minor-unit digits:
 * `1.234,56 €` for de-DE, `€1,234.56` for en-US, `£1,234.56` for GBP in en-GB (ADR-0025).
 */
fun Money.format(locale: Locale): String {
    val digits = Money.fractionDigits(currency)
    // NumberFormat isn't thread-safe, so every call gets its own.
    val format =
        NumberFormat.getCurrencyInstance(locale).apply {
            currency = Currency.getInstance(this@format.currency)
            minimumFractionDigits = digits
            maximumFractionDigits = digits
        }
    return format.format(BigDecimal.valueOf(minorUnits, digits))
}
