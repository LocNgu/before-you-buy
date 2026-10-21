package io.github.locngu.beforeyoubuy.notifications

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.locngu.beforeyoubuy.MainActivity
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.navigation.Home
import io.github.locngu.beforeyoubuy.navigation.ItemDecision
import io.github.locngu.beforeyoubuy.navigation.ItemDetail
import io.github.locngu.beforeyoubuy.navigation.ItemReflection
import io.github.locngu.beforeyoubuy.navigation.Wishlist
import io.github.locngu.beforeyoubuy.navigation.backStackFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en")
class NotificationLinksTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun `every target opens on top of the screens that lead there`() {
        fun stack(target: NotificationTarget, id: Long? = 3) =
            backStackFor(NotificationTap(target, id?.let(::ItemId), 1))

        assertEquals(listOf(Home, Wishlist), stack(NotificationTarget.WISHLIST, id = null))
        assertEquals(listOf(Home, Wishlist, ItemDetail(3)), stack(NotificationTarget.ITEM))
        assertEquals(listOf(Home, Wishlist, ItemDetail(3), ItemDecision(3)), stack(NotificationTarget.DECISION))
        assertEquals(listOf(Home, Wishlist, ItemDetail(3), ItemReflection(3)), stack(NotificationTarget.REFLECTION))
    }

    @Test
    fun `taps are read back from their intents, anything else isn't one`() {
        val tap = NotificationTap(NotificationTarget.DECISION, ItemId(3), 42)

        assertEquals(tap, NotificationIntents.tapOf(NotificationIntents.open(context, tap.target, tap.itemId, 42)))
        assertEquals(
            LetItGoRequest(ItemId(3), 42),
            NotificationIntents.letItGoOf(NotificationIntents.letItGo(context, ItemId(3), 42))
        )
        assertNull(NotificationIntents.tapOf(Intent(Intent.ACTION_MAIN)))
        assertNull(NotificationIntents.tapOf(null))
        // An item target needs its item.
        assertNull(NotificationIntents.tapOf(NotificationIntents.open(context, NotificationTarget.ITEM, null, 42)))
        assertNull(
            NotificationIntents.letItGoOf(NotificationIntents.open(context, NotificationTarget.ITEM, ItemId(3), 42))
        )
    }

    @Test
    fun `"Still want it" opens the app on the decision screen`() {
        val intent = NotificationIntents.open(context, NotificationTarget.DECISION, ItemId(1), 42)

        ActivityScenario.launch<MainActivity>(intent).use {
            // A fresh install has no item 1, so the decision screen says it's gone; what matters is where it opened.
            composeRule.waitUntilExactlyOneExists(
                hasText("This item is no longer on your wishlist"),
                timeoutMillis = 5_000
            )
        }
    }

    @Test
    fun `a digest opens the wishlist`() {
        val intent = NotificationIntents.open(context, NotificationTarget.WISHLIST, null, 42)

        ActivityScenario.launch<MainActivity>(intent).use {
            composeRule.waitUntilExactlyOneExists(hasText("Your wishlist is empty"), timeoutMillis = 5_000)
        }
    }
}
