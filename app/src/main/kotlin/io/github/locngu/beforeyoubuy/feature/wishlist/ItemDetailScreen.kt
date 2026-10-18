package io.github.locngu.beforeyoubuy.feature.wishlist

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.locngu.beforeyoubuy.R
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.Reflection
import io.github.locngu.beforeyoubuy.core.domain.item.UpgradeOrNew
import io.github.locngu.beforeyoubuy.core.domain.item.UsageFrequency
import io.github.locngu.beforeyoubuy.core.domain.wishlist.ItemDetail
import io.github.locngu.beforeyoubuy.core.domain.wishlist.canMarkAsNeed
import io.github.locngu.beforeyoubuy.core.domain.wishlist.webLink
import io.github.locngu.beforeyoubuy.ui.components.EmptyState
import io.github.locngu.beforeyoubuy.ui.components.MoneyText
import io.github.locngu.beforeyoubuy.ui.components.SectionHeader
import io.github.locngu.beforeyoubuy.ui.format.formattedDate
import kotlinx.coroutines.launch

/**
 * One item (brief §6.3): the user's reasons and the countdown first, the shop link out of the way while cooling off
 * (ADR-0013). [onEdit] opens the edit screen.
 */
@Composable
fun ItemDetailScreen(
    itemId: Long,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ItemDetailViewModel =
        hiltViewModel<ItemDetailViewModel, ItemDetailViewModel.Factory> { it.create(itemId) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources by rememberUpdatedState(LocalResources.current)
    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.message.collect {
            val text = when (it) {
                ItemDetailMessage.CHANGED_MEANWHILE -> resources.getString(R.string.item_changed_meanwhile)
            }
            snackbarHostState.showSnackbar(text)
        }
    }

    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val uriHandler = LocalUriHandler.current
    ItemDetailContent(
        state = state,
        onEdit = onEdit,
        onMarkAsNeed = viewModel::markAsNeed,
        onCopyLink = { link ->
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, link)))
                snackbarHostState.showSnackbar(resources.getString(R.string.item_link_copied))
            }
        },
        onOpenLink = { link ->
            // No browser, or none that takes the link: say so instead of crashing.
            val opened = runCatching { uriHandler.openUri(link) }.isSuccess
            if (!opened) {
                scope.launch {
                    snackbarHostState.showSnackbar(resources.getString(R.string.item_link_not_opened))
                }
            }
        },
        snackbarHostState = snackbarHostState,
        modifier = modifier
    )
}

/**
 * Stateless item detail, for screenshots. [onOpenLink] gets the address to open ([webLink]); [onCopyLink] the link
 * as stored.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailContent(
    state: ItemDetailUiState,
    onEdit: () -> Unit,
    onMarkAsNeed: () -> Unit,
    onCopyLink: (String) -> Unit,
    onOpenLink: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val detail = state.detail
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    if (detail != null) {
                        TextButton(onClick = onEdit) { Text(stringResource(R.string.item_edit)) }
                        detail.item.link?.let { LinkMenu(it, onCopyLink, onOpenLink) }
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

            detail != null -> Detail(detail, onMarkAsNeed, onOpenLink, Modifier.padding(innerPadding))
        }
    }
}

/** Copy and open the shop link. Always here, also while cooling off, when it's the only way to the link. */
@Composable
private fun LinkMenu(link: String, onCopyLink: (String) -> Unit, onOpenLink: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painterResource(R.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.item_more_options)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.item_copy_link)) },
                onClick = {
                    expanded = false
                    onCopyLink(link)
                }
            )
            webLink(link)?.let { address ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.item_open_link)) },
                    onClick = {
                        expanded = false
                        onOpenLink(address)
                    }
                )
            }
        }
    }
}

@Composable
private fun Detail(
    detail: ItemDetail,
    onMarkAsNeed: () -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Header(detail)
        // The user's own reasons come before anything about the product (ADR-0013).
        Reasons(detail)
        Details(detail)
        Actions(detail, onMarkAsNeed, onOpenLink)
    }
}

@Composable
private fun Header(detail: ItemDetail) {
    val item = detail.item
    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(item.name, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        MoneyText(item.price, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StageChip(detail.stage)
            StageStatus(detail.stage, detail.daysLeft)
        }
        val wanted = if (detail.wantedForDays == 0) {
            stringResource(R.string.item_added_today)
        } else {
            pluralStringResource(R.plurals.item_wanted_for_days, detail.wantedForDays, detail.wantedForDays)
        }
        SecondaryText(wanted)
        if (item.kind == ItemKind.NEED) SecondaryText(stringResource(R.string.item_need))
    }
}

