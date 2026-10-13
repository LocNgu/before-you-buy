package io.github.locngu.beforeyoubuy.core.domain.money

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The same number in [currency] (ADR-0033): `100,00 €` becomes `100.00 $`. There is no conversion; the app has no
 * exchange rates (ADR-0003). Minor units are rescaled when the currencies have different minor-unit digits: `12,34 €`
 * becomes `12 ¥` (rounded half up), `12 ¥` becomes `12,00 €`. Throws [ArithmeticException] if the result doesn't fit
 * a [Long].
 */
fun Money.relabeled(currency: String): Money {
    if (currency == this.currency) return this
    val shift = Money.fractionDigits(currency) - Money.fractionDigits(this.currency)
    val minor = BigDecimal.valueOf(minorUnits).movePointRight(shift).setScale(0, RoundingMode.HALF_UP).longValueExact()
    return Money(minor, currency)
}
