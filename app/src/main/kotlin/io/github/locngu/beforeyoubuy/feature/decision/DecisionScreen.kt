package io.github.locngu.beforeyoubuy.feature.decision

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.locngu.beforeyoubuy.R
import io.github.locngu.beforeyoubuy.core.domain.decision.BuyPath
import io.github.locngu.beforeyoubuy.core.domain.decision.DecisionForm
import io.github.locngu.beforeyoubuy.core.domain.decision.DecisionOverview
import io.github.locngu.beforeyoubuy.core.domain.decision.WAIT_DAYS
import io.github.locngu.beforeyoubuy.core.domain.decision.keepFor
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.Stage
import io.github.locngu.beforeyoubuy.core.domain.money.format
import io.github.locngu.beforeyoubuy.feature.capture.DayPicker
import io.github.locngu.beforeyoubuy.feature.capture.priceErrorMessage
import io.github.locngu.beforeyoubuy.feature.money.SavingsProgressBar
import io.github.locngu.beforeyoubuy.feature.reflection.usageLabel
import io.github.locngu.beforeyoubuy.ui.components.ButtonPair
import io.github.locngu.beforeyoubuy.ui.components.CountdownText
import io.github.locngu.beforeyoubuy.ui.components.EmptyState
import io.github.locngu.beforeyoubuy.ui.components.MoneyText
import io.github.locngu.beforeyoubuy.ui.components.PrimaryButton
import io.github.locngu.beforeyoubuy.ui.components.SecondaryButton
import io.github.locngu.beforeyoubuy.ui.components.SectionHeader
import io.github.locngu.beforeyoubuy.ui.format.currencySymbol
import io.github.locngu.beforeyoubuy.ui.format.currentLocale
import io.github.locngu.beforeyoubuy.ui.format.formattedDate
import java.time.LocalDate

/**
 * The decision screen for an item (brief §6.5, issue #18): the item, the user's reasons and what else the money could
 * do (issue #25), then "Do you still want to spend this money on this?" with Buy and "Don't buy — keep …" as equals (ADR-0012,
 * ADR-0028). [onDecided] closes the screen once a decision is stored; [onReflect] opens the reflection questions.
 */
@Composable
fun DecisionScreen(
    itemId: Long,
    onDecided: (DecisionOutcome) -> Unit,
    onReflect: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DecisionViewModel =
        hiltViewModel<DecisionViewModel, DecisionViewModel.Factory> { it.create(itemId) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val locale = currentLocale()
    LaunchedEffect(viewModel, locale) { viewModel.setLocale(locale) }
    val decided by rememberUpdatedState(onDecided)
    LaunchedEffect(viewModel) { viewModel.outcome.collect { decided(it) } }
    val snackbarHostState = remember { SnackbarHostState() }
    val resources by rememberUpdatedState(LocalResources.current)
    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.message.collect {
            val text = when (it) {
                DecisionMessage.CHANGED_MEANWHILE -> resources.getString(R.string.item_changed_meanwhile)
            }
            snackbarHostState.showSnackbar(text)
        }
    }
    // Back from a step returns to the overview; from the overview it leaves the screen.
    BackHandler(enabled = state.step != DecisionStep.OVERVIEW, onBack = viewModel::onBackToOverview)
    DecisionContent(
        state = state,
        actions = viewModel,
        onReflect = onReflect,
        snackbarHostState = snackbarHostState,
        modifier = modifier
    )
}

