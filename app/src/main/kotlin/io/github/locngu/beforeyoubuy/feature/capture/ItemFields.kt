package io.github.locngu.beforeyoubuy.feature.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActionScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.locngu.beforeyoubuy.R
import io.github.locngu.beforeyoubuy.core.domain.capture.CaptureForm
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

// The item fields quick add and edit share, so both read and check them the same way.

/** Changes to the fields of a [CaptureForm], from quick add or edit. */
interface ItemFormActions {
    fun onNameChange(name: String)

    fun onPriceChange(price: String)

    fun onLinkChange(link: String)

    fun onNeedChange(need: Boolean)

    fun onCategoryChange(category: String)

    fun onNotesChange(notes: String)

    fun onTargetDateChange(date: LocalDate?)
}

@Composable
internal fun NameField(
    form: CaptureForm,
    showNameMissing: Boolean,
    actions: ItemFormActions,
    modifier: Modifier = Modifier
) {
    val nameError = if (showNameMissing) stringResource(R.string.quick_add_name_missing) else null
    OutlinedTextField(
        value = form.name,
        onValueChange = actions::onNameChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.quick_add_name)) },
        isError = showNameMissing,
        supportingText = supportingText(nameError),
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next)
    )
}

/**
 * The price, with the currency's sign. [priceError] is shown under it; without one, [note] is (e.g. how the wait
 * changes). [onDone] is what "Done" on the keyboard does.
 */
@Composable
internal fun PriceField(
    form: CaptureForm,
    priceError: MoneyParseResult?,
    currency: String?,
    actions: ItemFormActions,
    onDone: KeyboardActionScope.() -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null
) {
    val error = priceError?.let { priceErrorMessage(it, currency) }
    OutlinedTextField(
        value = form.price,
        onValueChange = actions::onPriceChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.quick_add_price)) },
        suffix = currency?.let { { Text(currencySymbol(it)) } },
        isError = error != null,
        supportingText = supportingText(error ?: note),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = onDone)
    )
}

@Composable
internal fun LinkField(form: CaptureForm, actions: ItemFormActions) {
    OutlinedTextField(
        value = form.link,
        onValueChange = actions::onLinkChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.quick_add_link)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done)
    )
}

@Composable
internal fun NeedToggle(form: CaptureForm, actions: ItemFormActions) {
    val need = form.kind == ItemKind.NEED
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = need, role = Role.Switch, onValueChange = actions::onNeedChange)
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

/** Category, notes and target date. [today] is the earliest date the picker offers. */
@Composable
internal fun DetailFields(form: CaptureForm, today: LocalDate?, actions: ItemFormActions) {
    OutlinedTextField(
        value = form.category,
        onValueChange = actions::onCategoryChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.quick_add_category)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next)
    )
    OutlinedTextField(
        value = form.notes,
        onValueChange = actions::onNotesChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.quick_add_notes)) },
        minLines = 2,
        maxLines = 5,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
    )

    var picking by rememberSaveable { mutableStateOf(false) }
    val targetDate = form.targetDate
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
            earliest = today,
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

private fun supportingText(message: String?): (@Composable () -> Unit)? = message?.let { { Text(it) } }

// The date picker works in UTC midnight milliseconds, whatever the device's zone.
private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toUtcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
