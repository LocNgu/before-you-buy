package io.github.locngu.beforeyoubuy.feature.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.locngu.beforeyoubuy.R
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.core.domain.money.MoneyParseResult
import io.github.locngu.beforeyoubuy.core.domain.money.format
import io.github.locngu.beforeyoubuy.core.domain.pool.AllowanceSettings
import io.github.locngu.beforeyoubuy.core.domain.pool.FunMoney
import io.github.locngu.beforeyoubuy.feature.capture.priceErrorMessage
import io.github.locngu.beforeyoubuy.ui.components.MoneyText
import io.github.locngu.beforeyoubuy.ui.components.SectionHeader
import io.github.locngu.beforeyoubuy.ui.format.currencySymbol
import io.github.locngu.beforeyoubuy.ui.format.currentLocale
import io.github.locngu.beforeyoubuy.ui.format.formattedDate

/**
 * The monthly fun money (brief §4.5): set it up, change it, adjust the balance. Opened from Home; the Settings screen
 * (#26) will link here too. [onDone] closes it after saving; going back discards the form.
 */
@Composable
fun FunMoneyScreen(onDone: () -> Unit, modifier: Modifier = Modifier, viewModel: FunMoneyViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val locale = currentLocale()
    LaunchedEffect(viewModel, locale) { viewModel.setLocale(locale) }
    val done by rememberUpdatedState(onDone)
    LaunchedEffect(viewModel) { viewModel.saved.collect { done() } }
    FunMoneyContent(state = state, actions = viewModel, modifier = modifier)
}

/** Stateless fun money screen, for screenshots. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FunMoneyContent(state: FunMoneyUiState, actions: FunMoneyActions, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.fun_money_title)) },
                actions = {
                    if (state.form != null) {
                        Button(
                            onClick = actions::save,
                            enabled = state.canSave,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(stringResource(R.string.quick_add_save))
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        val form = state.form ?: return@Scaffold
        val setUp = state.money as? FunMoney.SetUp
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.fun_money_explained),
                modifier = Modifier.padding(horizontal = 24.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (setUp != null) Balance(setUp)
            SectionHeader(stringResource(R.string.fun_money_monthly_title))
            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AmountField(
                    value = form.monthly,
                    onValueChange = actions::onMonthlyChange,
                    label = stringResource(R.string.fun_money_monthly),
                    error = state.monthlyError,
                    currency = state.currency
                )
                AccrualDayField(
                    day = form.accrualDay,
                    onDayChange = actions::onAccrualDayChange,
                    note = state.firstAccrual?.let {
                        stringResource(R.string.fun_money_first_accrual, formattedDate(it))
                    }
                )
                if (setUp == null) {
                    AmountField(
                        value = form.startingBalance,
                        onValueChange = actions::onStartingBalanceChange,
                        label = stringResource(R.string.fun_money_starting_balance),
                        error = state.startingBalanceError,
                        currency = state.currency,
                        note = stringResource(R.string.fun_money_starting_balance_explained)
                    )
                } else {
                    Text(
                        text = stringResource(R.string.fun_money_changes_apply),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (setUp != null) Adjust(state, form, actions)
        }
        state.confirmTakeOut?.let { takeOut ->
            val locale = currentLocale()
            AlertDialog(
                onDismissRequest = actions::dismissTakeOut,
                title = { Text(stringResource(R.string.fun_money_below_zero_title)) },
                text = {
                    Text(
                        stringResource(
                            R.string.fun_money_below_zero_body,
                            takeOut.amount.format(locale),
                            takeOut.leaves.format(locale)
                        )
                    )
                },
                confirmButton = {
                    TextButton(onClick = actions::confirmTakeOut) { Text(stringResource(R.string.fun_money_take_out)) }
                },
                dismissButton = {
                    TextButton(onClick = actions::dismissTakeOut) { Text(stringResource(R.string.dialog_cancel)) }
                }
            )
        }
    }
}

/** The same three amounts as Home, and when the next monthly amount is added. */
@Composable
private fun Balance(money: FunMoney.SetUp) {
    SectionHeader(stringResource(R.string.fun_money_balance_title))
    Column(modifier = Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AmountRow(stringResource(R.string.home_money_pool), money.balance.pool)
        AmountRow(stringResource(R.string.home_money_committed), money.balance.committed)
        AmountRow(stringResource(R.string.home_money_unallocated), money.balance.unallocated)
        Text(
            text = stringResource(R.string.fun_money_next_accrual, formattedDate(money.nextAccrual)),
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** "Adjust balance": an amount, a note, and two equal buttons to add it or take it out. */
@Composable
private fun Adjust(state: FunMoneyUiState, form: FunMoneyForm, actions: FunMoneyActions) {
    SectionHeader(stringResource(R.string.fun_money_adjust_title))
    Column(modifier = Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.fun_money_adjust_explained),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AmountField(
            value = form.adjustment,
            onValueChange = actions::onAdjustmentChange,
            label = stringResource(R.string.fun_money_adjust_amount),
            error = state.adjustmentError,
            currency = state.currency
        )
        OutlinedTextField(
            value = form.note,
            onValueChange = actions::onNoteChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.fun_money_adjust_note)) },
            singleLine = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = actions::add, enabled = state.canAdjust, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.fun_money_add))
            }
            OutlinedButton(onClick = actions::takeOut, enabled = state.canAdjust, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.fun_money_take_out))
            }
        }
    }
}

@Composable
private fun AmountRow(label: String, amount: Money) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            modifier = Modifier.weight(1f).padding(end = 16.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        MoneyText(amount, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** An amount in the app's currency, read like a price (ADR-0027). */
@Composable
private fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: MoneyParseResult?,
    currency: String?,
    note: String? = null
) {
    val message = error?.let { priceErrorMessage(it, currency, missing = R.string.fun_money_amount_missing) }
    val supporting = message ?: note
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        suffix = currency?.let { { Text(currencySymbol(it)) } },
        isError = message != null,
        supportingText = supporting?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done)
    )
}

/** The day of the month the amount is added: 1–28, which every month has. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccrualDayField(day: Int, onDayChange: (Int) -> Unit, note: String?) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = day.toString(),
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            label = { Text(stringResource(R.string.fun_money_accrual_day)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            supportingText = note?.let { { Text(it) } },
            singleLine = true
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            (1..AllowanceSettings.MAX_ACCRUAL_DAY).forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.toString()) },
                    onClick = {
                        onDayChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
