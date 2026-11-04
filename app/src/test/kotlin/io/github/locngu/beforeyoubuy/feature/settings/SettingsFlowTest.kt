package io.github.locngu.beforeyoubuy.feature.settings

import android.Manifest
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.locngu.beforeyoubuy.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings in the real app (Hilt, Room, DataStore): opened from Home's top bar and back to Home, the fun money row,
 * the notification switches stored across visits, the line while the phone blocks notifications, and the system and
 * web pages it opens. Robolectric runs Android 13+, so the language row is the link; the line for older versions is
 * in `SettingsScreenshotTest` and `SettingsContentTest`.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w411dp-h891dp-xxhdpi")
class SettingsFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val application get() = ApplicationProvider.getApplicationContext<Application>()

    private fun tap(text: String) = composeRule.onNode(hasText(text) and hasClickAction()).performClick()

    private fun waitFor(text: String) = composeRule.waitUntilExactlyOneExists(hasText(text), timeoutMillis = 5_000)

    private fun openSettings() {
        waitFor("Considering")
        composeRule.onNode(hasContentDescription("Settings") and hasClickAction()).performClick()
        waitFor("Notifications")
    }

    private fun scrollTo(text: String) = composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(text))

    private fun waitingPeriodsSwitch() = composeRule.onNode(hasText("Waiting periods") and isToggleable())

    /** The next activity the app asked the system to start. */
    private fun startedIntent(): Intent = shadowOf(application).nextStartedActivity

    @Test
    fun `settings open from Home's top bar and system back returns to Home`() {
        openSettings()

        Espresso.pressBack()

        waitFor("Considering")
        composeRule.onNode(hasText("Notifications")).assertDoesNotExist()
    }

    @Test
    fun `the fun money row opens the fun money screen and back returns to settings`() {
        openSettings()

        tap("Monthly amount and adjustments")
        waitFor(
            "A monthly amount for the things you want, added by itself on the day you choose. It’s only a number in " +
                "this app; no real money moves."
        )
        Espresso.pressBack()

        waitFor("Notifications")
    }

    @Test
    fun `a notification switch is stored and still off on the next visit`() {
        openSettings()
        waitingPeriodsSwitch().assertIsOn()

        waitingPeriodsSwitch().performClick()
        waitingPeriodsSwitch().assertIsOff()
        Espresso.pressBack()
        openSettings()

        waitingPeriodsSwitch().assertIsOff()
        composeRule.onNode(hasText("Reflection reminders") and isToggleable()).assertIsOn()
    }

    @Test
    fun `without the permission a line says so and links to the app's notification settings, until it's back`() {
        shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        openSettings()
        val blocked = "Notifications are off for this app in the system settings, so none will show."
        waitFor(blocked)

        tap("Open system settings")
        val intent = startedIntent()
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(application.packageName, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))

        // Allowed in the system settings; the line goes when the app comes back to the front.
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitUntilDoesNotExist(hasText(blocked), timeoutMillis = 5_000)
    }

    @Test
    fun `the language row opens the system's per-app language page`() {
        openSettings()
        scrollTo("App language")

        tap("App language")

        val intent = startedIntent()
        assertEquals(Settings.ACTION_APP_LOCALE_SETTINGS, intent.action)
        assertEquals(Uri.fromParts("package", application.packageName, null), intent.data)
    }

    @Test
    fun `about links to the licence and the source repository in the browser`() {
        openSettings()

        scrollTo("Source available, free for personal and non-commercial use")
        tap("Source available, free for personal and non-commercial use")
        startedIntent().let {
            assertEquals(Intent.ACTION_VIEW, it.action)
            assertEquals(Uri.parse(LICENCE_URL), it.data)
        }

        scrollTo("Source code")
        tap("Source code")
        startedIntent().let {
            assertEquals(Intent.ACTION_VIEW, it.action)
            assertEquals(Uri.parse(SOURCE_URL), it.data)
        }
    }
}
