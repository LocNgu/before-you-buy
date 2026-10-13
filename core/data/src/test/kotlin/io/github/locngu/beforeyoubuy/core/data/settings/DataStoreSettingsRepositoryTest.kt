package io.github.locngu.beforeyoubuy.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.test
import io.github.locngu.beforeyoubuy.core.domain.coolingoff.CoolingOffRules
import io.github.locngu.beforeyoubuy.core.domain.coolingoff.CoolingOffRulesResult
import io.github.locngu.beforeyoubuy.core.domain.coolingoff.CoolingOffTier
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.core.domain.settings.NotificationPrefs
import io.github.locngu.beforeyoubuy.core.domain.settings.Settings
import java.io.File
import java.time.Period
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreSettingsRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var locale: Locale = Locale.forLanguageTag("de-DE")
    private val file by lazy { File(folder.root, "settings.preferences_pb") }

    /** One DataStore per file and process: a second active one on the same file throws. */
    private fun storeIn(scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope) { file }

    private fun TestScope.repository(store: DataStore<Preferences> = storeIn(backgroundScope)) =
        DataStoreSettingsRepository(store) { locale }

    private val eurRules = rules("EUR")

    @Test
    fun `a fresh install reads defaults with the region's currency`() = runTest {
        val repository = repository()
        assertEquals(
            Settings("EUR", CoolingOffRules.defaults("EUR"), NotificationPrefs(), onboardingComplete = false),
            repository.settings.first()
        )

        locale = Locale.forLanguageTag("en-US")
        assertEquals(
            Settings("USD", CoolingOffRules.defaults("USD"), NotificationPrefs(), onboardingComplete = false),
            repository.settings.first()
        )
    }

    @Test
    fun `without a usable region the currency is EUR`() = runTest {
        locale = Locale.forLanguageTag("en")

        assertEquals("EUR", repository().settings.first().currency)
    }

    @Test
    fun `a chosen currency is kept when the region changes`() = runTest {
        val repository = repository()
        repository.setCurrency("CHF")

        locale = Locale.forLanguageTag("en-US")

        assertEquals("CHF", repository.settings.first().currency)
    }

    @Test
    fun `setCurrency refuses anything but an ISO 4217 code`() = runTest {
        val repository = repository()

        assertThrowsIllegalArgument { repository.setCurrency("Euro") }
        assertThrowsIllegalArgument { repository.setCurrency("eur") }
        assertEquals("EUR", repository.settings.first().currency)
    }

    @Test
    fun `stores the user's cooling-off rules`() = runTest {
        val repository = repository()
        repository.setCoolingOffRules(eurRules)

        assertEquals(eurRules, repository.settings.first().coolingOffRules)
    }

    @Test
    fun `refuses rules in another currency`() = runTest {
        val repository = repository()

        assertThrowsIllegalArgument { repository.setCoolingOffRules(rules("USD")) }
        assertEquals(CoolingOffRules.defaults("EUR"), repository.settings.first().coolingOffRules)
    }

    @Test
    fun `after a currency change the defaults in the new currency apply`() = runTest {
        val repository = repository()
        repository.setCoolingOffRules(eurRules)

        repository.setCurrency("USD")
        assertEquals(CoolingOffRules.defaults("USD"), repository.settings.first().coolingOffRules)

        repository.setCurrency("EUR")
        assertEquals(eurRules, repository.settings.first().coolingOffRules)
    }

    @Test
    fun `unreadable stored values read as defaults`() = runTest {
        val store = storeIn(backgroundScope)
        store.edit {
            it[stringPreferencesKey("currency")] = "Euro"
            it[stringPreferencesKey("cooling_off_rules")] = "{broken"
        }

        val settings = repository(store).settings.first()

        assertEquals("EUR", settings.currency)
        assertEquals(CoolingOffRules.defaults("EUR"), settings.coolingOffRules)
    }

    @Test
    fun `stores notification prefs and the onboarding flag`() = runTest {
        val repository = repository()
        val prefs = NotificationPrefs(waitingPeriods = false, reflectionReminders = true)

        repository.setNotificationPrefs(prefs)
        repository.setOnboardingComplete(true)

        val settings = repository.settings.first()
        assertEquals(prefs, settings.notifications)
        assertEquals(true, settings.onboardingComplete)
    }

    @Test
    fun `settings emit on every change`() = runTest {
        val repository = repository()

        repository.settings.test {
            assertEquals(false, awaitItem().onboardingComplete)
            repository.setOnboardingComplete(true)
            assertEquals(true, awaitItem().onboardingComplete)
        }
    }

    @Test
    fun `settings survive a restart`() = runTest {
        val firstRun = Job()
        DataStoreSettingsRepository(storeIn(CoroutineScope(Dispatchers.IO + firstRun))) { locale }.apply {
            setCurrency("JPY")
            setCoolingOffRules(rules("JPY"))
            setOnboardingComplete(true)
        }
        firstRun.cancelAndJoin() // Closes the file, like the process ending.

        val settings = repository().settings.first()

        assertEquals("JPY", settings.currency)
        assertEquals(rules("JPY"), settings.coolingOffRules)
        assertEquals(true, settings.onboardingComplete)
    }

    private suspend fun assertThrowsIllegalArgument(block: suspend () -> Unit) {
        val thrown = runCatching { block() }.exceptionOrNull()
        assertTrue("Expected IllegalArgumentException, was $thrown", thrown is IllegalArgumentException)
    }

    private fun rules(currency: String) = (
        CoolingOffRules.from(
            listOf(
                CoolingOffTier(Money(0, currency), Period.ofDays(3)),
                CoolingOffTier(Money(10_000, currency), Period.ofDays(21))
            )
        ) as CoolingOffRulesResult.Valid
        ).rules
}