/** Stateless decision screen, for screenshots. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecisionContent(
    state: DecisionUiState,
    actions: DecisionActions,
    onReflect: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val overview = state.overview
    val inStep = overview != null && state.step != DecisionStep.OVERVIEW
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.decision_title)) },
                navigationIcon = {
                    if (inStep) {
                        IconButton(onClick = actions::onBackToOverview) {
                            Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.decision_back))
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        when {
            state.gone ->
                Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    EmptyState(title = stringResource(R.string.item_gone))
                }

            overview != null ->
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (state.step) {
                        DecisionStep.OVERVIEW -> Overview(overview, actions, onReflect, state.saving)
                        DecisionStep.BUY -> state.form?.let { BuyStep(state, overview, it, actions) }
                        DecisionStep.WAIT -> state.form?.let { WaitStep(state, it, actions) }
                        DecisionStep.REJECT -> state.form?.let { RejectStep(state, it, actions) }
                    }
                }
        }
    }
    state.confirmBelowZero?.let { BelowZeroDialog(it, actions) }
}

/** Buying for more than is set aside and not set aside asks first (issue #23); the purchase is the only record. */
@Composable
private fun BelowZeroDialog(belowZero: BelowZero, actions: DecisionActions) {
    val locale = currentLocale()
    AlertDialog(
        onDismissRequest = actions::dismissBelowZero,
        title = { Text(stringResource(R.string.fun_money_below_zero_title)) },
        text = {
            Text(
                stringResource(
                    R.string.decision_below_zero_body,
                    belowZero.paid.format(locale),
                    belowZero.leaves.format(locale)
                )
            )
        },
        confirmButton = {
            TextButton(onClick = actions::confirmBelowZero) { Text(stringResource(R.string.decision_buy_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = actions::dismissBelowZero) { Text(stringResource(R.string.dialog_cancel)) }
        }
    )
}

@Composable
private fun Overview(overview: DecisionOverview, actions: DecisionActions, onReflect: () -> Unit, saving: Boolean) {
    Header(overview)
    // The user's own reasons come before anything else (ADR-0013).
    Reasons(overview, onReflect)
    OpportunityCostSection(overview.opportunityCost)
    Text(
        stringResource(R.string.decision_question),
        modifier = Modifier.padding(horizontal = 16.dp).semantics { heading() },
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface
    )
    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val keep = keepLabel(overview)
        if (overview.buyPath == BuyPath.EARLY) {
            // Not Ready: no Buy. Not buying stays one tap; buying early is the quieter way, behind friction (ADR-0006).
            SecondaryButton(
                text = keep,
                onClick = actions::onReject,
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving
            )
            TextButton(onClick = actions::onBuy, enabled = !saving, modifier = Modifier.offset(x = (-12).dp)) {
                Text(stringResource(R.string.decision_early))
            }
        } else {
            // Equal weight: same look, same size (ADR-0028).
            ButtonPair(
                start = {
                    PrimaryButton(stringResource(R.string.decision_buy), actions::onBuy, it, enabled = !saving)
                },
                end = { SecondaryButton(keep, actions::onReject, it, enabled = !saving) }
            )
            OutlinedButton(
                onClick = actions::onWait,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                enabled = !saving
            ) { Text(stringResource(R.string.decision_wait)) }
        }
    }
}

/**
 * "Don't buy — keep 1,200 €", naming the priority the money stays for when the top opportunity cost line is about one
 * (research §1: describe the alternative concretely; ADR-0048).
 */
@Composable
private fun keepLabel(overview: DecisionOverview): String {
    val price = overview.item.price.format(currentLocale())
    val keepFor = overview.opportunityCost.keepFor()
        ?: return stringResource(R.string.decision_reject, price)
    return stringResource(R.string.decision_reject_for, price, keepFor.priority.name, keepFor.rank)
}

/** Name, price, how long it's wanted and where its cooling-off stands. */
@Composable
private fun Header(overview: DecisionOverview) {
    val item = overview.item
    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(item.name, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        MoneyText(item.price, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        SecondaryText(
            if (overview.wantedForDays == 0) {
                stringResource(R.string.decision_added_today)
            } else {
                pluralStringResource(R.plurals.decision_wanted_for_days, overview.wantedForDays, overview.wantedForDays)
            }
        )
        when {
            item.kind == ItemKind.NEED -> SecondaryText(stringResource(R.string.item_need))

            overview.stage == Stage.PARKED -> SecondaryText(stringResource(R.string.stage_not_priority))

            overview.stage == Stage.COOLING_OFF ->
                CountdownText(overview.daysLeft, style = MaterialTheme.typography.bodyMedium)

            else -> SecondaryText(stringResource(R.string.decision_cooling_done))
        }
        overview.recheckOn?.let { SecondaryText(stringResource(R.string.decision_recheck_on, formattedDate(it))) }
        // The amount saved (brief §6.5); nothing for a need or without fun money.
        overview.savings?.let { SavingsProgressBar(it, Modifier.fillMaxWidth()) }
    }
}

/** Why, how often, the reasons against and what it replaces: the answers that weigh most when deciding. */
@Composable
private fun Reasons(overview: DecisionOverview, onReflect: () -> Unit) {
    val reflection = overview.reflection
    val answers = buildList {
        reflection?.why?.takeIf { it.isNotBlank() }?.let { add(stringResource(R.string.item_reason_why) to it.trim()) }
        reflection?.usageFrequency?.let { add(stringResource(R.string.item_reason_usage) to usageLabel(it)) }
        reasonsAgainstText(overview)?.let { add(stringResource(R.string.item_reasons_against) to it) }
        reflection?.replaces?.takeIf { it.isNotBlank() }?.let {
            add(stringResource(R.string.item_reason_replaces) to it.trim())
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(stringResource(R.string.item_reasons_title))
        if (answers.isEmpty()) {
            SecondaryText(stringResource(R.string.decision_no_reasons), Modifier.padding(horizontal = 16.dp))
        }
        answers.forEach { (label, value) -> Answer(label, value) }
        if (!overview.reflected) {
            TextButton(onClick = onReflect, modifier = Modifier.padding(horizontal = 16.dp).offset(x = (-12).dp)) {
                Text(stringResource(R.string.decision_answer_questions))
            }
        }
    }
}

/** The reasons against as a bulleted list; null if there are none. */
private fun reasonsAgainstText(overview: DecisionOverview): String? = overview.reflection?.reasonsAgainst
    ?.filter { it.isNotBlank() }
    ?.takeIf { it.isNotEmpty() }
    ?.joinToString("\n") { "• ${it.trim()}" }

/**
 * The price paid and the date. Buying early (ADR-0006) first shows the days left and the reasons against again and
 * asks why now; "Buy anyway" stays disabled until there's a reason.
 */
@Composable
private fun BuyStep(state: DecisionUiState, overview: DecisionOverview, form: DecisionForm, actions: DecisionActions) {
    val early = state.buyPath == BuyPath.EARLY
    StepTitle(stringResource(if (early) R.string.decision_early_title else R.string.decision_buy_title))
    StepColumn {
        Text(overview.item.name, style = MaterialTheme.typography.titleMedium)
        if (early) {
            SecondaryText(
                when {
                    // Not a priority: its wait doesn't run, so there are no days left to show (ADR-0049).
                    overview.stage == Stage.PARKED -> stringResource(R.string.stage_not_priority)

                    overview.stage == Stage.COOLING_OFF ->
                        pluralStringResource(R.plurals.decision_early_days_left, overview.daysLeft, overview.daysLeft)

                    !overview.reflected -> stringResource(R.string.decision_early_wait_over)

                    overview.stage == Stage.SAVING -> stringResource(
                        R.string.decision_early_saving,
                        (overview.savings?.shownToGo ?: overview.item.price).format(currentLocale())
                    )

                    // Cooling off ended while this step was open; it stays an early purchase (ADR-0006).
                    else -> stringResource(R.string.decision_cooling_done)
                },
                style = MaterialTheme.typography.bodyLarge
            )
            Answer(
                stringResource(R.string.decision_early_reasons_against),
                reasonsAgainstText(overview) ?: stringResource(R.string.decision_early_no_reasons_against),
                indented = false
            )
            OutlinedTextField(
                value = form.earlyReason,
                onValueChange = actions::onEarlyReasonChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.decision_early_reason)) },
                supportingText = { Text(stringResource(R.string.decision_early_reason_help)) },
                minLines = 2,
                maxLines = 5,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )
        }
        val currency = overview.item.price.currency
        val priceError = state.purchase?.price?.let { priceErrorMessage(it, currency) }
        OutlinedTextField(
            value = form.price,
            onValueChange = actions::onPriceChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.decision_price_paid)) },
            suffix = { Text(currencySymbol(currency)) },
            isError = priceError != null,
            supportingText = priceError?.let { { Text(it) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        DateButton(
            label = stringResource(R.string.decision_bought_on, formattedDate(form.boughtOn)),
            initial = form.boughtOn,
            earliest = state.purchaseDates?.start,
            latest = state.purchaseDates?.endInclusive,
            onPicked = actions::onBoughtOnChange
        )
        PrimaryButton(
            text = stringResource(if (early) R.string.decision_early_confirm else R.string.decision_buy_confirm),
            onClick = actions::confirm,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.purchase?.isValid == true && !state.saving
        )
    }
}

