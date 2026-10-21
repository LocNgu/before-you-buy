package io.github.locngu.beforeyoubuy.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles "Let it go" on a notification: records the reject without opening the app, then removes the notification.
 * No confirmation notification: the notification going away is the answer, calmly (ADR-0012).
 */
class LetItGoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val request = NotificationIntents.letItGoOf(intent) ?: return
        val app = context.applicationContext
        val pending = goAsync()
        scope.launch {
            try {
                handle(app, request)
            } finally {
                pending.finish()
            }
        }
    }

    /** The work of [onReceive], outside `goAsync()` so it can be tested (docs/architecture.md, Pitfalls). */
    internal suspend fun handle(
        context: Context,
        request: LetItGoRequest,
        letItGo: LetItGo = EntryPointAccessors.fromApplication(context, NotificationsEntryPoint::class.java).letItGo()
    ) {
        try {
            letItGo.run(request.itemId)
        } finally {
            // Also when the item had ended meanwhile: the notification is out of date either way.
            NotificationManagerCompat.from(context).cancel(request.notificationId)
        }
    }

    private companion object {
        // Outlives the receiver; goAsync() keeps the process alive until finish().
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
