package io.github.locngu.beforeyoubuy.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RoborazziRule
import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.core.domain.settings.NotificationNotice
import io.github.locngu.beforeyoubuy.core.domain.settings.NotificationPrefs
import io.github.locngu.beforeyoubuy.testing.asPriority
import io.github.locngu.beforeyoubuy.testing.captureContent
import io.github.locngu.beforeyoubuy.ui.theme.BeforeYouBuyTheme
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings: typical (Android 13+, two priorities, everything the phone shows), notifications blocked for the app, a
 * channel switched off with the line for Android 8–12, and loading; en/de × light/dark. A taller screen than other
 * screenshots, so the whole page is in one image. Record: `./gradlew :app:recordRoborazziDebug`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class SettingsScreenshotTest {
    @get:Rule(order = 0)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // Names images after the concrete subclass, not this base class.
    @get:Rule(order = 1)
    val roborazziRule = RoborazziRule()

    private val now = Instant.parse("2026-11-03T10:00:00Z")

    private fun priority(id: Long, slot: Int) = Item(
        id = ItemId(id),
        name = "Item $id",
        price = Money(10_000, "EUR"),
        addedAt = now,
        coolingOffDays = 30
    ).asPriority(now.plusSeconds(10L * 24 * 3600), slot)

    private val typical = SettingsUiState(
        notifications = NotificationPrefs(waitingPeriods = true, reflectionReminders = false),
        notice = NotificationNotice.None,
        prioritySlots = 3,
        active = listOf(priority(1, 1), priority(2, 2))
    )

    @Test
    fun typical() = capture(typical)

    @Test
    fun notificationsBlocked() = capture(typical.copy(notice = NotificationNotice.AppBlocked))

    @Test
    fun channelOffOlderAndroid() = capture(
        typical.copy(
            notifications = NotificationPrefs(),
            notice = NotificationNotice.ChannelsOff(waitingPeriods = true, reflectionReminders = false)
        ),
        perAppLanguage = false
    )

    @Test
    fun loading() = capture(null)

    private fun capture(state: SettingsUiState?, perAppLanguage: Boolean = true) {
        composeRule.setContent {
            BeforeYouBuyTheme {
                SettingsContent(state, version = "0.6.0", perAppLanguage = perAppLanguage, actions = SettingsActions())
            }
        }
        composeRule.captureContent()
    }
}

@Config(qualifiers = "en-w411dp-h1300dp-notnight-xxhdpi")
class SettingsScreenshotEnLightTest : SettingsScreenshotTest()

@Config(qualifiers = "en-w411dp-h1300dp-night-xxhdpi")
class SettingsScreenshotEnDarkTest : SettingsScreenshotTest()

@Config(qualifiers = "de-w411dp-h1300dp-notnight-xxhdpi")
class SettingsScreenshotDeLightTest : SettingsScreenshotTest()

@Config(qualifiers = "de-w411dp-h1300dp-night-xxhdpi")
class SettingsScreenshotDeDarkTest : SettingsScreenshotTest()
