package io.github.locngu.beforeyoubuy.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.locngu.beforeyoubuy.feature.capture.QuickAddHost
import io.github.locngu.beforeyoubuy.feature.capture.QuickAddViewModel
import io.github.locngu.beforeyoubuy.feature.home.HomeScreen
import io.github.locngu.beforeyoubuy.feature.wishlist.ItemDetailScreen
import io.github.locngu.beforeyoubuy.feature.wishlist.ItemEditScreen
import io.github.locngu.beforeyoubuy.feature.wishlist.WishlistScreen
import kotlinx.serialization.Serializable

@Serializable
data object Home : NavKey

@Serializable
data object Wishlist : NavKey

/** One item's detail (brief §6.3). The id is [io.github.locngu.beforeyoubuy.core.domain.item.ItemId.value]. */
@Serializable
data class ItemDetail(val itemId: Long) : NavKey

@Serializable
data class ItemEdit(val itemId: Long) : NavKey

@Composable
fun AppNavigation() {
    val backStack = rememberNavBackStack(Home)
    // One quick add for the whole app, composed once and scoped to the activity, outside every entry: its `saved`
    // events reach a single collector, and two entries can be on screen together during a transition.
    val quickAdd: QuickAddViewModel = hiltViewModel()
    val snackbarHostState = remember { SnackbarHostState() }
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        // Each entry gets its own view models, cleared when it leaves the back stack (ADR-0037).
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider =
            entryProvider {
                entry<Home> {
                    HomeScreen(
                        onAdd = { quickAdd.open() },
                        onOpenWishlist = { backStack.open(Wishlist) },
                        snackbarHostState = snackbarHostState
                    )
                }
                entry<Wishlist> {
                    WishlistScreen(
                        onAdd = { quickAdd.open() },
                        onOpenItem = { backStack.open(ItemDetail(it.value)) },
                        snackbarHostState = snackbarHostState
                    )
                }
                entry<ItemDetail> { key ->
                    ItemDetailScreen(itemId = key.itemId, onEdit = { backStack.open(ItemEdit(key.itemId)) })
                }
                entry<ItemEdit> { key ->
                    ItemEditScreen(
                        itemId = key.itemId,
                        // Only the edit screen itself closes, even if the save lands after another tap.
                        onDone = { if (backStack.lastOrNull() == key) backStack.removeLastOrNull() }
                    )
                }
            }
    )
    QuickAddHost(viewModel = quickAdd, snackbarHostState = snackbarHostState)
}

/** Adds [key] unless it's already on top, so a double tap doesn't open a screen twice. */
private fun NavBackStack<NavKey>.open(key: NavKey) {
    if (lastOrNull() != key) add(key)
}
