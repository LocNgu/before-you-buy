package io.github.locngu.beforeyoubuy.core.domain.coolingoff

import io.github.locngu.beforeyoubuy.core.domain.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Period

class CoolingOffRulesTest {
    private fun eur(minorUnits: Long) = Money(minorUnits, "EUR")

    private fun tier(minorUnits: Long, days: Int) = CoolingOffTier(eur(minorUnits), Period.ofDays(days))

    private fun errorsOf(vararg tiers: CoolingOffTier): List<CoolingOffRulesError> =
        (CoolingOffRules.from(tiers.toList()) as CoolingOffRulesResult.Invalid).errors

    @Test
    fun `defaults are the brief's tiers in the currency's major units`() {
        assertEquals(
            listOf(tier(0, 7), tier(10_000, 30), tier(50_000, 60), tier(100_000, 90)),
            CoolingOffRules.defaults("EUR").tiers
        )
        // No minor units: 100 ¥ is stored as 100.
        assertEquals(
            listOf(0L, 100L, 500L, 1_000L),
            CoolingOffRules.defaults("JPY").tiers.map { it.minPriceInclusive.minorUnits }
        )
    }

    @Test
    fun `defaults pass validation`() {
        val defaults = CoolingOffRules.defaults("EUR")
        assertEquals(CoolingOffRulesResult.Valid(defaults), CoolingOffRules.from(defaults.tiers))
    }

    @Test
    fun `a single tier at zero and equal periods are valid`() {
        assertEquals(CoolingOffRulesResult.Valid::class, CoolingOffRules.from(listOf(tier(0, 1)))::class)
        assertEquals(
            CoolingOffRulesResult.Valid::class,
            CoolingOffRules.from(listOf(tier(0, 30), tier(10_000, 30)))::class
        )
    }

    @Test
    fun `no tiers is rejected`() {
        assertEquals(listOf(CoolingOffRulesError.NoTiers), errorsOf())
    }

    @Test
    fun `the first tier must start at zero`() {
        assertEquals(listOf(CoolingOffRulesError.FirstTierNotAtZero), errorsOf(tier(1, 7), tier(10_000, 30)))
    }

    @Test
    fun `thresholds must be strictly ascending`() {
        assertEquals(
            listOf(CoolingOffRulesError.ThresholdNotAscending(2)),
            errorsOf(tier(0, 7), tier(50_000, 30), tier(10_000, 60))
        )
        assertEquals(
            listOf(CoolingOffRulesError.ThresholdNotAscending(1)),
            errorsOf(tier(0, 7), tier(0, 30))
        )
    }

    @Test
    fun `periods must be at least one day`() {
        assertEquals(listOf(CoolingOffRulesError.PeriodUnderOneDay(0)), errorsOf(tier(0, 0)))
        assertEquals(listOf(CoolingOffRulesError.PeriodUnderOneDay(0)), errorsOf(tier(0, -3)))
    }

    @Test
    fun `periods must be whole days, not months or years`() {
        assertEquals(
            listOf(CoolingOffRulesError.PeriodNotInDays(1)),
            errorsOf(tier(0, 7), CoolingOffTier(eur(10_000), Period.ofMonths(1)))
        )
        assertEquals(
            listOf(CoolingOffRulesError.PeriodNotInDays(0)),
            errorsOf(CoolingOffTier(eur(0), Period.ofYears(1)))
        )
    }

    @Test
    fun `periods must not decrease with price`() {
        assertEquals(
            listOf(CoolingOffRulesError.PeriodShorterThanPrevious(2)),
            errorsOf(tier(0, 7), tier(10_000, 30), tier(50_000, 29))
        )
    }

    @Test
    fun `every problem is reported at once, in tier order`() {
        assertEquals(
            listOf(
                CoolingOffRulesError.FirstTierNotAtZero,
                CoolingOffRulesError.PeriodUnderOneDay(1),
                CoolingOffRulesError.ThresholdNotAscending(1),
                CoolingOffRulesError.PeriodShorterThanPrevious(1)
            ),
            errorsOf(tier(500, 7), tier(100, 0))
        )
    }

    @Test
    fun `thresholds in different currencies are a programming error`() {
        assertThrows(IllegalArgumentException::class.java) {
            CoolingOffRules.from(listOf(tier(0, 7), CoolingOffTier(Money(10_000, "USD"), Period.ofDays(30))))
        }
    }

    @Test
    fun `rules keep their own copy of the tiers`() {
        val tiers = mutableListOf(tier(0, 7))
        val rules = (CoolingOffRules.from(tiers) as CoolingOffRulesResult.Valid).rules
        tiers += tier(1, 0)
        assertEquals(listOf(tier(0, 7)), rules.tiers)
    }
}
