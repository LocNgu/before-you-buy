package io.github.locngu.beforeyoubuy.notifications

import android.Manifest
import android.app.Application
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.locngu.beforeyoubuy.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The one-time notification prompt in the real app (Hilt, Room, DataStore), without the permission granted. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en")
class NotificationPermissionFlowTest {
    // Denied before the activity starts (TestApplication grants it for every other test).
    private val denyPermission: TestRule = object : TestWatcher() {
        override fun starting(description: Description) {
            shadowOf(ApplicationProvider.getApplicationContext<Application>())
                .denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(denyPermission).around(composeRule)

    private val explanation = "Get one calm notification when a waiting period ends, at most once a day."

    private fun add(name: String) {
        composeRule.onNode(hasText("Add") and hasClickAction()).performClick()
        composeRule.onNode(hasSetTextAction() and hasText("Name")).performTextInput(name)
        composeRule.onNode(hasSetTextAction() and hasText("Price")).performTextInput("50")
        composeRule.onNode(hasText("Save") and hasClickAction()).performClick()
    }

    @Test
    fun `not asked before the first item, then once after it, and not again after "Not now"`() {
        composeRule.waitForIdle()
        composeRule.onNode(hasText(explanation)).assertDoesNotExist()

        add("Headphones")
        composeRule.waitUntilExactlyOneExists(hasText(explanation), timeoutMillis = 5_000)
        composeRule.onNode(hasText("Not now") and hasClickAction()).performClick()
        composeRule.waitUntilDoesNotExist(hasText(explanation), timeoutMillis = 5_000)

        add("Lamp")
        composeRule.waitForIdle()
        composeRule.onNode(hasText(explanation)).assertDoesNotExist()
    }

    @Test
    fun `"Allow notifications" asks the system and doesn't ask again`() {
        add("Headphones")
        composeRule.waitUntilExactlyOneExists(hasText(explanation), timeoutMillis = 5_000)

        composeRule.onNode(hasText("Allow notifications") and hasClickAction()).performClick()

        composeRule.waitUntilDoesNotExist(hasText(explanation), timeoutMillis = 5_000)
        val requested = shadowOf(composeRule.activity).lastRequestedPermission
        assertEquals(listOf(Manifest.permission.POST_NOTIFICATIONS), requested?.requestedPermissions?.toList())
    }
}
