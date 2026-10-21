package io.github.locngu.beforeyoubuy.feature.history

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.locngu.beforeyoubuy.R
import io.github.locngu.beforeyoubuy.core.domain.history.BoughtEntry
import io.github.locngu.beforeyoubuy.core.domain.history.DecidedAgainst
import io.github.locngu.beforeyoubuy.core.domain.history.DecidedAgainstEntry
import io.github.locngu.beforeyoubuy.core.domain.history.HistoryPeriod
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.money.format
import io.github.locngu.beforeyoubuy.ui.components.EmptyState
import io.github.locngu.beforeyoubuy.ui.components.MoneyText
import io.github.locngu.beforeyoubuy.ui.format.currentLocale
import io.github.locngu.beforeyoubuy.ui.format.formattedDate

/** History (brief §6.6): "Bought" and "Decided against". Tapping an entry opens it with [onOpenItem]. */
@Composable
fun HistoryScreen(
    onOpenItem: (ItemId) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryContent(
        state = state,
        onSelectTab = viewModel::selectTab,
        onSelectPeriod = viewModel::selectPeriod,
        onOpenItem = onOpenItem,
        modifier = modifier
    )
}

/**
 * Stateless history, for screenshots. The "Decided against" total is plain text at the top of its tab: no badges,
 * streaks or celebration, and never next to anything ready to buy (ADR-0012).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryContent(
    state: HistoryUiState,
    onSelectTab: (HistoryTab) -> Unit,
    onSelectPeriod: (HistoryPeriod) -> Unit,
    onOpenItem: (ItemId) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.history_title)) }) }
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            PrimaryTabRow(selectedTabIndex = state.tab.ordinal) {
                HistoryTab.entries.forEach { tab ->
                    Tab(
                        selected = state.tab == tab,
                        onClick = { onSelectTab(tab) },
                        text = { Text(stringResource(tabLabel(tab))) }
                    )
                }
            }
            PeriodFilter(state.period, onSelectPeriod)
            when (state.tab) {
                HistoryTab.BOUGHT -> state.bought?.let { BoughtList(it, state.period, onOpenItem) }

                HistoryTab.DECIDED_AGAINST -> state.decidedAgainst?.let {
                    DecidedAgainstList(it, state.period, onOpenItem)
                }
            }
        }
    }
}

@StringRes
private fun tabLabel(tab: HistoryTab): Int = when (tab) {
    HistoryTab.BOUGHT -> R.string.history_tab_bought
    HistoryTab.DECIDED_AGAINST -> R.string.history_tab_decided_against
}

/** All, this month, this year: one choice, for both tabs. */
@Composable
private fun PeriodFilter(selected: HistoryPeriod, onSelect: (HistoryPeriod) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(HistoryPeriod.ALL, HistoryPeriod.THIS_MONTH, HistoryPeriod.THIS_YEAR).forEach { period ->
            FilterChip(
                selected = period == selected,
                onClick = { onSelect(period) },
                label = { Text(stringResource(periodLabel(period))) }
            )
        }
    }
}

@StringRes
private fun periodLabel(period: HistoryPeriod): Int = when (period) {
    HistoryPeriod.THIS_MONTH -> R.string.history_period_this_month
    HistoryPeriod.THIS_YEAR -> R.string.history_period_this_year
    HistoryPeriod.ALL -> R.string.history_period_all
}

@Composable
private fun BoughtList(entries: List<BoughtEntry>, period: HistoryPeriod, onOpenItem: (ItemId) -> Unit) {
    if (entries.isEmpty()) {
        Empty(
            title = when (period) {
                HistoryPeriod.THIS_MONTH -> R.string.history_bought_empty_month
                HistoryPeriod.THIS_YEAR -> R.string.history_bought_empty_year
                HistoryPeriod.ALL -> R.string.history_bought_empty
            },
            body = R.string.history_bought_empty_body.takeIf { period == HistoryPeriod.ALL }
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        items(entries, key = { it.item.id.value }) { entry ->
            BoughtRow(entry, onClick = { onOpenItem(entry.item.id) })
        }
    }
}

@Composable
private fun DecidedAgainstList(decided: DecidedAgainst, period: HistoryPeriod, onOpenItem: (ItemId) -> Unit) {
    if (decided.entries.isEmpty()) {
        Empty(
            title = when (period) {
                HistoryPeriod.THIS_MONTH -> R.string.history_decided_against_empty_month
                HistoryPeriod.THIS_YEAR -> R.string.history_decided_against_empty_year
                HistoryPeriod.ALL -> R.string.history_decided_against_empty
            },
            body = R.string.history_decided_against_empty_body.takeIf { period == HistoryPeriod.ALL }
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "total") { Total(decided, period) }
        items(decided.entries, key = { it.item.id.value }) { entry ->
            DecidedAgainstRow(entry, onClick = { onOpenItem(entry.item.id) })
        }
    }
}

/** "You decided against 3,420 € of purchases": the exact sum, rounded once (ADR-0034), stated plainly (ADR-0012). */
@Composable
private fun Total(decided: DecidedAgainst, period: HistoryPeriod) {
    val amount = decided.total.format(currentLocale())
    val text = when (period) {
        HistoryPeriod.THIS_MONTH -> R.string.history_decided_against_total_month
        HistoryPeriod.THIS_YEAR -> R.string.history_decided_against_total_year
        HistoryPeriod.ALL -> R.string.history_decided_against_total
    }
    Text(
        text = stringResource(text, amount),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun Empty(@StringRes title: Int, @StringRes body: Int?) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(title = stringResource(title), body = body?.let { stringResource(it) })
    }
}

@Composable
private fun BoughtRow(entry: BoughtEntry, onClick: () -> Unit) {
    HistoryRow(name = entry.item.name, amount = { MoneyText(entry.paid, style = it) }, onClick = onClick) {
        Secondary(stringResource(R.string.history_bought_on, formattedDate(entry.boughtOn)))
        Secondary(consideredFor(entry.consideredForDays))
        entry.wishlistPrice?.let {
            Secondary(stringResource(R.string.history_wishlist_price, it.format(currentLocale())))
        }
        Flags(entry)
    }
}

@Composable
private fun DecidedAgainstRow(entry: DecidedAgainstEntry, onClick: () -> Unit) {
    HistoryRow(name = entry.item.name, amount = { MoneyText(entry.price, style = it) }, onClick = onClick) {
        Secondary(stringResource(R.string.history_decided_on, formattedDate(entry.decidedOn)))
        entry.note?.let { Secondary(it, maxLines = 2) }
    }
}

/** A row like the wishlist's: name and details on the left, the amount on the right; tapping opens the entry. */
@Composable
private fun HistoryRow(
    name: String,
    amount: @Composable (TextStyle) -> Unit,
    onClick: () -> Unit,
    details: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            details()
        }
        amount(MaterialTheme.typography.titleMedium)
    }
}

/** "Considered for 47 days", or that it was bought the day it was added. */
@Composable
internal fun consideredFor(days: Int): String = if (days == 0) {
    stringResource(R.string.history_considered_same_day)
} else {
    pluralStringResource(R.plurals.history_considered_for_days, days, days)
}

/** "Bought early" and "Need": facts about the purchase, shown like the wishlist's stage chips, never as a warning. */
@Composable
internal fun Flags(entry: BoughtEntry) {
    if (!entry.early && !entry.need) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (entry.early) Flag(stringResource(R.string.history_flag_early))
        if (entry.need) Flag(stringResource(R.string.history_flag_need))
    }
}

@Composable
private fun Flag(text: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun Secondary(text: String, maxLines: Int = Int.MAX_VALUE) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}
