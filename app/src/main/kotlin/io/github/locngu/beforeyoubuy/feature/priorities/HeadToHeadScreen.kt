package io.github.locngu.beforeyoubuy.feature.priorities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.locngu.beforeyoubuy.R
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.priorities.OutcomeLine
import io.github.locngu.beforeyoubuy.feature.money.SavingsProgressBar
import io.github.locngu.beforeyoubuy.feature.reflection.usageLabel
import io.github.locngu.beforeyoubuy.ui.components.ButtonPair
import io.github.locngu.beforeyoubuy.ui.components.EmptyState
import io.github.locngu.beforeyoubuy.ui.components.MoneyText
import io.github.locngu.beforeyoubuy.ui.components.PrimaryButton
import io.github.locngu.beforeyoubuy.ui.components.SecondaryButton

/**
 * "Earn its place" (brief §4.7, ADR-0011): the challenger [itemId] next to one priority at a time, then what changed.
 * [onDone] closes it; back leaves at any point without changing anything.
 */
@Composable
fun HeadToHeadScreen(
    itemId: Long,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HeadToHeadViewModel =
        hiltViewModel<HeadToHeadViewModel, HeadToHeadViewModel.Factory> { it.create(itemId) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HeadToHeadContent(state = state, onChoose = viewModel::choose, onDone = onDone, modifier = modifier)
}

/** Stateless head-to-head, for screenshots. [onChoose] gets the winner and the priority it was compared with. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeadToHeadContent(
    state: HeadToHeadUiState,
    onChoose: (winner: ItemId, opponent: ItemId) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.head_to_head_title)) }) }
    ) { innerPadding ->
        val padded = Modifier.fillMaxSize().padding(innerPadding)
        val comparison = state.comparison
        val summary = state.summary
        when {
            comparison != null -> ComparisonView(comparison, onChoose, padded)

            summary != null -> Summary(summary, onDone, padded)

            state.gone || state.changedMeanwhile ->
                Box(padded, contentAlignment = Alignment.Center) {
                    EmptyState(
                        title = stringResource(
                            if (state.gone) R.string.head_to_head_gone else R.string.priority_changed_meanwhile
                        ),
                        action = { PrimaryButton(stringResource(R.string.head_to_head_done), onDone) }
                    )
                }
        }
    }
}

/** One comparison: the question, both cards side by side, and two equal buttons named after them. */
@Composable
private fun ComparisonView(
    comparison: Comparison,
    onChoose: (winner: ItemId, opponent: ItemId) -> Unit,
    modifier: Modifier = Modifier
) {
    val challenger = comparison.challenger.item
    val opponent = comparison.opponent.item
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.head_to_head_question),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(comparison.challenger, Modifier.weight(1f).fillMaxHeight())
            Card(comparison.opponent, Modifier.weight(1f).fillMaxHeight())
        }
        // Neither side is louder (ADR-0012): the same button, only the name differs.
        ButtonPair(
            start = { PrimaryButton(challenger.name, { onChoose(challenger.id, opponent.id) }, it) },
            end = { SecondaryButton(opponent.name, { onChoose(opponent.id, opponent.id) }, it) }
        )
    }
}

/** One side: the reasons first, then price and savings; no photo (ADR-0013). */
@Composable
private fun Card(card: ComparisonCard, modifier: Modifier = Modifier) {
    OutlinedCard(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = card.rank?.let { stringResource(R.string.priority_item_rank, it) }
                    ?: stringResource(R.string.head_to_head_not_priority),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                card.item.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            val reflection = card.reflection
            Reason(stringResource(R.string.item_reason_why), reflection?.why?.trim()?.ifEmpty { null })
            Reason(
                stringResource(R.string.item_reason_usage),
                reflection?.usageFrequency?.let { usageLabel(it) }
            )
            val against = reflection?.reasonsAgainst.orEmpty().filter { it.isNotBlank() }
            Reason(
                stringResource(R.string.item_reasons_against),
                against.takeIf { it.isNotEmpty() }?.joinToString("\n") { "• ${it.trim()}" }
            )
            MoneyText(
                card.item.price,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            card.savings?.let { SavingsProgressBar(it, Modifier.fillMaxWidth()) }
        }
    }
}

/** A label and the answer, or "No answer yet". */
@Composable
private fun Reason(label: String, value: String?) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value ?: stringResource(R.string.head_to_head_no_answer),
            style = MaterialTheme.typography.bodyMedium,
            color = if (value == null) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

/** What changed: "Camera stays #1", "3D printer is now #2", "Bike moved back to the wishlist". */
@Composable
private fun Summary(lines: List<OutcomeLine>, onDone: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.head_to_head_summary_title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        lines.forEach { line ->
            Text(
                outcomeText(line),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        PrimaryButton(stringResource(R.string.head_to_head_done), onDone, Modifier.fillMaxWidth().padding(top = 8.dp))
    }
}

/** One summary line (see [OutcomeLine]). */
@Composable
internal fun outcomeText(line: OutcomeLine): String {
    val name = line.item.name
    val after = line.after
    return when {
        after == null && line.before == null -> stringResource(R.string.priority_outcome_stays_on_wishlist, name)
        after == null -> stringResource(R.string.priority_outcome_back, name)
        after == line.before -> stringResource(R.string.priority_outcome_stays, name, after)
        else -> stringResource(R.string.priority_outcome_now, name, after)
    }
}
