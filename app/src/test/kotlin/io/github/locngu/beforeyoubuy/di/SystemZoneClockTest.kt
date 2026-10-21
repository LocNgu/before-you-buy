package io.github.locngu.beforeyoubuy.di

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemZoneClockTest {
    private val original = TimeZone.getDefault()

    @After
    fun restoreTimeZone() = TimeZone.setDefault(original)

    @Test
    fun `follows a change of the device time zone`() {
        val clock = TimeModule.provideClock(ClockOffset.None)

        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
        assertEquals(ZoneId.of("Europe/Berlin"), clock.zone)

        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        assertEquals(ZoneId.of("America/New_York"), clock.zone)
    }

    @Test
    fun `an offset moves the time and is read on every call, the zone still follows the device`() {
        var offset = Duration.ZERO
        val clock = TimeModule.provideClock { offset }

        val before = Instant.now()
        offset = Duration.ofDays(7)
        assertTrue(clock.instant() >= before.plus(Duration.ofDays(7)))

        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
        assertEquals(ZoneId.of("Europe/Berlin"), clock.zone)
    }
}
