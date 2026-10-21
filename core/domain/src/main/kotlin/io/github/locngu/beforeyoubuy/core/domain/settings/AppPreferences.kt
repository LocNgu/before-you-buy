package io.github.locngu.beforeyoubuy.core.domain.settings

import kotlinx.coroutines.flow.Flow

/**
 * The user's preferences that don't affect items (brief §6.7). Every value has a default, so a fresh install reads
 * complete preferences.
 *
 * @property notificationPermissionAsked the app has explained notifications and offered to ask for the permission
 *   (Android 13+), whatever the answer. It asks only once.
 */
data class AppPreferences(
    val notifications: NotificationPrefs = NotificationPrefs(),
    val onboardingComplete: Boolean = false,
    val notificationPermissionAsked: Boolean = false
)

/**
 * Which notifications the user wants (brief §8); one switch per notification channel. The daily check reads them;
 * the Settings screen (M3) will change them. Until then both are on, and the system's per-channel switches apply.
 */
data class NotificationPrefs(val waitingPeriods: Boolean = true, val reflectionReminders: Boolean = true)

/** Stores [AppPreferences]. Implemented in `:core:data` (DataStore). */
interface PreferencesRepository {
    /** The current preferences; emits again on every change. */
    val preferences: Flow<AppPreferences>

    suspend fun setNotificationPrefs(prefs: NotificationPrefs)

    suspend fun setOnboardingComplete(complete: Boolean)

    suspend fun setNotificationPermissionAsked(asked: Boolean)
}
