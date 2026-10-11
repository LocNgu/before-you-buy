package io.github.locngu.beforeyoubuy.core.domain.money

import java.util.Currency

/**
 * An amount of money in the currency's minor units, e.g. cents for EUR (ADR-0018: never a floating-point type).
 *
 * [currency] is an ISO 4217 code such as `EUR`; how many minor units make one major unit comes from
 * [java.util.Currency] (2 for EUR, 0 for JPY).
 */
data class Money(val minorUnits: Long, val currency: String) {
    init {
        val known = runCatching { Currency.getInstance(currency) }.isSuccess
        require(known && currency.all { it in 'A'..'Z' }) {
            "Currency must be an ISO 4217 code like EUR, was \"$currency\""
        }
    }

    companion object {
        /** Minor-unit digits of an ISO 4217 [currency] (2 for EUR, 0 for JPY, 3 for BHD). */
        fun fractionDigits(currency: String): Int =
            Currency.getInstance(currency).defaultFractionDigits.coerceAtLeast(0)
    }
}
