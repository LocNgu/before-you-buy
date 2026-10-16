package io.github.locngu.beforeyoubuy.core.domain.money

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * This amount as [locale] writes it, rounded to whole units, halves up (ADR-0034): `20 €` for 19,99 €
 * in de-DE, `€1,234` for €1,234.49 in en-US, `£1,235` for £1,234.50 in en-GB.
 *
 * Sum exact amounts first and format the total once ([total]); never add up formatted or rounded values.
 */
fun Money.format(locale: Locale): String {
    val digits = Money.fractionDigits(currency)
    // NumberFormat isn't thread-safe, so every call gets its own.
    val format =
        NumberFormat.getCurrencyInstance(locale).apply {
            currency = Currency.getInstance(this@format.currency)
            minimumFractionDigits = 0
            maximumFractionDigits = 0
        }
    return format.format(BigDecimal.valueOf(minorUnits, digits).setScale(0, RoundingMode.HALF_UP))
}

/**
 * This amount exactly, for prefilling an input field: no currency sign, no grouping, ASCII digits and
 * [locale]'s decimal separator (`19,99` in de-DE, `19.99` in en-US, `120` for a whole amount).
 * [parseMoney] reads it back to the same amount, so saving an unchanged form never changes a price.
 *
 * Throws for negative amounts, which [parseMoney] doesn't accept.
 */
fun Money.formatForInput(locale: Locale): String {
    require(minorUnits >= 0) { "Input fields only hold amounts ≥ 0, was $minorUnits" }
    val digits = Money.fractionDigits(currency)
    val amount = BigDecimal.valueOf(minorUnits, digits)
    val whole = amount.toBigInteger().toString()
    val fraction = amount.remainder(BigDecimal.ONE).unscaledValue()
    if (fraction.signum() == 0) return whole
    return whole + inputDecimalSeparator(locale) + fraction.toString().padStart(digits, '0')
}

/**
 * [locale]'s decimal separator for money if [parseMoney] accepts it as one, else `.`. Every en and de
 * locale uses `.` or `,`; elsewhere a three-decimal amount (BHD) would read back as grouped.
 */
private fun inputDecimalSeparator(locale: Locale): Char {
    val separator = DecimalFormatSymbols.getInstance(locale).monetaryDecimalSeparator
    return if (separator == ',' || separator == '.') separator else '.'
}
