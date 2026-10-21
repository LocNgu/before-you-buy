package io.github.locngu.beforeyoubuy.core.domain.settings

import io.github.locngu.beforeyoubuy.core.domain.coolingoff.CoolingOffRules
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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
    fun `usable currencies are real money with fixed minor units`() {
        listOf("EUR", "USD", "JPY", "BHD", "CHF").forEach { assertTrue(it, isUsableCurrency(it)) }
        listOf("XXX", "XTS", "XAU", "eur", "Euro", "", "ABC").forEach { assertFalse(it, isUsableCurrency(it)) }
    }

    @Test
    fun `preference defaults are notifications on, onboarding not done and the permission not asked yet`() {
        val preferences = AppPreferences()

        assertEquals(NotificationPrefs(waitingPeriods = true, reflectionReminders = true), preferences.notifications)
        assertEquals(false, preferences.onboardingComplete)
        assertEquals(false, preferences.notificationPermissionAsked)
    }

    @Test
    fun `a policy's rules must be in its currency`() {
        assertThrows(IllegalArgumentException::class.java) { Policy("EUR", CoolingOffRules.defaults("USD")) }
    }
}
