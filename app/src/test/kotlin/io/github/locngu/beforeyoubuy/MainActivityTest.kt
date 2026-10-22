package io.github.locngu.beforeyoubuy

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `launches to Home showing the app name`() {
        val appName = composeRule.activity.getString(R.string.app_name)
        composeRule.onNodeWithText(appName).assertIsDisplayed()
    }

    // The per-app language setting changes the app's configuration locale, like these qualifiers do.
    @Test
    @Config(qualifiers = "en")
    fun `Home is in English`() {
        // The sections load from the database off the main thread.
        composeRule.waitUntilExactlyOneExists(hasText("Considering"), timeoutMillis = 5_000)
        composeRule.onNodeWithText("Considering").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "de")
    fun `Home is in German`() {
        // The sections load from the database off the main thread.
        composeRule.waitUntilExactlyOneExists(hasText("In Überlegung"), timeoutMillis = 5_000)
        composeRule.onNodeWithText("In Überlegung").assertIsDisplayed()
    }
}
