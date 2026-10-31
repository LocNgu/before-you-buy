package io.github.locngu.beforeyoubuy.feature.history

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * History in the real app (Hilt, Room): an item decided against shows up under "Decided against" with the total,
 * "Want it again?" puts a new item on the wishlist while the entry stays, and deleting asks first.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w411dp-h891dp-xxhdpi")
class HistoryFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun button(text: String, substring: Boolean = false): SemanticsNodeInteraction =
        composeRule.onNode(hasText(text, substring = substring) and hasClickAction())

    /** Taps a button, scrolling to it first if it's in a scrolling screen. */
    private fun tap(text: String, substring: Boolean = false) {
        val node = button(text, substring)
        if (runCatching { node.assert(hasAnyAncestor(hasScrollAction())) }.isSuccess) node.performScrollTo()
        node.performClick()
    }

    private fun tapInDialog(text: String) =
        composeRule.onNode(hasText(text) and hasClickAction() and hasAnyAncestor(isDialog())).performClick()

    /**
     * The ⋮ button, then its menu. It appears once the entry has loaded, so wait for it first, and for a dialog that
     * is still closing to be gone: it would take the tap, and the menu would never open.
     */
    private fun openMenu() {
        composeRule.waitUntilDoesNotExist(isDialog(), timeoutMillis = 5_000)
        composeRule.waitUntilExactlyOneExists(hasContentDescription("More options"), timeoutMillis = 5_000)
        composeRule.onNode(hasContentDescription("More options")).performClick()
        composeRule.waitUntilExactlyOneExists(
            hasText("Delete from history") and hasClickAction(),
            timeoutMillis = 5_000
        )
    }

    private fun waitFor(text: String, substring: Boolean = false) =
        composeRule.waitUntilExactlyOneExists(hasText(text, substring = substring), timeoutMillis = 5_000)

    /** Adds a 50 € want, decides against it with a note, and opens "Decided against" from Home. */
    @Before
    fun decideAgainstAnItem() {
        // Home loads its sections from the database off the main thread.
        composeRule.waitUntilExactlyOneExists(hasText("Open wishlist"), timeoutMillis = 5_000)
        tap("Open wishlist")
        tap("Add")
        composeRule.onNode(hasSetTextAction() and hasText("Name")).performTextInput("Headphones")
        composeRule.onNode(hasSetTextAction() and hasText("Price")).performTextInput("50")
        tap("Save")
        waitFor("Headphones")
        tap("Headphones")
        tap("Decide now")
        tap("Don’t buy — keep", substring = true)
        composeRule.onNode(hasSetTextAction() and hasText("Why not? (optional)")).performTextInput("Mine still work")
        tap("Don’t buy")
        waitFor("Got it. It’s in “Decided against”.")
        // Back from the wishlist to Home.
        Espresso.pressBack()
        tap("History")
        tap("Decided against")
        waitFor("You decided against", substring = true)
    }

    @Test
    fun `decided against lists the item, its note and a plain total`() {
        waitFor("You decided against €50 of purchases")
        waitFor("Mine still work")
        button("Headphones").assertExists()
    }

    @Test
    fun `want it again adds a new item to the wishlist, and the entry stays in history`() {
        tap("Headphones")
        tap("Want it again?")
        waitFor("Put it back on your wishlist?")
        tapInDialog("Add to wishlist")

        // The new item's detail: parked like a new capture (ADR-0049), no answers yet.
        waitFor("Why do you want this?")
        waitFor("Not one of your priorities")
        Espresso.pressBack()

        waitFor("You decided against €50 of purchases")
        button("Headphones").assertExists()
    }

    @Test
    fun `deleting asks first, then removes the entry and its total`() {
        tap("Headphones")
        openMenu()
        tap("Delete from history")
        waitFor("Delete from history?")
        // Cancelling keeps it.
        tapInDialog("Cancel")
        waitFor("Mine still work")

        openMenu()
        tap("Delete from history")
        tapInDialog("Delete")

        waitFor("Nothing decided against yet")
        composeRule.onNode(hasText("Headphones")).assertDoesNotExist()
    }
}
