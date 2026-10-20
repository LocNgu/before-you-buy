package io.github.locngu.beforeyoubuy.feature.reflection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.locngu.beforeyoubuy.R
import io.github.locngu.beforeyoubuy.core.domain.item.UpgradeOrNew
import io.github.locngu.beforeyoubuy.core.domain.item.UsageFrequency
import io.github.locngu.beforeyoubuy.core.domain.reflection.Onward
import io.github.locngu.beforeyoubuy.core.domain.reflection.ReflectionForm
import io.github.locngu.beforeyoubuy.core.domain.reflection.ReflectionPage
import io.github.locngu.beforeyoubuy.core.domain.reflection.ReflectionStep
import io.github.locngu.beforeyoubuy.ui.components.EmptyState

/**
 * The reflection questions for an item, one per screen (brief §6.4): why, how often, reasons against, then the
 * optional ones. Every answer is kept as it's given (ADR-0039); [onDone] closes the flow after the last question or
 * "Close", and back leaves it too.
 */
@Composable
fun ReflectionScreen(
    itemId: Long,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReflectionViewModel =
        hiltViewModel<ReflectionViewModel, ReflectionViewModel.Factory> { it.create(itemId) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val done by rememberUpdatedState(onDone)
    LaunchedEffect(viewModel) { viewModel.finished.collect { done() } }
    ReflectionContent(state = state, actions = viewModel, modifier = modifier)
}

/** Stateless reflection flow, for screenshots. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReflectionContent(state: ReflectionUiState, actions: ReflectionActions, modifier: Modifier = Modifier) {
    val form = state.form
    val page = state.page
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { state.itemName?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                navigationIcon = {
                    IconButton(onClick = actions::close) {
                        Icon(painterResource(R.drawable.ic_close), stringResource(R.string.reflection_close))
                    }
                }
            )
        },
        bottomBar = { if (page != null && !state.gone) StepButtons(page, actions) }
    ) { innerPadding ->
        when {
            state.gone ->
                Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    EmptyState(title = stringResource(R.string.item_gone))
                }

            form != null && page != null ->
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Progress(page)
                    Question(page.step)
                    Answer(page.step, form, actions)
                }
        }
    }
}

/** "Question 2 of 8", a bar, and whether the question is required or optional. */
@Composable
private fun Progress(page: ReflectionPage) {
    val step = page.step
    val count = ReflectionStep.entries.size
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row {
            Text(
                stringResource(R.string.reflection_progress, step.ordinal + 1, count),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(if (page.required) R.string.reflection_required else R.string.reflection_optional),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LinearProgressIndicator(progress = { (step.ordinal + 1f) / count }, modifier = Modifier.fillMaxWidth())
    }
}

/** The question and a gentle hint, never a judgement (copy-and-tone.md). */
@Composable
private fun Question(step: ReflectionStep) {
    val (title, help) = when (step) {
        ReflectionStep.WHY -> R.string.reflection_why_title to R.string.reflection_why_help

        ReflectionStep.USAGE -> R.string.reflection_usage_title to R.string.reflection_usage_help

        ReflectionStep.REASONS_AGAINST ->
            R.string.reflection_reasons_against_title to R.string.reflection_reasons_against_help

        ReflectionStep.PROBLEM_SOLVED ->
            R.string.reflection_problem_solved_title to R.string.reflection_problem_solved_help

        ReflectionStep.CURRENT_ALTERNATIVE ->
            R.string.reflection_current_alternative_title to R.string.reflection_current_alternative_help

        ReflectionStep.REPLACES -> R.string.reflection_replaces_title to R.string.reflection_replaces_help

        ReflectionStep.WHY_NOW -> R.string.reflection_why_now_title to R.string.reflection_why_now_help

        ReflectionStep.UPGRADE_OR_NEW ->
            R.string.reflection_upgrade_or_new_title to R.string.reflection_upgrade_or_new_help
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            stringResource(help),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Answer(step: ReflectionStep, form: ReflectionForm, actions: ReflectionActions) {
    when (step) {
        ReflectionStep.WHY -> TextAnswer(form.why, actions::onWhyChange)

        ReflectionStep.USAGE -> Choices(
            options = UsageFrequency.entries,
            selected = form.usageFrequency,
            label = { usageLabel(it) },
            // Required: another choice replaces it, but it can't be cleared.
            onSelect = actions::onUsageChange
        )

        ReflectionStep.REASONS_AGAINST -> ReasonsAgainst(form, actions)

        ReflectionStep.PROBLEM_SOLVED -> TextAnswer(form.problemSolved, actions::onProblemSolvedChange)

        ReflectionStep.CURRENT_ALTERNATIVE -> TextAnswer(form.currentAlternative, actions::onCurrentAlternativeChange)

        ReflectionStep.REPLACES -> TextAnswer(form.replaces, actions::onReplacesChange)

        ReflectionStep.WHY_NOW -> TextAnswer(form.whyNow, actions::onWhyNowChange)

        ReflectionStep.UPGRADE_OR_NEW -> Choices(
            options = UpgradeOrNew.entries,
            selected = form.upgradeOrNew,
            label = { upgradeOrNewLabel(it) },
            // Optional: tapping the chosen answer again clears it.
            onSelect = actions::onUpgradeOrNewTap
        )
    }
}

@Composable
private fun TextAnswer(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.reflection_answer)) },
        minLines = 3,
        maxLines = 8,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
    )
}

