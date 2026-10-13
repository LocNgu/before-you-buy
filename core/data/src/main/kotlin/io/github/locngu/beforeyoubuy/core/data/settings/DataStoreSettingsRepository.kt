package io.github.locngu.beforeyoubuy.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.locngu.beforeyoubuy.core.domain.coolingoff.CoolingOffRules
import io.github.locngu.beforeyoubuy.core.domain.settings.NotificationPrefs
import io.github.locngu.beforeyoubuy.core.domain.settings.Settings
import io.github.locngu.beforeyoubuy.core.domain.settings.SettingsRepository
import io.github.locngu.beforeyoubuy.core.domain.settings.defaultCurrency
import io.github.locngu.beforeyoubuy.core.domain.settings.isUsableCurrency
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen

/**
 * [SettingsRepository] on DataStore Preferences (ADR-0032). Only values the user set are stored; everything else is a
 * default computed on read. The currency follows [locale] until the first setting is saved, which also stores the
 * currency, so saved tiers never lose their currency to a later region change. Unreadable values read as their
 * default; a corrupt file is replaced by an empty one, and a failed read is retried without emitting anything, so a
 * returning user never sees defaults (e.g. onboarding) because of a disk hiccup.
 */
internal class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val locale: () -> Locale = Locale::getDefault
) : SettingsRepository {
    override val settings: Flow<Settings> = dataStore.data
        // Unlike catch, retryWhen keeps the flow alive, so later changes still arrive after a failed read.
        .retryWhen { cause, _ ->
            if (cause !is IOException) return@retryWhen false
            delay(READ_RETRY_DELAY_MILLIS)
            true
        }
        .map { it.toSettings() }
        .distinctUntilChanged()

    override suspend fun setCurrency(currency: String) {
        require(isUsableCurrency(currency)) { "Not a usable ISO 4217 currency: \"$currency\"" }
        edit { prefs, _ -> prefs[Keys.CURRENCY] = currency }
    }

    override suspend fun setCoolingOffRules(rules: CoolingOffRules) = edit { prefs, currency ->
        require(rules.currency == currency) { "Rules in ${rules.currency} don't match the currency $currency" }
        prefs[Keys.COOLING_OFF_RULES] = CoolingOffRulesJson.encode(rules)
    }

    override suspend fun setNotificationPrefs(prefs: NotificationPrefs) = edit { stored, _ ->
        stored[Keys.NOTIFY_WAITING_PERIODS] = prefs.waitingPeriods
        stored[Keys.NOTIFY_REFLECTION_REMINDERS] = prefs.reflectionReminders
    }

    override suspend fun setOnboardingComplete(complete: Boolean) = edit { prefs, _ ->
        prefs[Keys.ONBOARDING_COMPLETE] = complete
    }

    /** Every write stores the currency in effect, so it stops following the region from now on. */
    private suspend fun edit(change: (MutablePreferences, currency: String) -> Unit) {
        dataStore.edit { prefs ->
            val currency = prefs.currency()
            prefs[Keys.CURRENCY] = currency
            change(prefs, currency)
        }
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
        this[Keys.CURRENCY]?.takeIf(::isUsableCurrency) ?: defaultCurrency(locale())

    private object Keys {
        val CURRENCY = stringPreferencesKey("currency")
        val COOLING_OFF_RULES = stringPreferencesKey("cooling_off_rules")
        val NOTIFY_WAITING_PERIODS = booleanPreferencesKey("notify_waiting_periods")
        val NOTIFY_REFLECTION_REMINDERS = booleanPreferencesKey("notify_reflection_reminders")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
    }

    companion object {
        private const val READ_RETRY_DELAY_MILLIS = 1_000L

        /**
         * The settings DataStore on [file]. Only one may be active per file and process. A corrupt file is replaced
         * by an empty one (all defaults) rather than making every read and write fail.
         */
        fun createDataStore(
            scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            file: () -> File
        ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = scope,
            produceFile = file
        )
    }
}
