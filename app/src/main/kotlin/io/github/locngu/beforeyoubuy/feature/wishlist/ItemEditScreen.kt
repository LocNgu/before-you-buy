package io.github.locngu.beforeyoubuy.feature.wishlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.locngu.beforeyoubuy.R
import io.github.locngu.beforeyoubuy.feature.capture.DetailFields
import io.github.locngu.beforeyoubuy.feature.capture.LinkField
import io.github.locngu.beforeyoubuy.feature.capture.NameField
import io.github.locngu.beforeyoubuy.feature.capture.NeedToggle
import io.github.locngu.beforeyoubuy.feature.capture.PriceField
import io.github.locngu.beforeyoubuy.ui.components.EmptyState
import io.github.locngu.beforeyoubuy.ui.format.currentLocale
import io.github.locngu.beforeyoubuy.ui.format.formattedDate

/**
 * Edits an item (brief §6.3) with the same fields as quick add, all shown. Under the price it says when a change
 * extends the wait (ADR-0005). [onDone] closes the screen after saving; going back discards the changes.
 */
@Composable
fun ItemEditScreen(
    itemId: Long,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ItemEditViewModel = hiltViewModel<ItemEditViewModel, ItemEditViewModel.Factory> { it.create(itemId) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val locale = currentLocale()
    LaunchedEffect(viewModel, locale) { viewModel.setLocale(locale) }
    val done by rememberUpdatedState(onDone)
    LaunchedEffect(viewModel) { viewModel.saved.collect { done() } }
    ItemEditContent(state = state, actions = viewModel, modifier = modifier)
}

/** Stateless edit screen, for screenshots. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemEditContent(state: ItemEditUiState, actions: ItemEditActions, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.item_edit)) },
                actions = {
                    if (state.form != null) {
                        Button(
                            onClick = actions::save,
                            enabled = state.canSave,
                            modifier = Modifier.padding(end = 8.dp)
                        ) { Text(stringResource(R.string.quick_add_save)) }
                    }
                }
            )
        }
    ) { innerPadding ->
        val form = state.form
        when {
            state.gone ->
                Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    EmptyState(title = stringResource(R.string.item_gone))
                }

            form != null ->
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (state.conflict) {
                        Text(
                            stringResource(R.string.item_edit_conflict),
                            style = MaterialTheme.typography.bodyMedium,
                            // Not an input error, so not the error colour (ui.md).
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    NameField(form, state.showNameMissing, actions)
                    PriceField(
                        form = form,
                        priceError = state.priceError,
                        currency = state.currency,
                        actions = actions,
                        onDone = { if (state.canSave) actions.save() else defaultKeyboardAction(ImeAction.Done) },
                        note = state.waitNote?.let { waitNoteText(it) }
                    )
                    LinkField(form, actions)
                    NeedToggle(form, actions)
                    DetailFields(form, state.today, actions)
                }
        }
    }
}

/** "Waiting period extended to 30 Nov 2026" when saving would make the item wait longer. */
@Composable
private fun waitNoteText(note: WaitNote): String = when (note) {
    is WaitNote.Extended -> stringResource(R.string.item_edit_wait_extended, formattedDate(note.endsOn))
    is WaitNote.Starts -> stringResource(R.string.item_edit_wait_starts, formattedDate(note.endsOn))
}
