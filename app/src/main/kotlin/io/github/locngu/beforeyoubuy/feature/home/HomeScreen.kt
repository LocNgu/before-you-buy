package io.github.locngu.beforeyoubuy.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.locngu.beforeyoubuy.R
import io.github.locngu.beforeyoubuy.feature.capture.QuickAddButton
import io.github.locngu.beforeyoubuy.ui.components.EmptyState
import io.github.locngu.beforeyoubuy.ui.components.SecondaryButton
import io.github.locngu.beforeyoubuy.ui.theme.BeforeYouBuyTheme

/**
 * Home. The dashboard itself is still a placeholder (#21); for now it links to the wishlist and history. Quick add is hosted by
 * the navigation, so this only asks to open it with [onAdd].
 */
@Composable
fun HomeScreen(
    onAdd: () -> Unit,
    onOpenWishlist: () -> Unit,
    onOpenHistory: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    HomeContent(
        onAdd = onAdd,
        onOpenWishlist = onOpenWishlist,
        onOpenHistory = onOpenHistory,
        snackbarHostState = snackbarHostState,
        modifier = modifier
    )
}

/** Placeholder until the real Home screen is built. */
@Composable
fun HomeContent(
    onAdd: () -> Unit,
    onOpenWishlist: () -> Unit,
    onOpenHistory: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = { QuickAddButton(onClick = onAdd) }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium
            )
            EmptyState(
                title = stringResource(R.string.home_empty_title),
                body = stringResource(R.string.home_empty_body),
                action = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        SecondaryButton(text = stringResource(R.string.wishlist_open), onClick = onOpenWishlist)
                        TextButton(onClick = onOpenHistory) { Text(stringResource(R.string.history_open)) }
                    }
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeContentPreview() {
    BeforeYouBuyTheme {
        HomeContent(
            onAdd = {},
            onOpenWishlist = {},
            onOpenHistory = {},
            snackbarHostState = remember { SnackbarHostState() }
        )
    }
}
