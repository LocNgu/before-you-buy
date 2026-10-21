package io.github.locngu.beforeyoubuy.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import io.github.locngu.beforeyoubuy.core.domain.settings.AppPreferences
import io.github.locngu.beforeyoubuy.core.domain.settings.NotificationPrefs
import io.github.locngu.beforeyoubuy.core.domain.settings.PreferencesRepository
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen

/**
 * [PreferencesRepository] on DataStore Preferences (ADR-0032). Only values the user set are stored; everything else
 * is a default computed on read. A corrupt file is replaced by an empty one, and a failed read is retried without
 * emitting anything, so a returning user never sees defaults (e.g. onboarding) because of a disk hiccup.
 */
internal class DataStorePreferencesRepository(private val dataStore: DataStore<Preferences>) : PreferencesRepository {
    override val preferences: Flow<AppPreferences> = dataStore.data
        // Unlike catch, retryWhen keeps the flow alive, so later changes still arrive after a failed read.
        .retryWhen { cause, _ ->
            if (cause !is IOException) return@retryWhen false
            delay(READ_RETRY_DELAY_MILLIS)
            true
        }
        .map { it.toAppPreferences() }
        .distinctUntilChanged()

    override suspend fun setNotificationPrefs(prefs: NotificationPrefs) {
        dataStore.edit {
            it[Keys.NOTIFY_WAITING_PERIODS] = prefs.waitingPeriods
            it[Keys.NOTIFY_REFLECTION_REMINDERS] = prefs.reflectionReminders
        }
    }

    override suspend fun setOnboardingComplete(complete: Boolean) {
        dataStore.edit { it[Keys.ONBOARDING_COMPLETE] = complete }
    }

    override suspend fun setNotificationPermissionAsked(asked: Boolean) {
        dataStore.edit { it[Keys.NOTIFICATION_PERMISSION_ASKED] = asked }
    }

    private fun Preferences.toAppPreferences(): AppPreferences {
        val defaults = AppPreferences()
        return AppPreferences(
            notifications = NotificationPrefs(
                waitingPeriods = this[Keys.NOTIFY_WAITING_PERIODS] ?: defaults.notifications.waitingPeriods,
                reflectionReminders = this[Keys.NOTIFY_REFLECTION_REMINDERS]
                    ?: defaults.notifications.reflectionReminders
            ),
            onboardingComplete = this[Keys.ONBOARDING_COMPLETE] ?: defaults.onboardingComplete,
            notificationPermissionAsked = this[Keys.NOTIFICATION_PERMISSION_ASKED]
                ?: defaults.notificationPermissionAsked
        )
    }

    private object Keys {
        val NOTIFY_WAITING_PERIODS = booleanPreferencesKey("notify_waiting_periods")
        val NOTIFY_REFLECTION_REMINDERS = booleanPreferencesKey("notify_reflection_reminders")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val NOTIFICATION_PERMISSION_ASKED = booleanPreferencesKey("notification_permission_asked")
    }

    companion object {
        private const val READ_RETRY_DELAY_MILLIS = 1_000L

        /**
         * The preferences DataStore on [file]. Only one may be active per file and process. A corrupt file is
         * replaced by an empty one (all defaults) rather than making every read and write fail.
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
