package io.github.locngu.beforeyoubuy.core.domain.settings

import io.github.locngu.beforeyoubuy.core.domain.coolingoff.CoolingOffRules
import kotlinx.coroutines.flow.Flow

/**
 * The user's settings (brief §6.7). Every value has a default, so a fresh install reads complete settings.
 *
 * @property currency ISO 4217 code of the app's currency; defaults to the device region's currency, else EUR
 *   (see [defaultCurrency]).
 * @property coolingOffRules always in [currency]; [CoolingOffRules.defaults] until the user sets their own.
 */
data class Settings(
    val currency: String,
    val coolingOffRules: CoolingOffRules,
    val notifications: NotificationPrefs = NotificationPrefs(),
    val onboardingComplete: Boolean = false
) {
    init {
        require(coolingOffRules.currency == currency) {
            "Cooling-off rules in ${coolingOffRules.currency} don't match the currency $currency"
        }
    }
}

/**
 * Which notifications the user wants (brief §8). Placeholders until the notifications (#20) and Settings screens
 * use them; one switch per notification channel.
 */
data class NotificationPrefs(val waitingPeriods: Boolean = true, val reflectionReminders: Boolean = true)

/**
 * Stores [Settings]. Implemented in `:core:data` (DataStore). Until the user saves a setting, the currency follows the
 * device region; saving any setting fixes it, so the tiers saved with it keep applying.
 */
interface SettingsRepository {
    /** The current settings; emits again on every change. */
    val settings: Flow<Settings>

    /**
     * Sets the app's currency; anything not [usable][isUsableCurrency] throws. Cooling-off rules in another currency
     * no longer apply: until rules in the new currency are set, [Settings.coolingOffRules] are the defaults in it.
     */
    suspend fun setCurrency(currency: String)

    /** Stores the user's tiers. They must be in the current currency; anything else throws. */
    suspend fun setCoolingOffRules(rules: CoolingOffRules)

    suspend fun setNotificationPrefs(prefs: NotificationPrefs)

    suspend fun setOnboardingComplete(complete: Boolean)
}
