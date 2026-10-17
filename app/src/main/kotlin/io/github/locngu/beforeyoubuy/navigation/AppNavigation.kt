package io.github.locngu.beforeyoubuy.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import io.github.locngu.beforeyoubuy.feature.capture.QuickAddHost
import io.github.locngu.beforeyoubuy.feature.capture.QuickAddViewModel
import io.github.locngu.beforeyoubuy.feature.home.HomeScreen
import io.github.locngu.beforeyoubuy.feature.wishlist.WishlistScreen
import kotlinx.serialization.Serializable

@Serializable
data object Home : NavKey

@Serializable
data object Wishlist : NavKey

@Composable
fun AppNavigation() {
    val backStack = rememberNavBackStack(Home)
    // One quick add for the whole app, composed once: its `saved` events reach a single collector, and two
    // entries can be on screen together during a transition.
    val quickAdd: QuickAddViewModel = hiltViewModel()
    val snackbarHostState = remember { SnackbarHostState() }
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider =
            entryProvider {
                entry<Home> {
                    HomeScreen(
                        onAdd = { quickAdd.open() },
                        onOpenWishlist = { if (backStack.lastOrNull() != Wishlist) backStack.add(Wishlist) },
                        snackbarHostState = snackbarHostState
                    )
                }
                entry<Wishlist> {
                    WishlistScreen(onAdd = { quickAdd.open() }, snackbarHostState = snackbarHostState)
                }
            }
    )
    QuickAddHost(viewModel = quickAdd, snackbarHostState = snackbarHostState)
}