/** 7, 14 or 30 days, or a picked date; the item isn't shown as Ready until then. */
@Composable
private fun WaitStep(state: DecisionUiState, form: DecisionForm, actions: DecisionActions) {
    val today = state.today ?: return
    StepTitle(stringResource(R.string.decision_wait_title))
    StepColumn {
        SecondaryText(stringResource(R.string.decision_wait_explained), style = MaterialTheme.typography.bodyLarge)
        val options = WAIT_DAYS.map { today.plusDays(it.toLong()) }
        var picking by rememberSaveable { mutableStateOf(false) }
        Column(modifier = Modifier.selectableGroup()) {
            WAIT_DAYS.zip(options).forEach { (days, date) ->
                Choice(
                    label = pluralStringResource(R.plurals.decision_wait_days, days, days),
                    detail = formattedDate(date),
                    selected = form.waitUntil == date,
                    onClick = { actions.onWaitUntilChange(date) }
                )
            }
            val picked = form.waitUntil.takeIf { it !in options }
            Choice(
                label = picked?.let { stringResource(R.string.decision_wait_on, formattedDate(it)) }
                    ?: stringResource(R.string.decision_wait_pick),
                detail = null,
                selected = picked != null,
                onClick = { picking = true }
            )
        }
        if (picking) {
            DayPicker(
                initial = form.waitUntil,
                earliest = state.earliestWait,
                onPicked = { date ->
                    date?.let(actions::onWaitUntilChange)
                    picking = false
                },
                onDismiss = { picking = false }
            )
        }
        PrimaryButton(
            text = stringResource(R.string.decision_wait_confirm, formattedDate(form.waitUntil)),
            onClick = actions::confirm,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.saving && state.earliestWait?.let { form.waitUntil >= it } == true
        )
    }
}

