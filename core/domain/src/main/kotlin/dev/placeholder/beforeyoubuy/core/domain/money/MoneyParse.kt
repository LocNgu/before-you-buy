package dev.placeholder.beforeyoubuy.core.domain.money

import java.math.BigInteger
import java.text.DecimalFormatSymbols
import java.util.Currency
import java.util.Locale

/**
 * Reads an amount typed by the user into minor units of [currency] (ADR-0025).
 *
 * Both `.` and `,` work as the decimal separator, whatever the locale, because keyboards differ:
 * - Both present: the later one is the decimal separator (`1.234,56`, `1,234.56`).
 * - Only one, appearing more than once: grouping (`1.234.567`).
 * - Only one, appearing once: the decimal separator, unless exactly three digits follow and it is
 *   not [locale]'s decimal separator (`1.234` is 1234 in German; `1,234` is 1234 in English) or the
 *   currency has no minor units (`1,234` yen).
 *
 * Spaces and apostrophes may also group digits; one number uses one kind of grouping separator.
 * Groups after the first must have three digits. The currency sign or code may be typed before or
 * after the number. Amounts are never negative, and extra decimals are rejected rather than rounded.
 */
fun parseMoney(text: String, currency: String, locale: Locale): MoneyParseResult {
    val fractionDigits = Money.fractionDigits(currency)
    val number = text.withoutCurrency(currency, locale).trim()
    if (number.isEmpty()) return MoneyParseResult.Empty
    if (number.any { !it.isAsciiDigit() && it !in DECIMAL_CANDIDATES && !it.isGroupingOnly() }) {
        return MoneyParseResult.Invalid
    }

    val decimalIndex = decimalSeparatorIndex(number, locale, fractionDigits)
    val integerPart = if (decimalIndex < 0) number else number.substring(0, decimalIndex)
    val fractionPart = if (decimalIndex < 0) "" else number.substring(decimalIndex + 1)

    val integerDigits = ungroup(integerPart) ?: return MoneyParseResult.Invalid
    if (!fractionPart.all { it.isAsciiDigit() }) return MoneyParseResult.Invalid
    if (integerDigits.isEmpty() && fractionPart.isEmpty()) return MoneyParseResult.Invalid
    if (fractionPart.length > fractionDigits) return MoneyParseResult.TooManyDecimals(fractionDigits)

    val minorUnits = BigInteger(integerDigits + fractionPart.padEnd(fractionDigits, '0'))
    if (minorUnits > MAX_MINOR_UNITS) return MoneyParseResult.TooLarge
    return MoneyParseResult.Success(Money(minorUnits.toLong(), currency))
}

private val DECIMAL_CANDIDATES = charArrayOf('.', ',')
private val MAX_MINOR_UNITS = BigInteger.valueOf(Long.MAX_VALUE)

// Any separator that may group digits: `.` `,` apostrophes and spaces (incl. no-break spaces).
// Must split on exactly the characters isGroupingOnly() lets through.
private val GROUP_SEPARATOR = Regex("""[.,'’\p{Zs}]""")

private fun Char.isAsciiDigit() = this in '0'..'9'

private fun Char.isSpace() = Character.getType(this) == Character.SPACE_SEPARATOR.toInt()

private fun Char.isGroupingOnly() = isSpace() || this == '\'' || this == '’'

/** The kind of grouping separator, so `'` and `’`, or a space and a no-break space, count as one. */
private fun Char.groupingKind(): Char = when {
    isSpace() -> ' '
    this == '’' -> '\''
    else -> this
}

/** Removes the currency code or [locale]'s sign from the start or end (never from the middle). */
private fun String.withoutCurrency(currency: String, locale: Locale): String {
    val signs =
        listOf(currency, Currency.getInstance(currency).getSymbol(locale))
            .filter { it.isNotEmpty() }
            .sortedByDescending { it.length }
    var result = trim()
    signs.firstOrNull { result.startsWith(it, ignoreCase = true) }?.let { result = result.drop(it.length).trim() }
    signs.firstOrNull { result.endsWith(it, ignoreCase = true) }?.let { result = result.dropLast(it.length).trim() }
    return result
}

/** Index of the decimal separator in [number], or -1 if it has none. */
private fun decimalSeparatorIndex(number: String, locale: Locale, fractionDigits: Int): Int {
    val separators = number.indices.filter { number[it] in DECIMAL_CANDIDATES }
    if (separators.isEmpty()) return -1
    val kinds = separators.map { number[it] }.toSet()
    if (kinds.size > 1) return separators.last()
    if (separators.size > 1) return -1

    val index = separators.single()
    val localeDecimal = DecimalFormatSymbols.getInstance(locale).monetaryDecimalSeparator
    val after = number.substring(index + 1)
    val looksGrouped = after.length == 3 && after.all { it.isAsciiDigit() }
    val grouped = looksGrouped && (number[index] != localeDecimal || fractionDigits == 0)
    return if (grouped) -1 else index
}

/** The digits of [integerPart] without grouping separators, or null if the groups are malformed. */
private fun ungroup(integerPart: String): String? {
    if (integerPart.isEmpty()) return ""
    val groups = integerPart.split(GROUP_SEPARATOR)
    if (groups.size == 1) return groups.single()
    if (integerPart.filterNot { it.isAsciiDigit() }.map { it.groupingKind() }.toSet().size > 1) return null
    val first = groups.first()
    val wellFormed =
        first.length in 1..3 &&
            !first.startsWith('0') &&
            groups.drop(1).all { it.length == 3 }
    return if (wellFormed) groups.joinToString("") else null
}
