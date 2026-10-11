package io.github.locngu.beforeyoubuy.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import io.github.locngu.beforeyoubuy.feature.home.HomeScreen
import kotlinx.serialization.Serializable

@Serializable
data object Home : NavKey

@Composable
fun AppNavigation() {
    val backStack = rememberNavBackStack(Home)
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider =
            entryProvider {
                entry<Home> { HomeScreen() }
            }
    )
}
