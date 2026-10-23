package io.github.locngu.beforeyoubuy.feature.wishlist

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.core.domain.pool.Savings
import io.github.locngu.beforeyoubuy.core.domain.wishlist.itemDetailOf
import io.github.locngu.beforeyoubuy.ui.theme.BeforeYouBuyTheme
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * "Mark as need" on item detail asks first and, for a want with money set aside, says that it goes back to the fun
 * money (ADR-0044, ADR-0046); nothing happens until confirmed. The dialog is its own window, so screenshots miss it.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-w411dp-h891dp-xxhdpi")
class MarkAsNeedDialogTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val now = Instant.parse("2026-10-16T10:00:00Z")
    private val camera = Item(
        id = ItemId(1),
        name = "Camera",
        price = Money(12_000, "EUR"),
        addedAt = now.minusSeconds(10L * 24 * 3600),
        coolingEndsAt = now.plusSeconds(10L * 24 * 3600)
    )

    private fun show(savings: Savings, onMarkAsNeed: () -> Unit) = composeRule.setContent {
        BeforeYouBuyTheme {
            ItemDetailContent(
                state = ItemDetailUiState(itemDetailOf(camera, null, savings, now, ZoneOffset.UTC)),
                onEdit = {},
                onReflect = {},
                onDecide = {},
                onMarkAsNeed = onMarkAsNeed,
                onOpenFunMoney = {},
                savingsActions = NoSavingsActions,
                onCopyLink = {},
                onOpenLink = {},
                snackbarHostState = SnackbarHostState()
            )
        }
    }

    private fun inDialog(text: String) = hasText(text, substring = true) and hasAnyAncestor(isDialog())

    @Test
    fun `with money set aside it says the money goes back, and marks only once confirmed`() {
        var marked = 0
        show(Savings(setUp = true, required = true, byItem = mapOf(camera.id to Money(6_000, "EUR")))) { marked++ }

        composeRule.onNodeWithText("Mark as need").performScrollTo().performClick()

        composeRule.onNode(inDialog("The €60 set aside for it goes back to your fun money.")).assertExists()
        assertEquals(0, marked)
        composeRule.onNode(inDialog("Cancel")).performClick()
        assertEquals(0, marked)

        composeRule.onNodeWithText("Mark as need").performScrollTo().performClick()
        composeRule.onNode(inDialog("Mark as need")).performClick()
        assertEquals(1, marked)
    }

    @Test
    fun `without money set aside it doesn't mention any`() {
        show(Savings(setUp = true, required = true, byItem = emptyMap())) {}

        composeRule.onNodeWithText("Mark as need").performScrollTo().performClick()

        composeRule.onNode(inDialog("Needs skip the waiting period")).assertExists()
        composeRule.onNode(inDialog("goes back to your fun money")).assertDoesNotExist()
    }
}
