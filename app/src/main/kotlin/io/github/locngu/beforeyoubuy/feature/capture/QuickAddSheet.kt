package io.github.locngu.beforeyoubuy.feature.capture

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.locngu.beforeyoubuy.R
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.core.domain.money.MoneyParseResult
import io.github.locngu.beforeyoubuy.core.domain.money.formatForInput
import io.github.locngu.beforeyoubuy.ui.format.currencySymbol
import io.github.locngu.beforeyoubuy.ui.format.currentLocale
import io.github.locngu.beforeyoubuy.ui.format.formattedDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

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

        val nameError = if (state.showNameMissing) stringResource(R.string.quick_add_name_missing) else null
        OutlinedTextField(
            value = state.form.name,
            onValueChange = actions::onNameChange,
            modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
            label = { Text(stringResource(R.string.quick_add_name)) },
            isError = state.showNameMissing,
            supportingText = errorText(nameError),
            singleLine = true,
            keyboardOptions =
                KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next)
        )

        val priceError = state.priceError?.let { priceErrorMessage(it, state.currency) }
        OutlinedTextField(
            value = state.form.price,
            onValueChange = actions::onPriceChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.quick_add_price)) },
            suffix = state.currency?.let { currency -> { Text(currencySymbol(currency)) } },
            isError = priceError != null,
            supportingText = errorText(priceError),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            // "Done" on the keyboard saves a valid form, so a want takes FAB → name → Next → price → Done.
            keyboardActions = KeyboardActions(
                onDone = { if (state.canSave) actions.save() else defaultKeyboardAction(ImeAction.Done) }
            )
        )

        OutlinedTextField(
            value = state.form.link,
            onValueChange = actions::onLinkChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.quick_add_link)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done)
        )

        NeedToggle(need = state.form.kind == ItemKind.NEED, onNeedChange = actions::onNeedChange)

        TextButton(onClick = { actions.onDetailsExpandedChange(!state.detailsExpanded) }) {
            val label = if (state.detailsExpanded) R.string.quick_add_fewer_details else R.string.quick_add_more_details
            Text(stringResource(label))
        }
        if (state.detailsExpanded) Details(state, actions)
    }
}

@Composable
private fun NeedToggle(need: Boolean, onNeedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = need, role = Role.Switch, onValueChange = onNeedChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(stringResource(R.string.quick_add_need), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(R.string.quick_add_need_explained),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // The row handles the toggle, so the whole line is one touch target with one announcement.
        Switch(checked = need, onCheckedChange = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Details(state: QuickAddUiState, actions: QuickAddActions) {
    OutlinedTextField(
        value = state.form.category,
        onValueChange = actions::onCategoryChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.quick_add_category)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next)
    )
    OutlinedTextField(
        value = state.form.notes,
        onValueChange = actions::onNotesChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.quick_add_notes)) },
        minLines = 2,
        maxLines = 5,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
    )

    var picking by rememberSaveable { mutableStateOf(false) }
    val targetDate = state.form.targetDate
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { picking = true }) {
            Text(
                if (targetDate == null) {
                    stringResource(R.string.quick_add_target_date_none)
                } else {
                    stringResource(R.string.quick_add_target_date, formattedDate(targetDate))
                }
            )
        }
        if (targetDate != null) {
            TextButton(onClick = { actions.onTargetDateChange(null) }) {
                Text(stringResource(R.string.quick_add_target_date_remove))
            }
        }
    }
    if (picking) {
        TargetDatePicker(
            initial = targetDate,
            earliest = state.today,
            onPicked = {
                actions.onTargetDateChange(it)
                picking = false
            },
            onDismiss = { picking = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TargetDatePicker(
    initial: LocalDate?,
    earliest: LocalDate?,
    onPicked: (LocalDate?) -> Unit,
    onDismiss: () -> Unit
) {
    val selectable = remember(earliest) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) =
                earliest == null || utcTimeMillis.toUtcDate() >= earliest

            override fun isSelectableYear(year: Int) = earliest == null || year >= earliest.year
        }
    }
    val pickerState =
        rememberDatePickerState(initialSelectedDateMillis = initial?.toUtcMillis(), selectableDates = selectable)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onPicked(pickerState.selectedDateMillis?.toUtcDate()) }) {
                Text(stringResource(R.string.dialog_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        }
    ) {
        DatePicker(state = pickerState)
    }
}

/** What's wrong with the typed price; null for a price that reads. */
@Composable
private fun priceErrorMessage(error: MoneyParseResult, currency: String?): String? {
    val locale = currentLocale()
    return when (error) {
        is MoneyParseResult.Success -> null

        MoneyParseResult.Empty -> stringResource(R.string.quick_add_price_missing)

        MoneyParseResult.Invalid -> {
            val example = remember(currency, locale) { currency?.let { Money(1_250, it).formatForInput(locale) } }
            if (example == null) {
                stringResource(R.string.quick_add_price_invalid_no_example)
            } else {
                stringResource(R.string.quick_add_price_invalid, example)
            }
        }

        is MoneyParseResult.TooManyDecimals -> if (error.maxDecimals == 0) {
            stringResource(R.string.quick_add_price_whole_only)
        } else {
            pluralStringResource(R.plurals.quick_add_price_too_many_decimals, error.maxDecimals, error.maxDecimals)
        }

        MoneyParseResult.TooLarge -> stringResource(R.string.quick_add_price_too_large)
    }
}

private fun errorText(message: String?): (@Composable () -> Unit)? = message?.let { { Text(it) } }

private fun savedMessage(resources: Resources, saved: QuickAddSaved): String = when (saved.kind) {
    ItemKind.NEED -> resources.getString(R.string.quick_add_saved_need)

    ItemKind.WANT ->
        resources.getQuantityString(R.plurals.quick_add_saved_want, saved.coolingOffDays, saved.coolingOffDays)
}

// The date picker works in UTC midnight milliseconds, whatever the device's zone.
private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toUtcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
