package io.github.locngu.beforeyoubuy.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.LocalResources
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.locngu.beforeyoubuy.R
import io.github.locngu.beforeyoubuy.feature.capture.QuickAddHost
import io.github.locngu.beforeyoubuy.feature.capture.QuickAddViewModel
import io.github.locngu.beforeyoubuy.feature.decision.DecisionOutcome
import io.github.locngu.beforeyoubuy.feature.decision.DecisionScreen
import io.github.locngu.beforeyoubuy.feature.history.HistoryItemScreen
import io.github.locngu.beforeyoubuy.feature.history.HistoryScreen
import io.github.locngu.beforeyoubuy.feature.home.HomeScreen
import io.github.locngu.beforeyoubuy.feature.money.FunMoneyScreen
import io.github.locngu.beforeyoubuy.feature.reflection.ReflectionScreen
import io.github.locngu.beforeyoubuy.feature.wishlist.ItemDetailScreen
import io.github.locngu.beforeyoubuy.feature.wishlist.ItemEditScreen
import io.github.locngu.beforeyoubuy.feature.wishlist.WishlistScreen
import io.github.locngu.beforeyoubuy.notifications.NotificationPermissionPrompt
import io.github.locngu.beforeyoubuy.notifications.NotificationPermissionViewModel
import io.github.locngu.beforeyoubuy.notifications.NotificationTap
import io.github.locngu.beforeyoubuy.notifications.NotificationTarget
import kotlinx.coroutines.launch
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

/** The reflection questions for one item (brief §6.4). The decision screen and nudges send unanswered items here. */
@Serializable
data class ItemReflection(val itemId: Long) : NavKey

/** Buy, Wait or Reject for one item (brief §6.5). */
@Serializable
data class ItemDecision(val itemId: Long) : NavKey

/** "Bought" and "Decided against" (brief §6.6). */
@Serializable
data object History : NavKey

/** One purchased or decided-against item, read-only (brief §6.6). */
@Serializable
data class HistoryItem(val itemId: Long) : NavKey

/** Setting up and changing the monthly fun money (brief §4.5). */
@Serializable
data object FunMoney : NavKey

/**
 * The app's screens. [notificationTap], if set, replaces the back stack with the screens it leads to
 * ([backStackFor]); [onNotificationTapShown] then clears it, so it's shown once.
 */
