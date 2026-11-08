package io.github.locngu.beforeyoubuy.feature.wishlist

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.core.domain.pool.Savings
import io.github.locngu.beforeyoubuy.core.domain.wishlist.SortField
import io.github.locngu.beforeyoubuy.core.domain.wishlist.WishlistSort
import io.github.locngu.beforeyoubuy.core.domain.wishlist.wishlistOf
import io.github.locngu.beforeyoubuy.ui.theme.BeforeYouBuyTheme
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The wishlist's sort menu and category chips (#83). The menu is a popup window, so screenshots miss it. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-w411dp-h891dp-xxhdpi")
class WishlistSortMenuTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val now = Instant.parse("2026-10-16T10:00:00Z")
    private val items = listOf(
        Item(ItemId(1), "Lens", Money(50_000, "EUR"), now.minusSeconds(100), coolingOffDays = 30, category = "Photo"),
        Item(ItemId(2), "Spade", Money(2_000, "EUR"), now.minusSeconds(50), coolingOffDays = 30, category = "Garden")
    )

    private fun show(onSort: (WishlistSort) -> Unit = {}, onFilter: (String?) -> Unit = {}) = composeRule.setContent {
        BeforeYouBuyTheme {
            WishlistContent(
                state = WishlistUiState(
                    wishlistOf(items, emptyMap(), Savings.NONE, now, ZoneOffset.UTC),
                    active = items
                ),
                onAdd = {},
                onOpenItem = {},
                snackbarHostState = SnackbarHostState(),
                onSort = onSort,
                onFilterCategory = onFilter
            )
        }
    }

    @Test
    fun `the menu offers both sorts in both directions and reports the choice`() {
        var chosen: WishlistSort? = null
        show(onSort = { chosen = it })

        composeRule.onNodeWithText("Sort").performClick()
        listOf("Added: oldest first", "Added: newest first", "Price: low to high", "Price: high to low")
            .forEach { composeRule.onNodeWithText(it).assertIsDisplayed() }
        composeRule.onNodeWithText("Price: high to low").performClick()

        assertEquals(WishlistSort(SortField.PRICE, descending = true), chosen)
    }

    @Test
    @Config(qualifiers = "de-w411dp-h891dp-xxhdpi")
    fun `the menu is in German`() {
        show()

        composeRule.onNodeWithText("Sortieren").performClick()

        composeRule.onNodeWithText("Preis: aufsteigend").assertIsDisplayed()
        composeRule.onNodeWithText("Hinzugefügt: neueste zuerst").assertIsDisplayed()
    }

    @Test
    fun `chips show All and each category, and report the choice`() {
        var chosen: String? = "none"
        show(onFilter = { chosen = it })

        composeRule.onNodeWithText("All").assertIsDisplayed()
        composeRule.onNodeWithText("Garden").performClick()
        assertEquals("Garden", chosen)
        composeRule.onNodeWithText("All").performClick()
        assertEquals(null, chosen)
    }
}
