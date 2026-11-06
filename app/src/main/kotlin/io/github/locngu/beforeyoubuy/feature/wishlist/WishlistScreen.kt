package io.github.locngu.beforeyoubuy.feature.wishlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.locngu.beforeyoubuy.R
import io.github.locngu.beforeyoubuy.core.domain.coolingoff.WaitProgress
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.Stage
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.core.domain.money.format
import io.github.locngu.beforeyoubuy.core.domain.priorities.canBePriority
import io.github.locngu.beforeyoubuy.core.domain.wishlist.Wishlist
import io.github.locngu.beforeyoubuy.core.domain.wishlist.WishlistEntry
import io.github.locngu.beforeyoubuy.feature.capture.QuickAddButton
import io.github.locngu.beforeyoubuy.feature.money.savingsText
import io.github.locngu.beforeyoubuy.feature.priorities.PriorityEvent
import io.github.locngu.beforeyoubuy.feature.priorities.PrioritySlotsDialog
import io.github.locngu.beforeyoubuy.feature.priorities.priorityMessage
import io.github.locngu.beforeyoubuy.ui.components.CountdownText
import io.github.locngu.beforeyoubuy.ui.components.EmptyState
import io.github.locngu.beforeyoubuy.ui.components.MoneyText
import io.github.locngu.beforeyoubuy.ui.components.SectionHeader
import io.github.locngu.beforeyoubuy.ui.format.currentLocale
import io.github.locngu.beforeyoubuy.ui.format.formattedDate
import java.io.File
import java.time.LocalDate

/**
 * The wishlist (brief §6.3). Quick add is hosted by the navigation, so this only asks to open it with [onAdd]
 * and shows messages in [snackbarHostState].
 */
@Composable
fun WishlistScreen(
    onAdd: () -> Unit,
    onOpenItem: (ItemId) -> Unit,
    onCompare: (ItemId) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: WishlistViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val resources by rememberUpdatedState(LocalResources.current)
    val compare by rememberUpdatedState(onCompare)
    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.priorityEvents.collect { event ->
            if (event is PriorityEvent.Compare) {
                compare(event.itemId)
            } else {
                priorityMessage(event, resources)?.let { snackbarHostState.showSnackbar(it) }
            }
        }
    }
    WishlistContent(
        state = state,
        onAdd = onAdd,
        onOpenItem = onOpenItem,
        onMakePriority = viewModel::makePriority,
        onChangePrioritySlots = viewModel::changePrioritySlots,
        snackbarHostState = snackbarHostState,
        modifier = modifier
    )
}

/**
 * Stateless wishlist, for screenshots (ADR-0049). The priorities come first by rank, each with its stage, with the
 * slots and a way to change them; then the needs; then every want without a slot under "Wishlist", whose row menu
 * offers "Make it a priority".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistContent(
    state: WishlistUiState,
    onAdd: () -> Unit,
    onOpenItem: (ItemId) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    onMakePriority: (ItemId) -> Unit = {},
    onChangePrioritySlots: (Int) -> Unit = {}
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.wishlist_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = { QuickAddButton(onClick = onAdd) }
    ) { innerPadding ->
        val wishlist = state.wishlist
        when {
            wishlist == null -> Unit

            wishlist.isEmpty() ->
                Column(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    EmptyState(
                        title = stringResource(R.string.wishlist_empty_title),
                        body = stringResource(R.string.wishlist_empty_body)
                    )
                }

            else -> WishlistList(
                state,
                wishlist,
                onOpenItem,
                onMakePriority,
                onChangePrioritySlots,
                Modifier.padding(innerPadding)
            )
        }
    }
}

@Composable
private fun WishlistList(
    state: WishlistUiState,
    wishlist: Wishlist,
    onOpenItem: (ItemId) -> Unit,
    onMakePriority: (ItemId) -> Unit,
    onChangePrioritySlots: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var changingSlots by rememberSaveable { mutableStateOf(false) }
    // Leaves room for the floating button, so the last row isn't hidden behind it.
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        // Always shown: every new want starts on the wishlist, and this is where its wait can start (ADR-0049).
        item(key = "header-priorities") {
            PrioritiesHeader(wishlist.priorities.size, state.prioritySlots, onChange = { changingSlots = true })
        }
        items(wishlist.priorities, key = { it.entry.item.id.value }) { priority ->
            WishlistRow(
                priority.entry,
                state.photos[priority.entry.item.id],
                onClick = { onOpenItem(priority.entry.item.id) },
                rank = priority.rank
            )
        }
        if (wishlist.needs.isNotEmpty()) {
            item(key = "header-needs") { SectionHeader(stringResource(R.string.wishlist_needs)) }
            items(wishlist.needs, key = { it.item.id.value }) { entry ->
                WishlistRow(entry, state.photos[entry.item.id], onClick = { onOpenItem(entry.item.id) })
            }
        }
        if (wishlist.parked.isNotEmpty()) {
            item(key = "header-parked") { SectionHeader(stringResource(R.string.wishlist_title)) }
            items(wishlist.parked, key = { it.item.id.value }) { entry ->
                val id = entry.item.id
                WishlistRow(
                    entry,
                    state.photos[id],
                    onClick = { onOpenItem(id) },
                    onMakePriority = if (entry.item.canBePriority) ({ onMakePriority(id) }) else null
                )
            }
        }
    }
    if (changingSlots) {
        PrioritySlotsDialog(
            current = state.prioritySlots,
            active = state.active,
            onSave = {
                changingSlots = false
                onChangePrioritySlots(it)
            },
            onDismiss = { changingSlots = false }
        )
    }
}

/**
 * "Priorities", how many slots are used (brief §4.7) and "Change", which opens the slots dialog. Without priorities, a
 * line says what one is for (ADR-0049).
 */
