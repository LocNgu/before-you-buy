package io.github.locngu.beforeyoubuy.core.domain.money

/** Outcome of [parseMoney]. Failures say why, so an input field can explain what's wrong. */
sealed interface MoneyParseResult {
    data class Success(val money: Money) : MoneyParseResult

    /** Nothing but whitespace (or only the currency sign) was entered. */
    data object Empty : MoneyParseResult

    /** Not an amount: letters, a minus sign, misplaced separators, … */
    data object Invalid : MoneyParseResult

    /** More decimals than the currency has minor units, e.g. `1,234` for EUR when `,` is the decimal separator. */
    data class TooManyDecimals(val maxDecimals: Int) : MoneyParseResult

    /** Doesn't fit into a [Long] of minor units. */
    data object TooLarge : MoneyParseResult
}
