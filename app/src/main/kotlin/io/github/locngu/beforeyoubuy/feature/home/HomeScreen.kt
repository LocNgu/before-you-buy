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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import io.github.locngu.beforeyoubuy.R
import io.github.locngu.beforeyoubuy.feature.capture.QuickAddButton
import io.github.locngu.beforeyoubuy.feature.capture.QuickAddHost
import io.github.locngu.beforeyoubuy.feature.capture.QuickAddViewModel
import io.github.locngu.beforeyoubuy.ui.components.EmptyState
import io.github.locngu.beforeyoubuy.ui.theme.BeforeYouBuyTheme

/** Home with quick add. The dashboard itself is still a placeholder. */
@Composable
fun HomeScreen(modifier: Modifier = Modifier, quickAdd: QuickAddViewModel = hiltViewModel()) {
    val snackbarHostState = remember { SnackbarHostState() }
    HomeContent(onAdd = { quickAdd.open() }, snackbarHostState = snackbarHostState, modifier = modifier)
    QuickAddHost(viewModel = quickAdd, snackbarHostState = snackbarHostState)
}

/** Placeholder until the real Home screen is built. */
@Composable
fun HomeContent(onAdd: () -> Unit, snackbarHostState: SnackbarHostState, modifier: Modifier = Modifier) {
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
                body = stringResource(R.string.home_empty_body)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeContentPreview() {
    BeforeYouBuyTheme { HomeContent(onAdd = {}, snackbarHostState = remember { SnackbarHostState() }) }
}
