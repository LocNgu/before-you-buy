package io.github.locngu.beforeyoubuy.core.domain.money

import java.util.Currency

/**
 * An amount of money in the currency's minor units, e.g. cents for EUR (ADR-0018: never a floating-point type).
 *
 * [currency] is an ISO 4217 code such as `EUR`; how many minor units make one major unit comes from
 * [java.util.Currency] (2 for EUR, 0 for JPY).
 *
 * Arithmetic and comparison only work within one currency: mixing currencies is a programming error and throws.
 * Overflowing a [Long] throws [ArithmeticException] instead of wrapping around.
 */
data class Money(val minorUnits: Long, val currency: String) : Comparable<Money> {
    init {
        val known = runCatching { Currency.getInstance(currency) }.isSuccess
        require(known && currency.all { it in 'A'..'Z' }) {
            "Currency must be an ISO 4217 code like EUR, was \"$currency\""
        }
    }

    /** The sum; throws on mixed currencies or overflow. */
    operator fun plus(other: Money): Money = Money(Math.addExact(minorUnits, sameCurrency(other).minorUnits), currency)

    /** The difference, which may be negative; throws on mixed currencies or overflow. */
    operator fun minus(other: Money): Money =
        Money(Math.subtractExact(minorUnits, sameCurrency(other).minorUnits), currency)

    /** Orders by amount; throws on mixed currencies. */
    override fun compareTo(other: Money): Int = minorUnits.compareTo(sameCurrency(other).minorUnits)

    private fun sameCurrency(other: Money): Money {
        require(other.currency == currency) { "Can't combine $currency with ${other.currency}" }
        return other
    }

    companion object {
        /** Minor-unit digits of an ISO 4217 [currency] (2 for EUR, 0 for JPY, 3 for BHD). */
        fun fractionDigits(currency: String): Int =
            Currency.getInstance(currency).defaultFractionDigits.coerceAtLeast(0)
    }
}

/**
 * The exact sum of these amounts, zero in [currency] when empty; throws on mixed currencies or overflow.
 * Format the total once, never add up rounded parts: `19,50 € + 19,50 €` shows as `39 €`, not `40 €` (ADR-0034).
 */
fun Iterable<Money>.total(currency: String): Money = fold(Money(0, currency), Money::plus)
