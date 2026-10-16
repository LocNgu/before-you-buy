package io.github.locngu.beforeyoubuy.core.domain.money

import io.github.locngu.beforeyoubuy.core.domain.coolingoff.CoolingOffRules
import io.github.locngu.beforeyoubuy.core.domain.coolingoff.CoolingOffRulesResult
import io.github.locngu.beforeyoubuy.core.domain.coolingoff.CoolingOffTier
import java.time.Period
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class RelabelTest {
    @Test
    fun `the number stays between currencies with the same minor units`() {
        assertEquals(Money(10_000, "USD"), Money(10_000, "EUR").relabeled("USD"))
        assertEquals(Money(1_234, "CHF"), Money(1_234, "GBP").relabeled("CHF"))
    }

    @Test
    fun `the same currency is returned as is`() {
        val money = Money(1_234, "EUR")

        assertSame(money, money.relabeled("EUR"))
    }

    @Test
    fun `minor units are rescaled, rounding half up when digits are dropped`() {
        assertEquals(Money(12, "JPY"), Money(1_234, "EUR").relabeled("JPY"))
        assertEquals(Money(13, "JPY"), Money(1_250, "EUR").relabeled("JPY"))
        assertEquals(Money(1_200, "EUR"), Money(12, "JPY").relabeled("EUR"))
        assertEquals(Money(12_340, "BHD"), Money(1_234, "EUR").relabeled("BHD"))
        assertEquals(Money(1_235, "EUR"), Money(12_345, "BHD").relabeled("EUR"))
    }

    @Test
    fun `a result too large for a Long throws`() {
        assertThrows(ArithmeticException::class.java) { Money(Long.MAX_VALUE, "JPY").relabeled("EUR") }
    }

    @Test
    fun `relabeled default tiers are the defaults in the new currency`() {
        assertEquals(CoolingOffRules.defaults("USD"), CoolingOffRules.defaults("EUR").relabeled("USD"))
        assertEquals(CoolingOffRules.defaults("JPY"), CoolingOffRules.defaults("EUR").relabeled("JPY"))
        assertEquals(CoolingOffRules.defaults("BHD"), CoolingOffRules.defaults("JPY").relabeled("BHD"))
    }

    @Test
    fun `tiers that round to the same threshold collapse into the pricier one`() {
        val rules = rules(
            "EUR",
            0L to 3,
            40L to 7, // 0,40 € → 0 ¥
            100L to 14, // 1,00 € → 1 ¥
            149L to 21, // 1,49 € → 1 ¥
            200L to 30
        )

        assertEquals(rules("JPY", 0L to 7, 1L to 21, 2L to 30), rules.relabeled("JPY"))
    }

    private fun rules(currency: String, vararg tiers: Pair<Long, Int>): CoolingOffRules {
        val result = CoolingOffRules.from(
            tiers.map { (min, days) ->
                CoolingOffTier(Money(min, currency), Period.ofDays(days))
            }
        )
        return (result as CoolingOffRulesResult.Valid).rules
    }
}
