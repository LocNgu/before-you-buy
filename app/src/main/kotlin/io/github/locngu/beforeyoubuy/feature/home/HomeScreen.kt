package io.github.locngu.beforeyoubuy.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.locngu.beforeyoubuy.R
import io.github.locngu.beforeyoubuy.core.domain.home.HomeOverview
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.Stage
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.core.domain.pool.FunMoney
import io.github.locngu.beforeyoubuy.core.domain.wishlist.WishlistEntry
import io.github.locngu.beforeyoubuy.feature.capture.QuickAddButton
import io.github.locngu.beforeyoubuy.feature.wishlist.StageChip
import io.github.locngu.beforeyoubuy.feature.wishlist.StageStatus
import io.github.locngu.beforeyoubuy.ui.components.CountdownText
import io.github.locngu.beforeyoubuy.ui.components.MoneyText
import io.github.locngu.beforeyoubuy.ui.components.SectionHeader

/**
 * Home (brief §6.1): what can I decide on, what am I considering, what am I saving for (from #23) and how much fun
 * money there is for wants. [onOpenFunMoney] sets it up or changes it. Quick add is hosted by the navigation, so this only asks to open it with [onAdd].
 */
@Composable
fun HomeScreen(
    onAdd: () -> Unit,
    onOpenDecision: (ItemId) -> Unit,
    onOpenWishlist: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenFunMoney: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(
        state = state,
        onAdd = onAdd,
        onOpenDecision = onOpenDecision,
        onOpenWishlist = onOpenWishlist,
        onOpenHistory = onOpenHistory,
        onOpenFunMoney = onOpenFunMoney,
        snackbarHostState = snackbarHostState,
        modifier = modifier
    )
}

/**
 * Stateless Home, for screenshots. The four questions in order; "To decide" only when there is something. Nothing
 * about decided-against items here: this screen shows Ready items (moral licensing, ADR-0012).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    state: HomeUiState,
    onAdd: () -> Unit,
    onOpenDecision: (ItemId) -> Unit,
    onOpenWishlist: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenFunMoney: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = { TextButton(onClick = onOpenHistory) { Text(stringResource(R.string.history_open)) } }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = { QuickAddButton(onClick = onAdd) }
    ) { innerPadding ->
        val overview = state.overview ?: return@Scaffold
        HomeSections(
            overview = overview,
            money = state.money,
            onOpenDecision = onOpenDecision,
            onOpenWishlist = onOpenWishlist,
            onOpenFunMoney = onOpenFunMoney,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

@Composable
private fun HomeSections(
    overview: HomeOverview,
    money: FunMoney?,
    onOpenDecision: (ItemId) -> Unit,
    onOpenWishlist: () -> Unit,
    onOpenFunMoney: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Leaves room for the floating button, so the last section isn't hidden behind it.
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        if (overview.toDecide.isNotEmpty()) {
            item(key = "decide") { SectionHeader(stringResource(R.string.home_decide_title)) }
            items(overview.toDecide, key = { it.item.id.value }) { entry ->
                DecideRow(entry, onClick = { onOpenDecision(entry.item.id) })
            }
        }
        item(key = "considering") {
            Considering(overview.activeCount, overview.nextCoolingEnd, onOpenWishlist)
        }
        // Placeholder until savings per item exist (#23): no amounts, nothing to tap.
        item(key = "saving") {
            ComingSoon(stringResource(R.string.home_saving_title), stringResource(R.string.home_saving_soon))
        }
        if (money != null) item(key = "money") { FunMoneySection(money, onOpenFunMoney) }
    }
}

/**
 * An item that can be decided on; tapping opens the decision screen, which offers the questions if answers are
 * missing.
 */
@Composable
private fun DecideRow(entry: WishlistEntry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = entry.item.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StageChip(entry.stage)
                // "Ready to decide" would only repeat the chip and the section title.
                if (entry.stage != Stage.READY) StageStatus(entry.stage, entry.daysLeft)
            }
        }
        MoneyText(
            money = entry.item.price,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** How many items are on the wishlist, whose waiting period ends next, and the way to the wishlist. */
@Composable
private fun Considering(activeCount: Int, next: WishlistEntry?, onOpenWishlist: () -> Unit) {
    Column {
        SectionHeader(stringResource(R.string.home_considering_title))
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = if (activeCount == 0) {
                    stringResource(R.string.home_considering_empty)
                } else {
                    pluralStringResource(R.plurals.home_considering_count, activeCount, activeCount)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (next != null) {
                Text(
                    text = stringResource(R.string.home_next_cooling_end),
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = next.item.name,
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    CountdownText(next.daysLeft, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        // Lines the label up with the text above; the button's own padding stays part of the touch target.
        TextButton(onClick = onOpenWishlist, modifier = Modifier.padding(horizontal = 16.dp).offset(x = (-12).dp)) {
            Text(stringResource(R.string.wishlist_open))
        }
    }
}

/**
 * "Fun money": what's in the pool, set aside and not yet set aside (ADR-0045), or what it is and a way to set it up.
 * Plain amounts, never "available to spend" (ADR-0014); the labels say they're for wants.
 */
@Composable
private fun FunMoneySection(money: FunMoney, onOpenFunMoney: () -> Unit) {
    Column {
        SectionHeader(stringResource(R.string.home_money_title))
        when (money) {
            FunMoney.NotSetUp ->
                Text(
                    text = stringResource(R.string.home_money_not_set_up),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

            is FunMoney.SetUp ->
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AmountRow(stringResource(R.string.home_money_pool), money.balance.pool)
                    AmountRow(stringResource(R.string.home_money_committed), money.balance.committed)
                    AmountRow(stringResource(R.string.home_money_unallocated), money.balance.unallocated)
                }
        }
        // Lined up with the text above, like "Open wishlist".
        TextButton(onClick = onOpenFunMoney, modifier = Modifier.padding(horizontal = 16.dp).offset(x = (-12).dp)) {
            Text(
                stringResource(
                    if (money == FunMoney.NotSetUp) R.string.home_money_set_up else R.string.home_money_manage
                )
            )
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

/** A section a later issue fills in. Quiet, with no amount, so it can't read as money to spend (ADR-0014). */
@Composable
private fun ComingSoon(title: String, body: String) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        SectionHeader(title)
        Text(
            text = body,
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
