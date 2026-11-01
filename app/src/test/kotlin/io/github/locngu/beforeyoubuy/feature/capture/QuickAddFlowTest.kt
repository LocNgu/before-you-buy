package io.github.locngu.beforeyoubuy.feature.capture

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.locngu.beforeyoubuy.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Quick add in the real app (Hilt, Room), the way a user does it: Add → type the name → Next → type the price →
 * Save. That's three taps plus typing (acceptance criterion of #14); "Done" on the keyboard can replace Save.
 *
 * Native graphics and a phone-sized screen: in legacy mode text gets placeholder widths, and the snackbar's text
 * didn't count as displayed.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QuickAddFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun field(label: String): SemanticsNodeInteraction =
        composeRule.onNode(hasSetTextAction() and hasText(label))

    @Test
    @Config(qualifiers = "en-w411dp-h891dp-xxhdpi")
    fun `a want in three taps, in English`() {
        composeRule.onNodeWithText("Add").performClick() // 1

        field("Name").assertIsFocused()
        field("Name").performTextInput("Headphones")
        field("Name").performImeAction() // 2: Next
        field("Price").assertIsFocused()
        field("Price").performTextInput("1,234.56")
        composeRule.onNodeWithText("Save").performClick() // 3

        composeRule.onNodeWithText("Added to your wishlist.").assertIsDisplayed()
        composeRule.onNode(hasText("Make it a priority") and hasClickAction()).assertIsDisplayed()
        composeRule.onNodeWithText("Quick add").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "de-w411dp-h891dp-xxhdpi")
    fun `a want in German, saved with Done on the keyboard`() {
        composeRule.onNodeWithText("Hinzufügen").performClick()

        field("Name").performTextInput("Kopfhörer")
        field("Preis").performTextInput("1.234,56")
        field("Preis").performImeAction()

        composeRule.onNodeWithText("Zu deiner Wunschliste hinzugefügt.").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-w411dp-h891dp-xxhdpi")
    fun `the price says the tier's waiting period as it's typed, and the snackbar makes it a priority`() {
        composeRule.onNodeWithText("Add").performClick()
        field("Name").performTextInput("Book")
        field("Price").performTextInput("12.50")
        composeRule.onNodeWithText("Waiting period: 7 days, once it’s a priority").assertIsDisplayed()
        field("Price").performTextReplacement("1500")
        composeRule.onNodeWithText("Waiting period: 90 days, once it’s a priority").assertIsDisplayed()
        composeRule.onNodeWithText("Save").performClick()

        composeRule.onNode(hasText("Make it a priority") and hasClickAction()).performClick()

        composeRule.waitUntilExactlyOneExists(hasText("Book is now priority #1."), timeoutMillis = 5_000)
    }

    @Test
    @Config(qualifiers = "en-w411dp-h891dp-xxhdpi")
    fun `a need says it was added as a need`() {
        composeRule.onNodeWithText("Add").performClick()
        field("Name").performTextInput("Fridge")
        field("Price").performTextInput("600")
        composeRule.onNodeWithText("This is a need").performClick()
        composeRule.onNodeWithText("Save").performClick()

        composeRule.onNodeWithText("Added as a need.").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-w411dp-h891dp-xxhdpi")
    fun `Save stays disabled and the price field says why`() {
        composeRule.onNodeWithText("Add").performClick()
        composeRule.onNodeWithText("Save").assertIsNotEnabled()

        field("Name").performTextInput("Headphones")
        field("Price").performTextInput("about 100")

        composeRule.onNodeWithText("Enter an amount like 12.50").assertIsDisplayed()
        composeRule.onNodeWithText("Save").assertIsNotEnabled()
        field("Price").performImeAction() // Done doesn't save an invalid form
        composeRule.onNodeWithText("Quick add").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-w411dp-h891dp-xxhdpi")
    fun `no reflection questions while adding`() {
        composeRule.onNodeWithText("Add").performClick()
        composeRule.onNodeWithText("More details").performClick()

        for (question in listOf("Why", "How often", "against", "replace", "problem")) {
            composeRule.onNode(hasText(question, substring = true, ignoreCase = true)).assertDoesNotExist()
        }
        composeRule.onNodeWithText("Save").assertIsNotEnabled()
        field("Name").performTextInput("Lamp")
        field("Price").performTextInput("30")
        composeRule.onNodeWithText("Save").assertIsEnabled()
    }
}
