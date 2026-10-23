package io.github.locngu.beforeyoubuy.core.domain.coolingoff

import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.core.domain.money.relabeled
import java.time.Period

/** Items priced at [minPriceInclusive] or more (up to the next tier) cool off for [period]. */
data class CoolingOffTier(val minPriceInclusive: Money, val period: Period)

/**
 * The price tiers that decide how long a want cools off (brief §4.2, ADR-0005). Always valid: build one with
 * [from] (user input, e.g. Settings) or [defaults].
 *
 * Changing the rules also moves the end of items that are still cooling off, in both directions: see
 * [coolingEndAfterRulesChange] (ADR-0031).
 */
class CoolingOffRules private constructor(val tiers: List<CoolingOffTier>) {
    /** The currency of every threshold; prices must be in it. */
    val currency: String get() = tiers.first().minPriceInclusive.currency

    /**
     * The same tiers with thresholds [relabeled][io.github.locngu.beforeyoubuy.core.domain.money.relabeled] to
     * [currency] (ADR-0033): the numbers stay, the periods stay. If rounding to fewer minor-unit digits makes
     * thresholds equal (0,40 € and 0 € both become 0 ¥), the pricier tier wins, as it would for a price at that
     * threshold; so the result is always valid.
     */
    fun relabeled(currency: String): CoolingOffRules {
        val relabeled = tiers.map { it.copy(minPriceInclusive = it.minPriceInclusive.relabeled(currency)) }
        return CoolingOffRules(relabeled.asReversed().distinctBy { it.minPriceInclusive }.asReversed())
    }

    override fun equals(other: Any?): Boolean = other is CoolingOffRules && other.tiers == tiers

    override fun hashCode(): Int = tiers.hashCode()

    override fun toString(): String = "CoolingOffRules($tiers)"

    companion object {
        /**
         * Checks [tiers]: at least one, the first starting at 0, thresholds strictly ascending, and every period a
         * whole number of days, at least 1 and not shorter than the previous tier's. Reports every problem at once,
         * so Settings can mark each tier. Thresholds in different currencies are a programming error and throw.
         */
        fun from(tiers: List<CoolingOffTier>): CoolingOffRulesResult {
            require(tiers.map { it.minPriceInclusive.currency }.distinct().size <= 1) {
                "All thresholds must be in one currency, were ${tiers.map { it.minPriceInclusive.currency }}"
            }
            val errors = validate(tiers)
            return if (errors.isEmpty()) {
                CoolingOffRulesResult.Valid(CoolingOffRules(tiers.toList()))
            } else {
                CoolingOffRulesResult.Invalid(errors)
            }
        }

        /** [DEFAULT_COOLING_OFF_TIERS] with thresholds in [currency]'s major units (100 → 100.00 € or 100 ¥). */
        fun defaults(currency: String): CoolingOffRules {
            val tiers = DEFAULT_COOLING_OFF_TIERS.map {
                CoolingOffTier(Money.ofWholeUnits(it.minMajorUnitsInclusive, currency), it.period)
            }
            return CoolingOffRules(tiers)
        }

        private fun validate(tiers: List<CoolingOffTier>): List<CoolingOffRulesError> {
            if (tiers.isEmpty()) return listOf(CoolingOffRulesError.NoTiers)
            val errors = mutableListOf<CoolingOffRulesError>()
            if (tiers.first().minPriceInclusive.minorUnits != 0L) errors += CoolingOffRulesError.FirstTierNotAtZero
            tiers.forEachIndexed { index, tier ->
                val days = tier.period.wholeDaysOrNull()
                when {
                    days == null -> errors += CoolingOffRulesError.PeriodNotInDays(index)
                    days < 1 -> errors += CoolingOffRulesError.PeriodUnderOneDay(index)
                }
                if (index == 0) return@forEachIndexed
                val previous = tiers[index - 1]
                if (tier.minPriceInclusive <= previous.minPriceInclusive) {
                    errors += CoolingOffRulesError.ThresholdNotAscending(index)
                }
                val previousDays = previous.period.wholeDaysOrNull()
                if (days != null && previousDays != null && days < previousDays) {
                    errors += CoolingOffRulesError.PeriodShorterThanPrevious(index)
                }
            }
            return errors
        }

        /** Months and years vary in length, so they can't be compared or checked against "at least 1 day". */
        private fun Period.wholeDaysOrNull(): Int? = if (years == 0 && months == 0) days else null
    }
}

/** A default tier; its threshold is in major units of whatever currency the user has. */
data class DefaultCoolingOffTier(val minMajorUnitsInclusive: Long, val period: Period)

/**
 * The default tiers (brief §4.2, ADR-0005): < 100 → 7 days, 100 – < 500 → 30 days, 500 – < 1,000 → 60 days,
 * ≥ 1,000 → 90 days. The one source for Settings and onboarding; use [CoolingOffRules.defaults] for usable rules.
 */
val DEFAULT_COOLING_OFF_TIERS: List<DefaultCoolingOffTier> = listOf(
    DefaultCoolingOffTier(0, Period.ofDays(7)),
    DefaultCoolingOffTier(100, Period.ofDays(30)),
    DefaultCoolingOffTier(500, Period.ofDays(60)),
    DefaultCoolingOffTier(1_000, Period.ofDays(90))
)

/** Outcome of [CoolingOffRules.from]. */
sealed interface CoolingOffRulesResult {
    data class Valid(val rules: CoolingOffRules) : CoolingOffRulesResult

    /** [errors] lists every problem, in tier order. */
    data class Invalid(val errors: List<CoolingOffRulesError>) : CoolingOffRulesResult
}

/** Why a set of tiers was rejected. [index] is the offending tier's position. */
sealed interface CoolingOffRulesError {
    data object NoTiers : CoolingOffRulesError

    /** The first tier must start at a price of 0, so every price has a period. */
    data object FirstTierNotAtZero : CoolingOffRulesError

    /** The tier doesn't start above the previous one (out of order, or the same threshold twice). */
    data class ThresholdNotAscending(val index: Int) : CoolingOffRulesError

    /** The period uses months or years; periods are whole calendar days. */
    data class PeriodNotInDays(val index: Int) : CoolingOffRulesError

    data class PeriodUnderOneDay(val index: Int) : CoolingOffRulesError

    /** A pricier tier must not cool off for less time than a cheaper one. */
    data class PeriodShorterThanPrevious(val index: Int) : CoolingOffRulesError
}
