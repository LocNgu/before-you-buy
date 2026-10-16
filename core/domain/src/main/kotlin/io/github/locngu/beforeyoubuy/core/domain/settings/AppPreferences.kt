package io.github.locngu.beforeyoubuy.core.domain.settings

import kotlinx.coroutines.flow.Flow

/**
 * The user's preferences that don't affect items (brief §6.7). Every value has a default, so a fresh install reads
 * complete preferences.
 */
data class AppPreferences(
    val notifications: NotificationPrefs = NotificationPrefs(),
    val onboardingComplete: Boolean = false
)

/**
 * Which notifications the user wants (brief §8). Placeholders until the notifications (#20) and Settings screens
 * use them; one switch per notification channel.
 */
data class NotificationPrefs(val waitingPeriods: Boolean = true, val reflectionReminders: Boolean = true)

/** Stores [AppPreferences]. Implemented in `:core:data` (DataStore). */
interface PreferencesRepository {
    /** The current preferences; emits again on every change. */
    val preferences: Flow<AppPreferences>

    suspend fun setNotificationPrefs(prefs: NotificationPrefs)

    suspend fun setOnboardingComplete(complete: Boolean)
}
