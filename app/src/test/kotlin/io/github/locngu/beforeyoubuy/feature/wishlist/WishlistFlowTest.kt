package io.github.locngu.beforeyoubuy.feature.wishlist

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.locngu.beforeyoubuy.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The wishlist in the real app (Hilt, Room): reached from Home, empty at first, quick add from its button. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w411dp-h891dp-xxhdpi")
class WishlistFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `an item added from the wishlist shows up under cooling off, with its snackbar`() {
        composeRule.onNodeWithText("Wishlist").performClick()
        // The list loads from the database off the main thread, so wait for it.
        composeRule.waitUntilExactlyOneExists(hasText("Your wishlist is empty"))

        composeRule.onNodeWithText("Add").performClick()
        composeRule.onNode(hasSetTextAction() and hasText("Name")).performTextInput("Headphones")
        composeRule.onNode(hasSetTextAction() and hasText("Price")).performTextInput("1,234.56")
        composeRule.onNodeWithText("Save").performClick()

        composeRule.onNodeWithText("Added. You can decide in 90 days.").assertIsDisplayed()
        composeRule.waitUntilExactlyOneExists(hasText("Headphones"))
        composeRule.onNodeWithText("Headphones").assertIsDisplayed()
        composeRule.onNodeWithText("90 days left").assertIsDisplayed()
        composeRule.onNodeWithText("Your wishlist is empty").assertDoesNotExist()
    }
}