/** One confirm, with an optional "Why not?". The message afterwards is neutral (ADR-0012). */
@Composable
private fun RejectStep(state: DecisionUiState, form: DecisionForm, actions: DecisionActions) {
    StepTitle(stringResource(R.string.decision_reject_title))
    StepColumn {
        state.overview?.let { Text(it.item.name, style = MaterialTheme.typography.titleMedium) }
        SecondaryText(stringResource(R.string.decision_reject_explained), style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = form.note,
            onValueChange = actions::onNoteChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.decision_reject_note)) },
            minLines = 2,
            maxLines = 5,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )
        SecondaryButton(
            text = stringResource(R.string.decision_reject_confirm),
            onClick = actions::confirm,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.saving
        )
    }
}

@Composable
private fun StepTitle(text: String) {
    Text(
        text,
        modifier = Modifier.padding(horizontal = 16.dp).semantics { heading() },
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun StepColumn(content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        content()
    }
}

/** A button showing a date; it opens a picker for the days from [earliest] to [latest]. */
@Composable
private fun DateButton(
    label: String,
    initial: LocalDate,
    earliest: LocalDate?,
    latest: LocalDate?,
    onPicked: (LocalDate) -> Unit
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = { picking = true }) { Text(label) }
    if (picking) {
        DayPicker(
            initial = initial,
            earliest = earliest,
            latest = latest,
            onPicked = { date ->
                date?.let(onPicked)
                picking = false
            },
            onDismiss = { picking = false }
        )
    }
}

/** A radio row; the whole row is the touch target. */
@Composable
private fun Choice(label: String, detail: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            label,
            modifier = Modifier.weight(1f).padding(start = 16.dp),
            style = MaterialTheme.typography.bodyLarge
        )
        detail?.let { SecondaryText(it) }
    }
}

@Composable
private fun Answer(label: String, value: String, indented: Boolean = true) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = if (indented) 16.dp else 0.dp)) {
        SecondaryText(label, style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun SecondaryText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium
) {
    Text(text, modifier = modifier, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