@Composable
private fun PrioritiesHeader(used: Int, slots: Int, onChange: () -> Unit) {
    Column {
        SectionHeader(stringResource(R.string.priorities_title))
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = pluralStringResource(R.plurals.priority_slots_used, slots, used, slots),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onChange) { Text(stringResource(R.string.priority_slots_change)) }
        }
        if (used == 0) {
            Text(
                text = stringResource(R.string.priorities_empty_hint),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * One item: thumbnail, name, stage and status, price. A priority shows its [rank] before the name. A want without a
 * slot has no stage and no rank, only how much of its wait is done, and a menu with "Make it a priority"
 * ([onMakePriority]).
 */
@Composable
private fun WishlistRow(
    entry: WishlistEntry,
    photo: File?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    rank: Int? = null,
    onMakePriority: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Small and only if there is one: the name and the reasons matter more than the picture (ADR-0013).
        // Decorative for screen readers; the name follows.
        val thumbnail = photo?.let { rememberPhoto(it, THUMBNAIL_SIZE_PX) }
        if (thumbnail != null) {
            ItemPhoto(
                bitmap = thumbnail,
                contentDescription = null,
                modifier = Modifier.size(40.dp).clip(MaterialTheme.shapes.small)
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (rank != null) PriorityRank(rank)
                Text(
                    text = entry.item.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // A parked want has no stage: it's under "Wishlist", and nothing runs for it (ADR-0049).
                if (entry.stage != Stage.PARKED) StageChip(entry.stage, waiting = entry.recheckOn != null)
                StageStatus(entry.stage, entry.daysLeft, entry.recheckOn, entry.savings?.shownToGo, entry.waitProgress)
            }
            // How far the savings are, once something is set aside; detail has the amounts (brief §4.6).
            entry.savings?.takeIf { it.allocated.minorUnits > 0 }?.let { savings ->
                val description = savingsText(savings)
                LinearProgressIndicator(
                    progress = { savings.fraction },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).semantics { stateDescription = description }
                )
            }
        }
        MoneyText(
            money = entry.item.price,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (onMakePriority != null) RowMenu(entry.item.name, onMakePriority)
    }
}

/** A priority's rank, "#1", before its name. Quiet: ranks order the list, they don't rate the item. */
@Composable
internal fun PriorityRank(rank: Int) {
    Text(
        text = stringResource(R.string.priority_rank, rank),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
}

/** A row's ⋮ menu: "Make it a priority" (brief §4.7). */
@Composable
private fun RowMenu(name: String, onMakePriority: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painterResource(R.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.priority_row_options, name)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.priority_make)) },
                onClick = {
                    expanded = false
                    onMakePriority()
                }
            )
        }
    }
}

/**
 * A countdown while cooling off, otherwise what the item is waiting for: after "Wait", the date to look again
 * ([recheckOn]); while saving, what is still to set aside ([toGo]); for a parked want, how much of its wait is done
 * ([waitProgress]), else that it isn't a priority (ADR-0049). Informational, never urgent.
 */
@Composable
internal fun StageStatus(
    stage: Stage,
    daysLeft: Int,
    recheckOn: LocalDate? = null,
    toGo: Money? = null,
    waitProgress: WaitProgress? = null
) {
    val style = MaterialTheme.typography.bodyMedium
    if (recheckOn != null) {
        Text(
            text = stringResource(R.string.wishlist_look_again_on, formattedDate(recheckOn)),
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    when (stage) {
        Stage.PARKED ->
            Text(
                text = waitProgress?.let { waitProgressText(it) } ?: stringResource(R.string.stage_not_priority),
                style = style,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

        Stage.COOLING_OFF -> CountdownText(daysLeft, style = style)

        Stage.NEEDS_REFLECTION -> CountdownText(0, style = style)

        Stage.SAVING ->
            if (toGo != null) {
                Text(
                    text = stringResource(R.string.savings_to_go, toGo.format(currentLocale())),
                    style = style,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

        Stage.READY ->
            Text(
                text = stringResource(R.string.wishlist_ready_to_decide),
                style = style,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
    }
}

/** The stage, or "Looking again later" for a Ready item after "Wait" until its date ([waiting], ADR-0049). */
@Composable
internal fun StageChip(stage: Stage, waiting: Boolean = false) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            text = if (waiting) stringResource(R.string.wishlist_waiting) else stageLabel(stage),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
internal fun stageLabel(stage: Stage): String = stringResource(
    when (stage) {
        // What the screen calls the wants without a slot (ADR-0049).
        Stage.PARKED -> R.string.wishlist_title

        Stage.COOLING_OFF -> R.string.stage_cooling_off

        Stage.NEEDS_REFLECTION -> R.string.stage_needs_reflection

        Stage.SAVING -> R.string.stage_saving

        Stage.READY -> R.string.stage_ready
    }
)

/** "Waiting period not started (30 days)", "12 of 30 days waited" or "Waiting period done" (ADR-0049). */
@Composable
internal fun waitProgressText(progress: WaitProgress): String = when (progress) {
    is WaitProgress.NotStarted ->
        pluralStringResource(R.plurals.wait_progress_not_started, progress.days, progress.days)

    is WaitProgress.Partly ->
        pluralStringResource(R.plurals.wait_progress_partly, progress.days, progress.waited, progress.days)

    WaitProgress.Done -> stringResource(R.string.wait_progress_done)
}

/** Decoded size of photo thumbnails: enough for 48 dp on the densest screens. */
internal const val THUMBNAIL_SIZE_PX = 192
