package io.github.locngu.beforeyoubuy.feature.money

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.locngu.beforeyoubuy.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The fun money in the real app (Hilt, Room): set up from Home, back on Home with its amounts, then a take-out that
 * goes below zero asks first. Amounts depend on today's date (the 1st credits at once), so they're covered by
 * `FunMoneyViewModelTest` and the repository tests instead.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w411dp-h891dp-xxhdpi")
class FunMoneyFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun tap(text: String) = composeRule.onNode(hasText(text) and hasClickAction()).performClick()

    /** For buttons further down the scrolling form. */
    private fun scrollAndTap(text: String) =
        composeRule.onNode(hasText(text) and hasClickAction()).performScrollTo().performClick()

    private fun type(field: String, text: String) =
        composeRule.onNode(hasSetTextAction() and hasText(field)).performScrollTo().performTextInput(text)

    private fun waitFor(text: String) = composeRule.waitUntilExactlyOneExists(hasText(text), timeoutMillis = 5_000)

    @Test
    fun `fun money is set up from Home and a take-out below zero asks first`() {
        waitFor("Choose a monthly amount for the things you want. It’s added each month by itself.")
        tap("Set up")

        type("Monthly amount", "300")
        type("Starting amount (optional)", "120")
        tap("Save")

        waitFor("Fun money for wants")
        waitFor("Set aside for wants")
        waitFor("Not yet set aside")
        tap("Change or adjust")

        waitFor("Adjust the amount")
        type("Amount", "100000")
        scrollAndTap("Take out")
        waitFor("Go below zero?")
        tap("Cancel")

        Espresso.pressBack()
        waitFor("Fun money for wants")
    }
}
