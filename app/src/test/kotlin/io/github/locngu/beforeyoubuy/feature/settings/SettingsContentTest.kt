package io.github.locngu.beforeyoubuy.feature.settings

import android.os.Build
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.locngu.beforeyoubuy.core.domain.settings.NotificationNotice
import io.github.locngu.beforeyoubuy.core.domain.settings.NotificationPrefs
import io.github.locngu.beforeyoubuy.notifications.Notifier
import io.github.locngu.beforeyoubuy.ui.theme.BeforeYouBuyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The stateless screen's behaviour: the language section per Android version, and which page each notice opens.
 * Robolectric only runs Android 13+, so the version check itself is [hasPerAppLanguageSettings].
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-w411dp-h891dp-xxhdpi")
class SettingsContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val loaded = SettingsUiState(
        notifications = NotificationPrefs(),
        notice = NotificationNotice.None,
        prioritySlots = 3,
        active = emptyList()
    )

    private fun show(
        state: SettingsUiState = loaded,
        perAppLanguage: Boolean = true,
        actions: SettingsActions = SettingsActions()
    ) = composeRule.setContent {
        BeforeYouBuyTheme { SettingsContent(state, version = "0.6.0", perAppLanguage, actions) }
    }

    private fun scrollTo(text: String) = composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(text))

    @Test
    fun `the per-app language page exists from Android 13`() {
        assertTrue(hasPerAppLanguageSettings(Build.VERSION_CODES.TIRAMISU))
        assertTrue(hasPerAppLanguageSettings(Build.VERSION_CODES.TIRAMISU + 1))
        assertFalse(hasPerAppLanguageSettings(Build.VERSION_CODES.S_V2))
        assertFalse(hasPerAppLanguageSettings(Build.VERSION_CODES.O))
    }

    @Test
    fun `from Android 13 the language row opens the system page`() {
        var opened = 0
        show(actions = SettingsActions(onOpenLanguage = { opened++ }))
        scrollTo("App language")

        composeRule.onNode(hasText("App language") and hasClickAction()).performClick()

        assertEquals(1, opened)
        composeRule.onNodeWithText("Follows the system language").assertDoesNotExist()
    }

    @Test
    fun `before Android 13 one line says the language follows the system, with nothing to tap`() {
        show(perAppLanguage = false)
        scrollTo("Follows the system language")

        composeRule.onNode(hasText("Follows the system language") and hasClickAction()).assertDoesNotExist()
        composeRule.onNodeWithText("App language").assertDoesNotExist()
    }

    @Test
    fun `a channel switched off in the system links to that channel's page`() {
        val opened = mutableListOf<String>()
        show(
            state = loaded.copy(
                notice = NotificationNotice.ChannelsOff(waitingPeriods = false, reflectionReminders = true)
            ),
            actions = SettingsActions(onOpenChannel = { opened += it })
        )

        composeRule.onNode(hasText("Open system settings") and hasClickAction()).performClick()

        assertEquals(listOf(Notifier.CHANNEL_REFLECTION), opened)
        composeRule.onNodeWithText("Switched off in the system settings").assertExists()
    }

    @Test
    fun `nothing but the title is shown before the state is loaded`() {
        composeRule.setContent {
            BeforeYouBuyTheme { SettingsContent(null, version = "0.6.0", perAppLanguage = true, SettingsActions()) }
        }

        composeRule.onNodeWithText("Settings").assertExists()
        composeRule.onNodeWithText("Waiting periods").assertDoesNotExist()
    }
}