/** The reflection answers given so far, and while the required ones are missing, a card asking for them. */
@Composable
private fun Reasons(detail: ItemDetail) {
    val reflection = detail.reflection
    val answers = reflection?.let { answersOf(it) }.orEmpty()
    if (answers.isEmpty() && detail.reflected) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(stringResource(R.string.item_reasons_title))
        answers.forEach { (label, value) -> Answer(label, value) }
        if (!detail.reflected) ReflectPrompt(started = answers.isNotEmpty())
    }
}

/** Asks for the reasons; once some are given, for the rest. */
@Composable
private fun ReflectPrompt(started: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val title = if (started) R.string.item_reflect_prompt_title_started else R.string.item_reflect_prompt_title
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.item_reflect_prompt_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Each answer that was given, labelled, in the order the questions are asked (brief §4.3). */
@Composable
private fun answersOf(reflection: Reflection): List<Pair<String, String>> = buildList {
    fun add(label: String, value: String?) {
        if (!value.isNullOrBlank()) add(label to value.trim())
    }
    add(stringResource(R.string.item_reason_why), reflection.why)
    add(stringResource(R.string.item_reason_usage), reflection.usageFrequency?.let { usageLabel(it) })
    val against = reflection.reasonsAgainst.filter { it.isNotBlank() }
    add(stringResource(R.string.item_reasons_against), against.joinToString("\n") { "• ${it.trim()}" })
    add(stringResource(R.string.item_reason_problem_solved), reflection.problemSolved)
    add(stringResource(R.string.item_reason_current_alternative), reflection.currentAlternative)
    add(stringResource(R.string.item_reason_replaces), reflection.replaces)
    add(stringResource(R.string.item_reason_why_now), reflection.whyNow)
    add(stringResource(R.string.item_reason_upgrade_or_new), reflection.upgradeOrNew?.let { upgradeOrNewLabel(it) })
}

@Composable
private fun usageLabel(usage: UsageFrequency): String = stringResource(
    when (usage) {
        UsageFrequency.DAILY -> R.string.usage_daily
        UsageFrequency.WEEKLY -> R.string.usage_weekly
        UsageFrequency.MONTHLY -> R.string.usage_monthly
        UsageFrequency.RARELY -> R.string.usage_rarely
        UsageFrequency.ONCE -> R.string.usage_once
    }
)

@Composable
private fun upgradeOrNewLabel(value: UpgradeOrNew): String = stringResource(
    when (value) {
        UpgradeOrNew.UPGRADE -> R.string.upgrade_or_new_upgrade
        UpgradeOrNew.NEW -> R.string.upgrade_or_new_new
    }
)

@Composable
private fun Details(detail: ItemDetail) {
    val item = detail.item
    val answers = listOfNotNull(
        item.category?.let { stringResource(R.string.item_category) to it },
        item.notes?.let { stringResource(R.string.item_notes) to it },
        item.targetDate?.let { stringResource(R.string.item_target_date) to formattedDate(it) }
    )
    if (answers.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(stringResource(R.string.item_details_title))
        answers.forEach { (label, value) -> Answer(label, value) }
    }
}

@Composable
private fun Answer(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        SecondaryText(label, style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * Once cooled off, the link may show as a plain text button; while cooling off it's only in the menu (ADR-0013).
 * "Mark as need" asks first: it ends the wait.
 */
@Composable
private fun Actions(detail: ItemDetail, onMarkAsNeed: () -> Unit, onOpenLink: (String) -> Unit) {
    val address = detail.item.link?.let { webLink(it) }?.takeIf { detail.linkProminent }
    val canMarkAsNeed = detail.item.canMarkAsNeed
    if (address == null && !canMarkAsNeed) return
    var confirming by rememberSaveable { mutableStateOf(false) }
    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (address != null) {
            // Lines the label up with the text above; the button's own padding stays part of the touch target.
            TextButton(onClick = { onOpenLink(address) }, modifier = Modifier.offset(x = (-12).dp)) {
                Text(stringResource(R.string.item_open_link))
            }
        }
        if (canMarkAsNeed) {
            OutlinedButton(onClick = { confirming = true }) { Text(stringResource(R.string.item_mark_as_need)) }
        }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.item_mark_as_need_title)) },
            text = { Text(stringResource(R.string.item_mark_as_need_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        onMarkAsNeed()
                    }
                ) { Text(stringResource(R.string.item_mark_as_need)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.dialog_cancel)) }
            }
        )
    }
}

@Composable
private fun SecondaryText(text: String, style: TextStyle = MaterialTheme.typography.bodyMedium) {
    Text(text, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
