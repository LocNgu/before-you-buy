package io.github.locngu.beforeyoubuy.feature.wishlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.locngu.beforeyoubuy.R
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.Stage
import io.github.locngu.beforeyoubuy.core.domain.wishlist.WishlistEntry
import io.github.locngu.beforeyoubuy.core.domain.wishlist.WishlistGroup
import io.github.locngu.beforeyoubuy.feature.capture.QuickAddButton
import io.github.locngu.beforeyoubuy.ui.components.CountdownText
import io.github.locngu.beforeyoubuy.ui.components.EmptyState
import io.github.locngu.beforeyoubuy.ui.components.MoneyText
import io.github.locngu.beforeyoubuy.ui.components.SectionHeader

/**
 * The wishlist (brief §6.3). Quick add is hosted by the navigation, so this only asks to open it with [onAdd]
 * and shows messages in [snackbarHostState].
 */
@Composable
fun WishlistScreen(
    onAdd: () -> Unit,
    onOpenItem: (ItemId) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: WishlistViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    WishlistContent(
        state = state,
        onAdd = onAdd,
        onOpenItem = onOpenItem,
        snackbarHostState = snackbarHostState,
        modifier = modifier
    )
}

/** Stateless wishlist, for screenshots. Items are grouped by stage; the soonest cooling-off end comes first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistContent(
    state: WishlistUiState,
    onAdd: () -> Unit,
    onOpenItem: (ItemId) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.wishlist_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = { QuickAddButton(onClick = onAdd) }
    ) { innerPadding ->
        val groups = state.groups
        when {
            groups == null -> Unit

            groups.isEmpty() ->
                Column(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    EmptyState(
                        title = stringResource(R.string.wishlist_empty_title),
                        body = stringResource(R.string.wishlist_empty_body)
                    )
                }

            else -> WishlistList(groups, onOpenItem, Modifier.padding(innerPadding))
        }
    }
}

@Composable
private fun WishlistList(groups: List<WishlistGroup>, onOpenItem: (ItemId) -> Unit, modifier: Modifier = Modifier) {
    // Leaves room for the floating button, so the last row isn't hidden behind it.
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        groups.forEach { group ->
            item(key = "header-${group.stage}") { SectionHeader(stageLabel(group.stage)) }
            items(group.entries, key = { it.item.id.value }) { entry ->
                WishlistRow(entry, onClick = { onOpenItem(entry.item.id) })
            }
        }
    }
}

@Composable
private fun WishlistRow(entry: WishlistEntry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = entry.item.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StageChip(entry.stage)
                StageStatus(entry.stage, entry.daysLeft)
            }
        }
        MoneyText(
            money = entry.item.price,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** A countdown while cooling off, otherwise what the item is waiting for. Informational, never urgent. */
@Composable
internal fun StageStatus(stage: Stage, daysLeft: Int) {
    val style = MaterialTheme.typography.bodyMedium
    when (stage) {
        Stage.COOLING_OFF -> CountdownText(daysLeft, style = style)

        Stage.NEEDS_REFLECTION -> CountdownText(0, style = style)

        Stage.SAVING -> Unit

        Stage.READY ->
            Text(
                text = stringResource(R.string.wishlist_ready_to_decide),
                style = style,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
    }
}

@Composable
internal fun StageChip(stage: Stage) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            text = stageLabel(stage),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
internal fun stageLabel(stage: Stage): String = stringResource(
    when (stage) {
        Stage.COOLING_OFF -> R.string.stage_cooling_off
        Stage.NEEDS_REFLECTION -> R.string.stage_needs_reflection
        Stage.SAVING -> R.string.stage_saving
        Stage.READY -> R.string.stage_ready
    }
)
