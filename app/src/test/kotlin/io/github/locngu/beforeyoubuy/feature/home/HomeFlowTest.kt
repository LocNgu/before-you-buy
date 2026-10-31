package io.github.locngu.beforeyoubuy.feature.home

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
 * Home in the real app (Hilt, Room): the way to the wishlist, history and the decision screen, and system back from
 * each. Stages that need a moved clock are covered by `HomeViewModelTest`.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w411dp-h891dp-xxhdpi")
class HomeFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun tap(text: String) = composeRule.onNode(hasText(text) and hasClickAction()).performClick()

    private fun waitFor(text: String) = composeRule.waitUntilExactlyOneExists(hasText(text), timeoutMillis = 5_000)

    private fun add(name: String, price: String, need: Boolean = false) {
        tap("Add")
        composeRule.onNode(hasSetTextAction() and hasText("Name")).performTextInput(name)
        composeRule.onNode(hasSetTextAction() and hasText("Price")).performTextInput(price)
        if (need) composeRule.onNodeWithText("This is a need").performClick()
        tap("Save")
    }

    @Test
    fun `wishlist and history open from Home, and system back returns to Home`() {
        waitFor("Considering")
        waitFor("Nothing yet. Add something you’d like to buy. It waits on your wishlist while you think it over.")

        tap("Open wishlist")
        waitFor("Your wishlist is empty")
        Espresso.pressBack()
        waitFor("Considering")

        tap("History")
        waitFor("Nothing bought yet")
        Espresso.pressBack()
        waitFor("Considering")
    }

    @Test
    fun `a new want is considered, has no countdown until it's a priority, and isn't to decide`() {
        add("Headphones", "50")

        waitFor("1 item on your wishlist")
        // Parked: no waiting period runs for it (ADR-0049).
        composeRule.onNodeWithText("Next waiting period to end").assertDoesNotExist()
        composeRule.onNodeWithText("7 days left").assertDoesNotExist()
        composeRule.onNodeWithText("To decide").assertDoesNotExist()
    }

    @Test
    fun `a need is ready to decide, tapping it opens the decision screen and back returns to Home`() {
        add("Winter boots", "80", need = true)
        waitFor("To decide")

        tap("Winter boots")
        waitFor("Do you still want to spend this money on this?")
        Espresso.pressBack()

        waitFor("To decide")
        waitFor("1 item on your wishlist")
    }

    @Test
    fun `deciding against an item from Home takes it off Home and says where it went`() {
        add("Winter boots", "80", need = true)
        waitFor("To decide")
        tap("Winter boots")
        waitFor("Do you still want to spend this money on this?")

        composeRule.onNode(hasText("Don’t buy — keep", substring = true) and hasClickAction()).performClick()
        waitFor("Don’t buy it")
        tap("Don’t buy")

        waitFor("Got it. It’s in “Decided against”.")
        // Home may first show its last state; the reject arrives from the database after it.
        composeRule.waitUntilDoesNotExist(hasText("To decide"), timeoutMillis = 5_000)
        composeRule.waitUntilDoesNotExist(hasText("Winter boots"), timeoutMillis = 5_000)
        waitFor("Considering")
    }
}
