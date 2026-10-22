package io.github.locngu.beforeyoubuy.feature.reflection

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.locngu.beforeyoubuy.MainActivity
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The reflection flow in the real app (Hilt, Room): opened from item detail, answered, left halfway. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w411dp-h891dp-xxhdpi")
class ReflectionFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun field(label: String): SemanticsNodeInteraction =
        composeRule.onNode(hasSetTextAction() and hasText(label))

    private fun tap(text: String) = composeRule.onNodeWithText(text).performClick()

    /** Waits for a screen that loads from the database; generous, as `verify` runs lint alongside the tests. */
    private fun waitFor(text: String) = composeRule.waitUntilExactlyOneExists(hasText(text), timeoutMillis = 5_000)

    /** Adds a 50 € want from the wishlist, opens it and taps the card asking for the reasons. */
    @Before
    fun openQuestions() {
        // Home loads its sections from the database off the main thread.
        composeRule.waitUntilExactlyOneExists(hasText("Open wishlist"), timeoutMillis = 5_000)
        tap("Open wishlist")
        tap("Add")
        field("Name").performTextInput("Headphones")
        field("Price").performTextInput("50")
        tap("Save")
        // The list and detail load from the database off the main thread, so wait for them.
        waitFor("Headphones")
        tap("Headphones")
        waitFor("Start")
        tap("Start")
        waitFor("Question 1 of 8")
    }

    @Test
    fun `answering every question shows the answers on detail`() {
        composeRule.onNodeWithText("Next").assertIsNotEnabled()
        field("Your answer").performTextInput("For the commute")
        tap("Next")
        tap("Weekly")
        tap("Next")
        field("Reason 1").performTextInput("I have earbuds")
        tap("Next")
        composeRule.onNodeWithText("Question 4 of 8").assertIsDisplayed()
        repeat(4) { tap("Skip") }
        composeRule.onNodeWithText("Question 8 of 8").assertIsDisplayed()
        tap("Done")

        waitFor("Edit answers")
        composeRule.onNodeWithText("For the commute").assertIsDisplayed()
        composeRule.onNodeWithText("Weekly").assertIsDisplayed()
        composeRule.onNodeWithText("• I have earbuds").assertIsDisplayed()
        composeRule.onNodeWithText("Start").assertDoesNotExist()
        // Answers never change the wait.
        composeRule.onNodeWithText("7 days left").assertIsDisplayed()
    }

    @Test
    fun `leaving halfway keeps the answers, and coming back continues there`() {
        field("Your answer").performTextInput("For the commute")
        tap("Next")

        Espresso.pressBack()

        waitFor("A few answers are still missing")
        composeRule.onNodeWithText("For the commute").assertIsDisplayed()
        tap("Continue")
        waitFor("Question 2 of 8")
    }
}
