package io.github.locngu.beforeyoubuy.core.domain.settings

import io.github.locngu.beforeyoubuy.core.domain.coolingoff.CoolingOffRules
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SettingsTest {
    @Test
    fun `default currency is the region's currency`() {
        assertEquals("EUR", defaultCurrency(Locale.forLanguageTag("de-DE")))
        assertEquals("USD", defaultCurrency(Locale.forLanguageTag("en-US")))
        assertEquals("JPY", defaultCurrency(Locale.forLanguageTag("ja-JP")))
        assertEquals("CHF", defaultCurrency(Locale.forLanguageTag("de-CH")))
    }

    @Test
    fun `default currency falls back to EUR without a usable region`() {
        assertEquals("EUR", defaultCurrency(Locale.forLanguageTag("en")))
        assertEquals("EUR", defaultCurrency(Locale.ROOT))
        // Antarctica has no currency.
        assertEquals("EUR", defaultCurrency(Locale.forLanguageTag("en-AQ")))
    }

    @Test
    fun `defaults are notifications on and onboarding not done`() {
        val settings = Settings("EUR", CoolingOffRules.defaults("EUR"))

        assertEquals(NotificationPrefs(waitingPeriods = true, reflectionReminders = true), settings.notifications)
        assertEquals(false, settings.onboardingComplete)
    }

    @Test
    fun `rules must be in the settings' currency`() {
        assertThrows(IllegalArgumentException::class.java) { Settings("EUR", CoolingOffRules.defaults("USD")) }
    }
}