@Composable
fun AppNavigation(notificationTap: NotificationTap? = null, onNotificationTapShown: () -> Unit = {}) {
    val backStack = rememberNavBackStack(Home)
    LaunchedEffect(notificationTap) {
        if (notificationTap == null) return@LaunchedEffect
        val keys = backStackFor(notificationTap)
        // One change, so no frame shows an empty back stack.
        Snapshot.withMutableSnapshot {
            backStack.clear()
            backStack.addAll(keys)
        }
        onNotificationTapShown()
    }
    // One quick add for the whole app, composed once and scoped to the activity, outside every entry: its `saved`
    // events reach a single collector, and two entries can be on screen together during a transition.
    val quickAdd: QuickAddViewModel = hiltViewModel()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
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
                        // Straight to the decision, which offers the questions if answers are missing. Back returns to Home.
                        onOpenDecision = { backStack.open(ItemDecision(it.value)) },
                        onOpenWishlist = { backStack.open(Wishlist) },
                        onOpenHistory = { backStack.open(History) },
                        onOpenFunMoney = { backStack.open(FunMoney) },
                        onOpenItem = { backStack.open(ItemDetail(it.value)) },
                        snackbarHostState = snackbarHostState
                    )
                }
                entry<FunMoney> {
                    // As for edit: only the screen itself closes, even if the save lands after another tap.
                    FunMoneyScreen(onDone = { if (backStack.lastOrNull() == FunMoney) backStack.removeLastOrNull() })
                }
                entry<Wishlist> {
                    WishlistScreen(
                        onAdd = { quickAdd.open() },
                        onOpenItem = { backStack.open(ItemDetail(it.value)) },
                        snackbarHostState = snackbarHostState
                    )
                }
                entry<ItemDetail> { key ->
                    ItemDetailScreen(
                        itemId = key.itemId,
                        onEdit = { backStack.open(ItemEdit(key.itemId)) },
                        onReflect = { backStack.open(ItemReflection(key.itemId)) },
                        onDecide = { backStack.open(ItemDecision(key.itemId)) },
                        onOpenFunMoney = { backStack.open(FunMoney) }
                    )
                }
                entry<ItemEdit> { key ->
                    ItemEditScreen(
                        itemId = key.itemId,
                        // Only the edit screen itself closes, even if the save lands after another tap.
                        onDone = { if (backStack.lastOrNull() == key) backStack.removeLastOrNull() }
                    )
                }
                entry<ItemDecision> { key ->
                    DecisionScreen(
                        itemId = key.itemId,
                        onDecided = { outcome ->
                            // Only the decision screen itself closes, even if the decision lands after another tap.
                            if (backStack.lastOrNull() != key) return@DecisionScreen
                            backStack.removeLastOrNull()
                            if (outcome is DecisionOutcome.Waiting) return@DecisionScreen
                            // Bought or decided against: the item has ended, so its detail closes too, and the
                            // screen below says where it went.
                            if (backStack.lastOrNull() == ItemDetail(key.itemId)) backStack.removeLastOrNull()
                            val message = if (outcome is DecisionOutcome.Rejected) {
                                R.string.decision_rejected
                            } else {
                                R.string.decision_bought
                            }
                            scope.launch { snackbarHostState.showSnackbar(resources.getString(message)) }
                        },
                        onReflect = { backStack.open(ItemReflection(key.itemId)) }
                    )
                }
                entry<History> {
                    HistoryScreen(onOpenItem = { backStack.open(HistoryItem(it.value)) })
                }
                entry<HistoryItem> { key ->
                    HistoryItemScreen(
                        itemId = key.itemId,
                        // As for edit: only the entry itself closes, even if the write lands after another tap.
                        onDeleted = { if (backStack.lastOrNull() == key) backStack.removeLastOrNull() },
                        onWantedAgain = { id ->
                            if (backStack.lastOrNull() != key) return@HistoryItemScreen
                            // The entry stays in history; the new item opens in its place, so back leads to history.
                            backStack.removeLastOrNull()
                            backStack.open(ItemDetail(id.value))
                        }
                    )
                }
                entry<ItemReflection> { key ->
                    ReflectionScreen(
                        itemId = key.itemId,
                        // As for edit: only the flow itself closes, even if "Done" lands after another tap.
                        onDone = { if (backStack.lastOrNull() == key) backStack.removeLastOrNull() }
                    )
                }
            }
    )
    QuickAddHost(viewModel = quickAdd, snackbarHostState = snackbarHostState)
    // App-wide like quick add: asks once, after the first item is added (Android 13+).
    val notificationPermission: NotificationPermissionViewModel = hiltViewModel()
    NotificationPermissionPrompt(notificationPermission)
}

/**
 * The back stack a notification tap opens: its screen on top of the screens that lead there, so back goes through
 * item detail and the wishlist to Home.
 */
fun backStackFor(tap: NotificationTap): List<NavKey> {
    val itemId = tap.itemId?.value
    val toWishlist = listOf(Home, Wishlist)
    if (itemId == null) return toWishlist
    val toItem = toWishlist + ItemDetail(itemId)
    return when (tap.target) {
        NotificationTarget.WISHLIST -> toWishlist
        NotificationTarget.ITEM -> toItem
        NotificationTarget.DECISION -> toItem + ItemDecision(itemId)
        NotificationTarget.REFLECTION -> toItem + ItemReflection(itemId)
    }
}

/** Adds [key] unless it's already on top, so a double tap doesn't open a screen twice. */
private fun NavBackStack<NavKey>.open(key: NavKey) {
    if (lastOrNull() != key) add(key)
}