/** One radio button per option; the whole row is the touch target. */
@Composable
private fun <T> Choices(options: List<T>, selected: T?, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    Column(modifier = Modifier.selectableGroup()) {
        options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .selectable(selected = option == selected, role = Role.RadioButton, onClick = { onSelect(option) }),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // The row handles the tap, so it's one target with one announcement.
                RadioButton(selected = option == selected, onClick = null)
                Text(label(option), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/**
 * A field per reason; another can be added once each holds one. A new field gets the cursor; removing one clears
 * it, since the fields after it move up and the cursor would land in another reason.
 */
@Composable
private fun ReasonsAgainst(form: ReflectionForm, actions: ReflectionActions) {
    val reasons = form.reasonsAgainst
    val focusManager = LocalFocusManager.current
    val newField = remember { FocusRequester() }
    var added by remember { mutableStateOf(false) }
    LaunchedEffect(reasons.size) {
        if (added) newField.requestFocus()
        added = false
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        reasons.forEachIndexed { index, reason ->
            val number = index + 1
            OutlinedTextField(
                value = reason,
                onValueChange = { actions.onReasonChange(index, it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (index == reasons.lastIndex) Modifier.focusRequester(newField) else Modifier),
                label = { Text(stringResource(R.string.reflection_reason, number)) },
                trailingIcon = if (reasons.size > 1) {
                    {
                        IconButton(
                            onClick = {
                                focusManager.clearFocus()
                                actions.onRemoveReason(index)
                            }
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_close),
                                stringResource(R.string.reflection_remove_reason, number)
                            )
                        }
                    }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )
        }
        // Lines the label up with the fields; the button's own padding stays part of the touch target.
        TextButton(
            onClick = {
                added = true
                actions.onAddReason()
            },
            enabled = form.canAddReason,
            modifier = Modifier.offset(x = (-12).dp)
        ) { Text(stringResource(R.string.reflection_add_reason)) }
    }
}

/**
 * Back to the previous question, and on: "Next" once a required question is answered, "Skip" for an unanswered
 * optional one, "Done" on the last. Above the keyboard while it's open.
 */
@Composable
private fun StepButtons(page: ReflectionPage, actions: ReflectionActions) {
    val onLabel = when (page.onward) {
        Onward.NEXT -> R.string.reflection_next
        Onward.SKIP -> R.string.reflection_skip
        Onward.DONE -> R.string.reflection_done
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (page.step.previous != null) {
            TextButton(onClick = actions::back) { Text(stringResource(R.string.reflection_back)) }
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = actions::next, enabled = page.canGoOn) { Text(stringResource(onLabel)) }
    }
}
