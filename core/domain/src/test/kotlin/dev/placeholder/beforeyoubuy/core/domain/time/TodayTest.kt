package dev.placeholder.beforeyoubuy.core.domain.time

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class TodayTest {
    private val instant = Instant.parse("2026-03-28T23:30:00Z")

    @Test
    fun `today is the calendar date in the clock's zone`() {
        assertEquals(LocalDate.of(2026, 3, 28), Clock.fixed(instant, ZoneId.of("UTC")).today())
        assertEquals(LocalDate.of(2026, 3, 29), Clock.fixed(instant, ZoneId.of("Europe/Berlin")).today())
    }
}
