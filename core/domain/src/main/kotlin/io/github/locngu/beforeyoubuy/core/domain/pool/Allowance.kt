package io.github.locngu.beforeyoubuy.core.domain.pool

import io.github.locngu.beforeyoubuy.core.domain.money.Money
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * The monthly fun money (brief §4.5, ADR-0010, ADR-0045): [monthlyAmount] is credited to the pool on [accrualDay]
 * of every month from [startDate] on, with no upkeep.
 *
 * @property accrualDay 1–[MAX_ACCRUAL_DAY], so every month has it; [clampAccrualDay] fits other values in.
 * @property startDate the day the allowance was set up. The first credit is the first accrual day on or after it;
 *   the starting balance covers the time before.
 */
data class AllowanceSettings(val monthlyAmount: Money, val accrualDay: Int, val startDate: LocalDate) {
    init {
        require(monthlyAmount.minorUnits >= 0) { "The monthly amount can't be negative, was $monthlyAmount" }
        require(accrualDay in 1..MAX_ACCRUAL_DAY) { "Accrual day must be 1–$MAX_ACCRUAL_DAY, was $accrualDay" }
    }

    /** The day [month]'s amount is due. */
    fun accrualDateIn(month: YearMonth): LocalDate = month.atDay(accrualDay)

    companion object {
        /** The last day every month has; the 29th–31st aren't offered. */
        const val MAX_ACCRUAL_DAY = 28

        const val DEFAULT_ACCRUAL_DAY = 1

        /** [day] fitted into 1–[MAX_ACCRUAL_DAY], e.g. for a stored value from elsewhere: the 31st becomes the 28th. */
        fun clampAccrualDay(day: Int): Int = day.coerceIn(1, MAX_ACCRUAL_DAY)
    }
}

/**
 * The months whose amount is due at [now] in [zone] but not credited yet, oldest first (brief §4.5).
 *
 * A month is due once its accrual date is on or after [AllowanceSettings.startDate] and not after today. Every due
 * month after [lastAccruedMonth] (the newest credited, null if none) is returned, so months the app wasn't opened
 * are caught up and none is credited twice. "Today" is the date in [zone], so a credit never lands a day early or
 * late around midnight in another zone (ADR-0018).
 *
 * The caller credits each with the [AllowanceSettings.monthlyAmount] in force; to change the settings, credit what's
 * due under the old ones first, so a new amount applies from the next accrual on.
 */
fun accrualsDue(
    settings: AllowanceSettings,
    lastAccruedMonth: YearMonth?,
    now: Instant,
    zone: ZoneId
): List<YearMonth> {
    val today = now.atZone(zone).toLocalDate()
    val thisMonth = YearMonth.from(today)
    val until = if (settings.accrualDateIn(thisMonth) <= today) thisMonth else thisMonth.minusMonths(1)
    return generateSequence(firstUncredited(settings, lastAccruedMonth)) { it.plusMonths(1) }
        .takeWhile { it <= until }
        .toList()
}

/**
 * When the next amount is added, for showing ("Next added on 1 Nov 2026"): the accrual date of the first month after
 * [lastAccruedMonth] that [accrualsDue] would credit. Call after accruing, so it isn't a date already past.
 */
fun nextAccrualDate(settings: AllowanceSettings, lastAccruedMonth: YearMonth?): LocalDate =
    settings.accrualDateIn(firstUncredited(settings, lastAccruedMonth))

private fun firstUncredited(settings: AllowanceSettings, lastAccruedMonth: YearMonth?): YearMonth {
    val startMonth = YearMonth.from(settings.startDate)
    val startMonthDue = settings.accrualDateIn(startMonth) >= settings.startDate
    val firstMonth = if (startMonthDue) startMonth else startMonth.plusMonths(1)
    return if (lastAccruedMonth == null || lastAccruedMonth < firstMonth) firstMonth else lastAccruedMonth.plusMonths(1)
}
