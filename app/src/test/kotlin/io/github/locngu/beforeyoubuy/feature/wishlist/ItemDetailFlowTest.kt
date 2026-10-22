package io.github.locngu.beforeyoubuy.feature.wishlist

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.locngu.beforeyoubuy.MainActivity
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Item detail and edit in the real app (Hilt, Room): opened from the wishlist, edited, marked as a need. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w411dp-h891dp-xxhdpi")
class ItemDetailFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun field(label: String): SemanticsNodeInteraction =
        composeRule.onNode(hasSetTextAction() and hasText(label))

    /** Adds a 50 € want (7 days) from the wishlist and opens it. */
    @Before
    fun openNewItem() {
        // Home loads its sections from the database off the main thread.
        composeRule.waitUntilExactlyOneExists(hasText("Open wishlist"), timeoutMillis = 5_000)
        composeRule.onNodeWithText("Open wishlist").performClick()
        composeRule.onNodeWithText("Add").performClick()
        field("Name").performTextInput("Headphones")
        field("Price").performTextInput("50")
        composeRule.onNodeWithText("Save").performClick()
        // The list loads from the database off the main thread, so wait for it.
        composeRule.waitUntilExactlyOneExists(hasText("Headphones"))
        composeRule.onNodeWithText("Headphones").performClick()
        composeRule.waitUntilExactlyOneExists(hasText("Why do you want this?"))
    }

    @Test
    fun `detail leads with the reasons and the countdown`() {
        composeRule.onNodeWithText("Headphones").assertIsDisplayed()
        composeRule.onNodeWithText("7 days left").assertIsDisplayed()
        composeRule.onNodeWithText("Added today").assertIsDisplayed()
        composeRule.onNodeWithText("Why do you want this?").assertIsDisplayed()
    }

    @Test
    fun `a higher price says it extends the wait, and the new price shows after saving`() {
        composeRule.onNodeWithText("Edit").performClick()
        composeRule.waitUntilExactlyOneExists(hasSetTextAction() and hasText("50"))

        field("Price").performTextReplacement("600")
        composeRule.onNode(hasText("Waiting period extended to", substring = true)).assertIsDisplayed()
        composeRule.onNodeWithText("Save").performClick()

        // Back on detail: 600 € is in the 60-day tier, counted from the day it was added.
        composeRule.waitUntilExactlyOneExists(hasText("60 days left"))
        composeRule.onNode(hasText("600", substring = true)).assertIsDisplayed()
    }

    @Test
    fun `mark as need asks first, then the wait is over`() {
        composeRule.onNodeWithText("Mark as need").performClick()
        composeRule.onNode(hasText("Mark as need") and hasAnyAncestor(isDialog())).performClick()

        composeRule.waitUntilExactlyOneExists(hasText("A need, so no waiting period"))
        composeRule.onNodeWithText("Ready to decide").assertIsDisplayed()
        composeRule.onNodeWithText("Why do you want this?").assertDoesNotExist()
    }

    @Test
    fun `a need's reasons are optional, behind a quiet link`() {
        composeRule.onNodeWithText("Mark as need").performClick()
        composeRule.onNode(hasText("Mark as need") and hasAnyAncestor(isDialog())).performClick()
        composeRule.waitUntilExactlyOneExists(hasText("A need, so no waiting period"))

        composeRule.onNodeWithText("Add your reasons").performClick()

        // The questions load from the database; generous, as `verify` runs lint alongside the tests.
        composeRule.waitUntilExactlyOneExists(hasText("Question 1 of 8"), timeoutMillis = 5_000)
        composeRule.onNodeWithText("Optional").assertIsDisplayed()
        composeRule.onNodeWithText("Skip").assertIsEnabled()
    }
}
