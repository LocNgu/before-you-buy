package io.github.locngu.beforeyoubuy.core.domain.pool

import io.github.locngu.beforeyoubuy.core.domain.money.Money
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AllowanceTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private val monthly = Money(30_000, "EUR")

    private fun settings(day: Int = 1, start: String = "2026-10-23", amount: Money = monthly) =
        AllowanceSettings(amount, day, LocalDate.parse(start))

    /** Noon in Berlin on [date], far from midnight. */
    private fun on(date: String): Instant = LocalDate.parse(date).atTime(12, 0).atZone(berlin).toInstant()

    private fun due(settings: AllowanceSettings, last: String?, now: Instant, zone: ZoneId = berlin) =
        accrualsDue(settings, last?.let(YearMonth::parse), now, zone).map(YearMonth::toString)

    @Test
    fun `the first credit is the first accrual day after setting up`() {
        // Set up on 23 Oct with the 1st: the starting balance covers October, November is the first credit.
        assertEquals(emptyList<String>(), due(settings(), null, on("2026-10-23")))
        assertEquals(emptyList<String>(), due(settings(), null, on("2026-10-31")))
        assertEquals(listOf("2026-11"), due(settings(), null, on("2026-11-01")))
    }

    @Test
    fun `setting up on the accrual day credits that day`() {
        assertEquals(listOf("2026-10"), due(settings(day = 23), null, on("2026-10-23")))
        assertEquals(listOf("2026-10"), due(settings(day = 1, start = "2026-10-01"), null, on("2026-10-01")))
    }

    @Test
    fun `an accrual day later in the start month is still that month`() {
        assertEquals(emptyList<String>(), due(settings(day = 28), null, on("2026-10-27")))
        assertEquals(listOf("2026-10"), due(settings(day = 28), null, on("2026-10-28")))
    }

    @Test
    fun `missed months are all caught up, oldest first`() {
        // Not opened from October until March.
        assertEquals(
            listOf("2026-11", "2026-12", "2027-01", "2027-02", "2027-03"),
            due(settings(), null, on("2027-03-15"))
        )
        assertEquals(listOf("2027-01", "2027-02", "2027-03"), due(settings(), "2026-12", on("2027-03-15")))
    }

    @Test
    fun `a credited month is never due again`() {
        assertEquals(emptyList<String>(), due(settings(), "2026-11", on("2026-11-01")))
        assertEquals(emptyList<String>(), due(settings(), "2026-11", on("2026-11-30")))
        assertEquals(listOf("2026-12"), due(settings(), "2026-11", on("2026-12-01")))
    }

    @Test
    fun `a later accrual day waits for that day`() {
        // Moved from the 1st to the 20th after November was credited: December is due on the 20th.
        assertEquals(emptyList<String>(), due(settings(day = 20), "2026-11", on("2026-12-19")))
        assertEquals(listOf("2026-12"), due(settings(day = 20), "2026-11", on("2026-12-20")))
    }

    @Test
    fun `an earlier accrual day credits this month once it has passed`() {
        // Moved from the 20th to the 1st on 15 Nov, with October credited: November's 1st has passed.
        assertEquals(listOf("2026-11"), due(settings(day = 1, start = "2026-10-01"), "2026-10", on("2026-11-15")))
    }

    @Test
    fun `today is the date in the device's zone`() {
        // 23:30 UTC on 31 Oct is already 1 Nov in Berlin, still 31 Oct in UTC.
        val lateEvening = Instant.parse("2026-10-31T23:30:00Z")
        assertEquals(listOf("2026-11"), due(settings(), null, lateEvening, berlin))
        assertEquals(emptyList<String>(), due(settings(), null, lateEvening, ZoneOffset.UTC))
    }

    @Test
    fun `daylight saving changes don't move the accrual day`() {
        // Berlin leaves summer time on 25 Oct 2026; one minute after midnight is the 25th.
        val justAfterMidnight = LocalDate.parse("2026-10-25").atStartOfDay(berlin).toInstant().plusSeconds(60)
        assertEquals(listOf("2026-10"), due(settings(day = 25, start = "2026-10-01"), null, justAfterMidnight))
    }

    @Test
    fun `accrual days stop at the 28th, which every month has`() {
        assertEquals(28, AllowanceSettings.clampAccrualDay(31))
        assertEquals(28, AllowanceSettings.clampAccrualDay(29))
        assertEquals(1, AllowanceSettings.clampAccrualDay(0))
        assertEquals(15, AllowanceSettings.clampAccrualDay(15))
        assertThrows(IllegalArgumentException::class.java) { settings(day = 29) }
        assertThrows(IllegalArgumentException::class.java) { settings(day = 0) }
        // February has a 28th even outside leap years.
        assertEquals(listOf("2027-02"), due(settings(day = 28), "2027-01", on("2027-02-28")))
    }

    @Test
    fun `the next accrual date follows the last credited month`() {
        assertEquals(LocalDate.parse("2026-11-01"), nextAccrualDate(settings(), null))
        assertEquals(LocalDate.parse("2026-10-23"), nextAccrualDate(settings(day = 23), null))
        assertEquals(LocalDate.parse("2027-01-01"), nextAccrualDate(settings(), YearMonth.parse("2026-12")))
        // An older credit than the start (e.g. set up again) doesn't move it before the start.
        assertEquals(LocalDate.parse("2026-11-01"), nextAccrualDate(settings(), YearMonth.parse("2026-01")))
    }

    @Test
    fun `a negative monthly amount is a programming error`() {
        assertThrows(IllegalArgumentException::class.java) { settings(amount = Money(-1, "EUR")) }
        settings(amount = Money(0, "EUR"))
    }
}
