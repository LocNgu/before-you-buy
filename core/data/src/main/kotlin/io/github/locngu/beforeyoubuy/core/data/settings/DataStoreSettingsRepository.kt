package io.github.locngu.beforeyoubuy.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.locngu.beforeyoubuy.core.domain.coolingoff.CoolingOffRules
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.core.domain.settings.NotificationPrefs
import io.github.locngu.beforeyoubuy.core.domain.settings.Settings
import io.github.locngu.beforeyoubuy.core.domain.settings.SettingsRepository
import io.github.locngu.beforeyoubuy.core.domain.settings.defaultCurrency
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * [SettingsRepository] on DataStore Preferences. Only values the user set are stored; everything else is a default
 * computed on read, so the currency follows [locale] until the user picks one. Unreadable values read as their
 * default (ADR-0032), and an unreadable file as all defaults.
 */
internal class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val locale: () -> Locale = Locale::getDefault
) : SettingsRepository {
    override val settings: Flow<Settings> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it.toSettings() }
        .distinctUntilChanged()

    override suspend fun setCurrency(currency: String) {
        Money(0, currency) // Throws for anything that isn't an ISO 4217 code.
        dataStore.edit { it[Keys.CURRENCY] = currency }
    }

    override suspend fun setCoolingOffRules(rules: CoolingOffRules) {
        dataStore.edit {
            val currency = it.currency()
            require(rules.currency == currency) { "Rules in ${rules.currency} don't match the currency $currency" }
            it[Keys.COOLING_OFF_RULES] = CoolingOffRulesJson.encode(rules)
        }
    }

    override suspend fun setNotificationPrefs(prefs: NotificationPrefs) {
        dataStore.edit {
            it[Keys.NOTIFY_WAITING_PERIODS] = prefs.waitingPeriods
            it[Keys.NOTIFY_REFLECTION_REMINDERS] = prefs.reflectionReminders
        }
    }

    override suspend fun setOnboardingComplete(complete: Boolean) {
        dataStore.edit { it[Keys.ONBOARDING_COMPLETE] = complete }
    }

    private fun Preferences.toSettings(): Settings {
        val currency = currency()
        val defaults = NotificationPrefs()
        return Settings(
            currency = currency,
            coolingOffRules = this[Keys.COOLING_OFF_RULES]?.let { CoolingOffRulesJson.decode(it, currency) }
                ?: CoolingOffRules.defaults(currency),
            notifications = NotificationPrefs(
                waitingPeriods = this[Keys.NOTIFY_WAITING_PERIODS] ?: defaults.waitingPeriods,
                reflectionReminders = this[Keys.NOTIFY_REFLECTION_REMINDERS] ?: defaults.reflectionReminders
            ),
            onboardingComplete = this[Keys.ONBOARDING_COMPLETE] ?: false
        )
    }

    private fun Preferences.currency(): String =
        this[Keys.CURRENCY]?.takeIf { runCatching { Money(0, it) }.isSuccess } ?: defaultCurrency(locale())

    private object Keys {
        val CURRENCY = stringPreferencesKey("currency")
        val COOLING_OFF_RULES = stringPreferencesKey("cooling_off_rules")
        val NOTIFY_WAITING_PERIODS = booleanPreferencesKey("notify_waiting_periods")
        val NOTIFY_REFLECTION_REMINDERS = booleanPreferencesKey("notify_reflection_reminders")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
    }
}
