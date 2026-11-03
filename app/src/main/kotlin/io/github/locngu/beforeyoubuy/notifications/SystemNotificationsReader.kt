package io.github.locngu.beforeyoubuy.notifications

import io.github.locngu.beforeyoubuy.core.domain.settings.SystemNotifications

/** Reads what the phone lets through of the app's notifications; [Notifier] in the app, a fake in tests. */
fun interface SystemNotificationsReader {
    fun read(): SystemNotifications
}
