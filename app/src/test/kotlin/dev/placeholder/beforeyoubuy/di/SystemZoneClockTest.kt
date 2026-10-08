package dev.placeholder.beforeyoubuy.di

import java.time.ZoneId
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class SystemZoneClockTest {
    private val original = TimeZone.getDefault()

    @After
    fun restoreTimeZone() = TimeZone.setDefault(original)

    @Test
    fun `follows a change of the device time zone`() {
        val clock = TimeModule.provideClock()

        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
        assertEquals(ZoneId.of("Europe/Berlin"), clock.zone)

        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        assertEquals(ZoneId.of("America/New_York"), clock.zone)
    }
}
