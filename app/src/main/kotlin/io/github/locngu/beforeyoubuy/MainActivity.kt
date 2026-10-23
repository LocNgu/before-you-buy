package io.github.locngu.beforeyoubuy

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.locngu.beforeyoubuy.navigation.AppNavigation
import io.github.locngu.beforeyoubuy.notifications.NotificationIntents
import io.github.locngu.beforeyoubuy.notifications.NotificationTap
import io.github.locngu.beforeyoubuy.ui.theme.BeforeYouBuyTheme
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // A notification tap waiting for the navigation to show it.
    private val notificationTap = MutableStateFlow<NotificationTap?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // After a configuration change the intent is the same tap again, and the back stack was restored already.
        if (savedInstanceState == null) openFromNotification(intent)
        setContent {
            val tap by notificationTap.collectAsStateWithLifecycle()
            BeforeYouBuyTheme {
                AppNavigation(notificationTap = tap, onNotificationTapShown = { notificationTap.value = null })
            }
        }
    }

    override fun onStart() {
        super.onStart()
        (application as BeforeYouBuyApplication).onAppForeground()
    }

    // The app was open already: a tap brings it forward with a new intent (FLAG_ACTIVITY_SINGLE_TOP).
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openFromNotification(intent)
    }

    private fun openFromNotification(intent: Intent?) {
        val tap = NotificationIntents.tapOf(intent) ?: return
        // An action ("Still want it") doesn't remove its notification by itself, unlike a tap on it.
        NotificationManagerCompat.from(this).cancel(tap.notificationId)
        notificationTap.value = tap
    }
}
