package dev.placeholder.beforeyoubuy

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `launches to Home showing the app name`() {
        composeRule.onNodeWithText("Before You Buy").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "de")
    fun `launches to Home showing the app name in German`() {
        composeRule.onNodeWithText("Before You Buy").assertIsDisplayed()
    }
}
