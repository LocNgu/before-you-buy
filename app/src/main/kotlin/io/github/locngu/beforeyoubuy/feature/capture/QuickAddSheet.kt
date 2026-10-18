package io.github.locngu.beforeyoubuy.feature.capture

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.locngu.beforeyoubuy.R
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.ui.format.currentLocale

/** Opens quick add. Every screen that lists items shows it (Home, the wishlist). */
@Composable
fun QuickAddButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    ExtendedFloatingActionButton(onClick = onClick, modifier = modifier) {
        Text(stringResource(R.string.quick_add_open))
    }
}

/**
 * Quick add on a screen (brief §6.2): the bottom sheet while [viewModel] has it open, and after saving a
 * snackbar in [snackbarHostState] that says how long the item waits. Open it with [QuickAddViewModel.open].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddHost(viewModel: QuickAddViewModel, snackbarHostState: SnackbarHostState) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val locale = currentLocale()
    LaunchedEffect(viewModel, locale) { viewModel.setLocale(locale) }

    // Not a key: a restart would cancel a showing snackbar, and its event is already taken from the channel.
    val resources by rememberUpdatedState(LocalResources.current)
    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.saved.collect { snackbarHostState.showSnackbar(savedMessage(resources, it)) }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Saving closes the sheet in the view model; slide it away instead of removing it at once. Opening it again
    // while it slides away has to bring it back, since the sheet never left the composition.
    LaunchedEffect(state.visible) { if (state.visible) sheetState.show() else sheetState.hide() }
    if (state.visible || sheetState.isVisible) {
        ModalBottomSheet(onDismissRequest = viewModel::dismiss, sheetState = sheetState) {
            QuickAddForm(state = state, actions = viewModel, focusNameOnStart = true)
        }
    }
}

/**
 * The quick-add form: name and price, an optional link and the need toggle, more details on request. Save stays
 * disabled until name and price are valid; errors show under a field once it has been typed in. No reflection
 * questions here: they come after capture (ADR-0009).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddForm(
    state: QuickAddUiState,
    actions: QuickAddActions,
    modifier: Modifier = Modifier,
    focusNameOnStart: Boolean = false
) {
    val nameFocus = remember { FocusRequester() }
    if (focusNameOnStart) LaunchedEffect(Unit) { nameFocus.requestFocus() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.quick_add_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = actions::save, enabled = state.canSave) {
                Text(stringResource(R.string.quick_add_save))
            }
        }

        NameField(
            form = state.form,
            showNameMissing = state.showNameMissing,
            actions = actions,
            modifier = Modifier.focusRequester(nameFocus)
        )
        PriceField(
            form = state.form,
            priceError = state.priceError,
            currency = state.currency,
            actions = actions,
            // "Done" on the keyboard saves a valid form, so a want takes FAB → name → Next → price → Done.
            onDone = { if (state.canSave) actions.save() else defaultKeyboardAction(ImeAction.Done) }
        )
        LinkField(state.form, actions)
        NeedToggle(state.form, actions)

        TextButton(onClick = { actions.onDetailsExpandedChange(!state.detailsExpanded) }) {
            val label = if (state.detailsExpanded) R.string.quick_add_fewer_details else R.string.quick_add_more_details
            Text(stringResource(label))
        }
        if (state.detailsExpanded) DetailFields(state.form, state.today, actions)
    }
}

private fun savedMessage(resources: Resources, saved: QuickAddSaved): String = when (saved.kind) {
    ItemKind.NEED -> resources.getString(R.string.quick_add_saved_need)

    ItemKind.WANT ->
        resources.getQuantityString(R.plurals.quick_add_saved_want, saved.coolingOffDays, saved.coolingOffDays)
}
