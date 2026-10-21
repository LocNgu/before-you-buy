package io.github.locngu.beforeyoubuy.feature.decision

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.locngu.beforeyoubuy.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The decision screen in the real app (Hilt, Room), opened from item detail. A new want is cooling off (the real
 * clock can't be moved here), so it shows the early path; marked as a need it shows Buy. Ready wants and Wait are
 * covered by `DecisionViewModelTest` with a movable clock.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w411dp-h891dp-xxhdpi")
class DecisionFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private var taps = 0

    private fun button(text: String, substring: Boolean = false): SemanticsNodeInteraction =
        composeRule.onNode(hasText(text, substring = substring) and hasClickAction())

    /** Taps a button, scrolling to it first if it's in a scrolling screen, and counts the tap. */
    private fun tap(text: String, substring: Boolean = false) {
        val node = button(text, substring)
        if (runCatching { node.assert(hasAnyAncestor(hasScrollAction())) }.isSuccess) node.performScrollTo()
        node.performClick()
        taps++
    }

    private fun waitFor(text: String, substring: Boolean = false) =
        composeRule.waitUntilExactlyOneExists(hasText(text, substring = substring), timeoutMillis = 5_000)

    /** Adds a 50 € want from the wishlist and opens its detail. */
    @Before
    fun openNewItem() {
        tap("Wishlist")
        tap("Add")
        composeRule.onNode(hasSetTextAction() and hasText("Name")).performTextInput("Headphones")
        composeRule.onNode(hasSetTextAction() and hasText("Price")).performTextInput("50")
        tap("Save")
        waitFor("Headphones")
        tap("Headphones")
        waitFor("Why do you want this?")
    }

    private fun openDecision(entry: String) {
        tap(entry)
        waitFor("Do you still want to spend this money on this?")
        taps = 0
    }

    @Test
    fun `a want that isn't ready offers no buy and no wait, only not buying and deciding early`() {
        openDecision("Decide now")

        waitFor("7 days left")
        button("Don’t buy — keep", substring = true).assertIsEnabled()
        button("Decide early…").assertIsEnabled()
        composeRule.onNode(hasText("Buy") and hasClickAction()).assertDoesNotExist()
        composeRule.onNode(hasText("Wait") and hasClickAction()).assertDoesNotExist()
    }

    @Test
    fun `buying early needs a reason, then the item leaves the wishlist`() {
        openDecision("Decide now")
        tap("Decide early…")
        waitFor("Buy before it’s ready?")
        waitFor("7 days of the waiting period are left.")
        button("Buy anyway").assertIsNotEnabled()

        composeRule.onNode(hasSetTextAction() and hasText("Why buy it now?")).performTextInput("Old ones broke")
        button("Buy anyway").assertIsEnabled()
        tap("Buy anyway")

        waitFor("It’s in “Bought”", substring = true)
        composeRule.onNode(hasText("Headphones")).assertDoesNotExist()
    }

    @Test
    fun `not buying takes no more taps than buying, and says where the item went without celebrating`() {
        // A need offers Buy, so both paths can be counted on the same screen.
        tap("Mark as need")
        composeRule.onNode(hasText("Mark as need") and hasAnyAncestor(isDialog())).performClick()
        waitFor("A need, so no waiting period")
        openDecision("Decide")

        tap("Buy")
        waitFor("Record the purchase")
        button("Buy").assertIsEnabled()
        val buyTaps = taps + 1
        // Back to the overview without deciding, then not buying.
        Espresso.pressBack()
        waitFor("Do you still want to spend this money on this?")
        taps = 0

        tap("Don’t buy — keep", substring = true)
        waitFor("Don’t buy it")
        tap("Don’t buy")
        waitFor("Got it. It’s in “Decided against”.")

        assertEquals(buyTaps, taps)
        composeRule.onNode(hasText("Headphones")).assertDoesNotExist()
    }
}
