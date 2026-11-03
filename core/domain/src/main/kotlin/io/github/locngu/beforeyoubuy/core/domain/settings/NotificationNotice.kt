package io.github.locngu.beforeyoubuy.core.domain.settings

/**
 * What the phone lets through of the app's notifications, as the app reads it from the system (brief §8).
 *
 * @property appAllowed the app may notify at all: the Android 13+ permission is granted and the app's notifications
 *   aren't switched off in the system settings.
 * @property waitingPeriodsChannelOn the "Waiting periods" channel isn't switched off.
 * @property reflectionRemindersChannelOn the "Reflection reminders" channel isn't switched off.
 */
data class SystemNotifications(
    val appAllowed: Boolean = true,
    val waitingPeriodsChannelOn: Boolean = true,
    val reflectionRemindersChannelOn: Boolean = true
)

/** What Settings says under the notification switches when the phone won't show what they ask for (ADR-0054). */
sealed interface NotificationNotice {
    /** Everything switched on in the app can show, or nothing is switched on. */
    data object None : NotificationNotice

    /** The permission is denied or the app's notifications are off: nothing switched on will show. */
    data object AppBlocked : NotificationNotice

    /** The app may notify, but the channel of each switch marked here is off although the switch is on. */
    data class ChannelsOff(val waitingPeriods: Boolean, val reflectionReminders: Boolean) : NotificationNotice
}

/**
 * The notice for [prefs] given what the phone lets through ([system]). Quiet when nothing is wanted: a blocked app
 * matters only for a switch that's on. A blocked app says so once instead of per channel, since the channels can't be
 * reached until the app may notify.
 */
fun notificationNoticeOf(prefs: NotificationPrefs, system: SystemNotifications): NotificationNotice {
    if (!prefs.waitingPeriods && !prefs.reflectionReminders) return NotificationNotice.None
    if (!system.appAllowed) return NotificationNotice.AppBlocked
    val waitingOff = prefs.waitingPeriods && !system.waitingPeriodsChannelOn
    val reflectionOff = prefs.reflectionReminders && !system.reflectionRemindersChannelOn
    return if (waitingOff || reflectionOff) {
        NotificationNotice.ChannelsOff(waitingPeriods = waitingOff, reflectionReminders = reflectionOff)
    } else {
        NotificationNotice.None
    }
}
