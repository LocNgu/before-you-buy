package io.github.locngu.beforeyoubuy

import android.Manifest
import org.robolectric.Shadows.shadowOf

/**
 * Application for Robolectric tests (src/test/resources/robolectric.properties): app-start and app-foreground work
 * is switched off so start-up coroutines can't race test fixtures (docs/architecture.md, Pitfalls).
 *
 * Notifications are allowed, as if the user had said yes, so the one-time permission prompt doesn't cover the
 * screens under test. `NotificationPermissionFlowTest` denies it again.
 */
class TestApplication : BeforeYouBuyApplication() {
    override fun onAppStart() {
        shadowOf(this).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onAppForeground() = Unit
}
