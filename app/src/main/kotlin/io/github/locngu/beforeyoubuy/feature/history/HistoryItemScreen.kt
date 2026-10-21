package io.github.locngu.beforeyoubuy.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.locngu.beforeyoubuy.R
import io.github.locngu.beforeyoubuy.core.domain.history.HistoryDetail
import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.core.domain.money.format
import io.github.locngu.beforeyoubuy.feature.wishlist.Answer
import io.github.locngu.beforeyoubuy.feature.wishlist.answersOf
import io.github.locngu.beforeyoubuy.ui.components.EmptyState
import io.github.locngu.beforeyoubuy.ui.components.MoneyText
import io.github.locngu.beforeyoubuy.ui.components.SectionHeader
import io.github.locngu.beforeyoubuy.ui.format.currentLocale
import io.github.locngu.beforeyoubuy.ui.format.formattedDate

/**
 * One history entry (brief §6.6), read-only. [onDeleted] runs once it's deleted, [onWantedAgain] with the new item
 * once "Want it again?" added it.
 */
@Composable
fun HistoryItemScreen(
    itemId: Long,
    onDeleted: () -> Unit,
    onWantedAgain: (ItemId) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryItemViewModel =
        hiltViewModel<HistoryItemViewModel, HistoryItemViewModel.Factory> { it.create(itemId) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources by rememberUpdatedState(LocalResources.current)
    val deleted by rememberUpdatedState(onDeleted)
    val wantedAgain by rememberUpdatedState(onWantedAgain)
    LaunchedEffect(viewModel) {
        viewModel.outcome.collect {
            when (it) {
                HistoryItemOutcome.Deleted -> deleted()
                is HistoryItemOutcome.WantedAgain -> wantedAgain(it.id)
            }
        }
    }
    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.message.collect {
            val text = when (it) {
                HistoryItemMessage.CHANGED_MEANWHILE -> resources.getString(R.string.item_changed_meanwhile)
            }
            snackbarHostState.showSnackbar(text)
        }
    }
    HistoryItemContent(
        state = state,
        onWantAgain = viewModel::wantAgain,
        onDelete = viewModel::delete,
        snackbarHostState = snackbarHostState,
        modifier = modifier
    )
}

/**
 * Stateless history entry, for screenshots: what was bought or decided against, the user's original reasons, and
 * for a decided-against item "Want it again?". Deleting is in the overflow menu and always asks first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryItemContent(
    state: HistoryItemUiState,
    onWantAgain: () -> Unit,
    onDelete: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val detail = state.detail
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {},
                actions = { if (detail != null) DeleteMenu(detail.item, enabled = !state.busy, onDelete) }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        when {
            state.gone ->
                Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    EmptyState(title = stringResource(R.string.history_item_gone))
                }

            detail != null ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (detail) {
                        is HistoryDetail.Bought -> BoughtHeader(detail)
                        is HistoryDetail.DecidedAgainst -> DecidedAgainstHeader(detail)
                    }
                    Reasons(detail)
                    Details(detail.item)
                    if (detail is HistoryDetail.DecidedAgainst) WantAgain(enabled = !state.busy, onWantAgain)
                }
        }
    }
}

private val HistoryDetail.item: Item
    get() = when (this) {
        is HistoryDetail.Bought -> entry.item
        is HistoryDetail.DecidedAgainst -> entry.item
    }

@Composable
private fun BoughtHeader(detail: HistoryDetail.Bought) {
    val entry = detail.entry
    Header(entry.item.name, entry.paid) {
        Secondary(stringResource(R.string.history_bought_on, formattedDate(entry.boughtOn)))
        Secondary(consideredFor(entry.consideredForDays))
        entry.wishlistPrice?.let {
            Secondary(stringResource(R.string.history_wishlist_price, it.format(currentLocale())))
        }
        Flags(entry)
    }
    entry.decision.earlyReason?.let { Answer(stringResource(R.string.history_early_reason), it) }
}

@Composable
private fun DecidedAgainstHeader(detail: HistoryDetail.DecidedAgainst) {
    val entry = detail.entry
    Header(entry.item.name, entry.price) {
        Secondary(stringResource(R.string.history_decided_on, formattedDate(entry.decidedOn)))
    }
    entry.note?.let { Answer(stringResource(R.string.history_note), it) }
}

@Composable
private fun Header(name: String, amount: Money, lines: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(name, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        MoneyText(amount, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        lines()
    }
}

/** The reflection answers as they were when the item ended: the user's own learning material. */
@Composable
private fun Reasons(detail: HistoryDetail) {
    val answers = detail.reflection?.let { answersOf(it) }.orEmpty()
    if (answers.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(stringResource(R.string.history_reasons_title))
        answers.forEach { (label, value) -> Answer(label, value) }
    }
}

@Composable
private fun Details(item: Item) {
    val answers = listOfNotNull(
        item.category?.let { stringResource(R.string.item_category) to it },
        item.notes?.let { stringResource(R.string.item_notes) to it }
    )
    if (answers.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(stringResource(R.string.item_details_title))
        answers.forEach { (label, value) -> Answer(label, value) }
    }
}

/** Puts the item back on the wishlist as a new item, after saying what that means. */
@Composable
private fun WantAgain(enabled: Boolean, onWantAgain: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = {
        confirming = true
    }, enabled = enabled, modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(stringResource(R.string.history_want_again))
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.history_want_again_title)) },
            text = { Text(stringResource(R.string.history_want_again_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        onWantAgain()
                    }
                ) { Text(stringResource(R.string.history_want_again_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.dialog_cancel)) }
            }
        )
    }
}

/** "Delete from history" in the overflow menu: only on purpose, and only after confirming (issue #19). */
@Composable
private fun DeleteMenu(item: Item, enabled: Boolean, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painterResource(R.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.item_more_options)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.history_delete)) },
                enabled = enabled,
                onClick = {
                    expanded = false
                    confirming = true
                }
            )
        }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.history_delete_title)) },
            text = { Text(stringResource(R.string.history_delete_body, item.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        onDelete()
                    }
                ) { Text(stringResource(R.string.history_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.dialog_cancel)) }
            }
        )
    }
}

@Composable
private fun Secondary(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
