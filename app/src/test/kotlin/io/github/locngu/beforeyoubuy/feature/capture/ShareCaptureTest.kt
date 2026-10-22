package io.github.locngu.beforeyoubuy.feature.capture

import android.content.Context
import android.content.Intent
import android.os.BadParcelableException
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.locngu.beforeyoubuy.MainActivity
import io.github.locngu.beforeyoubuy.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast

/**
 * Share capture (#15) in the real app (Hilt, Room). Robolectric starts each test with a fresh application and only
 * the share activity is launched, as on a share while the app's process is dead (cold start). Returning to the app
 * the share came from is the task's doing and can only be seen on a device; here the activity finishes.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w411dp-h891dp-xxhdpi")
class ShareCaptureTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun share(text: String? = null, subject: String? = null, title: String? = null): Intent =
        Intent(Intent.ACTION_SEND).apply {
            setClass(context, ShareReceiverActivity::class.java)
            type = "text/plain"
            text?.let { putExtra(Intent.EXTRA_TEXT, it) }
            subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
            title?.let { putExtra(Intent.EXTRA_TITLE, it) }
        }

    private fun field(label: String): SemanticsNodeInteraction =
        composeRule.onNode(hasSetTextAction() and hasText(label))

    // Robolectric leaves a finished activity resumed; finishing is what the user sees as "back where I was".
    private fun ActivityScenario<*>.awaitFinishing() = composeRule.waitUntil(timeoutMillis = 5_000) {
        var finishing = false
        onActivity { finishing = it.isFinishing }
        finishing
    }

    @Test
    fun `a link shared from Chrome opens quick add filled in, with the cursor in the price, and Done saves it`() {
        val chrome = share(
            text = "https://www.otto.de/p/philips-airfryer-xxl-S0A1B2C3/?utm_source=chrome",
            subject = "Philips Airfryer XXL HD9650/90 | OTTO"
        )
        ActivityScenario.launch<ShareReceiverActivity>(chrome).use { scenario ->
            field("Name").assertTextContains("Philips Airfryer XXL HD9650/90")
            field("Link (optional)").assertTextContains("https://www.otto.de/p/philips-airfryer-xxl-S0A1B2C3/")
            field("Price").assertIsFocused()

            field("Price").performTextInput("199.99")
            field("Price").performImeAction()

            scenario.awaitFinishing()
            composeRule.waitUntil { ShadowToast.getTextOfLatestToast() != null }
            assertEquals("Added. You can decide in 30 days.", ShadowToast.getTextOfLatestToast())
        }

        // Stored like any other item: it's on the wishlist.
        ActivityScenario.launch(MainActivity::class.java).use {
            // Home loads its sections from the database off the main thread.
            composeRule.waitUntilExactlyOneExists(hasText("Open wishlist"), timeoutMillis = 5_000)
            composeRule.onNodeWithText("Open wishlist").performClick()
            composeRule.waitUntilExactlyOneExists(hasText("Philips Airfryer XXL HD9650/90"))
        }
    }

    @Test
    fun `closing the sheet finishes without storing anything`() {
        ActivityScenario.launch<ShareReceiverActivity>(share(text = "Lampe https://shop.example/p/1")).use { scenario ->
            field("Name").assertTextContains("Lampe")
            Espresso.pressBackUnconditionally()

            scenario.awaitFinishing()
            assertNull(ShadowToast.getTextOfLatestToast())
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            // Home loads its sections from the database off the main thread.
            composeRule.waitUntilExactlyOneExists(hasText("Open wishlist"), timeoutMillis = 5_000)
            composeRule.onNodeWithText("Open wishlist").performClick()
            composeRule.waitUntilExactlyOneExists(hasText("Your wishlist is empty"))
        }
    }

    @Test
    fun `turning the phone keeps what was typed`() {
        ActivityScenario.launch<ShareReceiverActivity>(share(text = "Lampe https://shop.example/p/1")).use { scenario ->
            field("Name").performTextReplacement("Lampe blau")
            field("Price").performTextInput("30")

            scenario.recreate()

            field("Name").assertTextContains("Lampe blau")
            field("Price").assertTextContains("30")
        }
    }

    @Test
    fun `a share without a link opens with the text as the name`() {
        ActivityScenario.launch<ShareReceiverActivity>(share(text = "Fahrradhelm, blau")).use {
            field("Name").assertTextContains("Fahrradhelm, blau")
            composeRule.onNodeWithText("Quick add").assertIsDisplayed()
        }
    }

    @Test
    fun `an empty share opens an empty sheet with the cursor in the name`() {
        ActivityScenario.launch<ShareReceiverActivity>(share()).use {
            field("Name").assertIsFocused()
            composeRule.onNodeWithText("Save").assertIsDisplayed()
        }
    }

    @Test
    fun `the app is offered for shared text, not for images`() {
        val text = context.packageManager.queryIntentActivities(Intent(Intent.ACTION_SEND).setType("text/plain"), 0)
        val ours = text.single { it.activityInfo.name == ShareReceiverActivity::class.java.name }
        assertEquals("Before You Buy", ours.loadLabel(context.packageManager).toString())

        val images = context.packageManager.queryIntentActivities(Intent(Intent.ACTION_SEND).setType("image/png"), 0)
        assertTrue(images.none { it.activityInfo.packageName == context.packageName })
    }

    // --- What the share fills in ---

    @Test
    fun `a link without a name is named after the shop`() {
        val prefill = sharedPrefill(share(text = "https://www.amazon.de/dp/B09Y2MYL5C"), context.resources)
        assertEquals(QuickAddPrefill(name = "amazon.de item", link = "https://www.amazon.de/dp/B09Y2MYL5C"), prefill)
    }

    @Test
    @Config(qualifiers = "de")
    fun `the shop's name is in German too`() {
        val prefill = sharedPrefill(share(text = "https://www.amazon.de/dp/B09Y2MYL5C"), context.resources)
        assertEquals("Artikel von amazon.de", prefill.name)
        assertEquals(context.getString(R.string.share_fallback_name, "amazon.de"), prefill.name)
    }

    @Test
    fun `the title is the fallback when there's no subject`() {
        val prefill =
            sharedPrefill(share(text = "https://shop.example/p/1", subject = " ", title = "Lampe"), context.resources)
        assertEquals("Lampe", prefill.name)
    }

    @Test
    fun `the price is never filled in`() {
        val prefill = sharedPrefill(share(text = "Lampe 29,99 € https://shop.example/p/1"), context.resources)
        assertEquals("", prefill.price)
    }

    @Test
    fun `anything but a share opens empty`() {
        val view = Intent(Intent.ACTION_VIEW).putExtra(Intent.EXTRA_TEXT, "Lampe https://shop.example/p/1")
        assertEquals(QuickAddPrefill(), sharedPrefill(view, context.resources))
    }

    @Test
    fun `extras that can't be read open it empty`() {
        // As when another app puts a Parcelable of its own into the extras: reading them throws.
        val unreadable = object : Intent(share(text = "Lampe https://shop.example/p/1")) {
            override fun getCharSequenceExtra(name: String?): CharSequence? =
                throw BadParcelableException("unknown class")
        }
        assertEquals(QuickAddPrefill(), sharedPrefill(unreadable, context.resources))
    }
}
