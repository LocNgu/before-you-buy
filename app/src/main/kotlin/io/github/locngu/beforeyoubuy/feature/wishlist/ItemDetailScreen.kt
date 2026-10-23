package io.github.locngu.beforeyoubuy.feature.wishlist

import android.content.ClipData
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.locngu.beforeyoubuy.R
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.MAX_IMAGE_SIZE_PX
import io.github.locngu.beforeyoubuy.core.domain.item.Reflection
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.Stage
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.core.domain.money.format
import io.github.locngu.beforeyoubuy.core.domain.wishlist.ItemDetail
import io.github.locngu.beforeyoubuy.core.domain.wishlist.canMarkAsNeed
import io.github.locngu.beforeyoubuy.core.domain.wishlist.webLink
import io.github.locngu.beforeyoubuy.feature.capture.priceErrorMessage
import io.github.locngu.beforeyoubuy.feature.money.SavingsProgressBar
import io.github.locngu.beforeyoubuy.feature.reflection.upgradeOrNewLabel
import io.github.locngu.beforeyoubuy.feature.reflection.usageLabel
import io.github.locngu.beforeyoubuy.ui.components.ButtonPair
import io.github.locngu.beforeyoubuy.ui.components.EmptyState
import io.github.locngu.beforeyoubuy.ui.components.MoneyText
import io.github.locngu.beforeyoubuy.ui.components.PrimaryButton
import io.github.locngu.beforeyoubuy.ui.components.SecondaryButton
import io.github.locngu.beforeyoubuy.ui.components.SectionHeader
import io.github.locngu.beforeyoubuy.ui.format.currencySymbol
import io.github.locngu.beforeyoubuy.ui.format.currentLocale
import io.github.locngu.beforeyoubuy.ui.format.formattedDate
import java.io.File
import kotlin.math.pow
import kotlinx.coroutines.launch

/**
 * One item (brief §6.3): the user's reasons and the countdown first, the shop link out of the way while cooling off
 * (ADR-0013). [onEdit] opens the edit screen, [onReflect] the reflection questions (brief §6.4), [onDecide] the
 * decision screen (brief §6.5), [onOpenFunMoney] the fun money screen to set it up (brief §4.5).
 */
@Composable
fun ItemDetailScreen(
    itemId: Long,
    onEdit: () -> Unit,
    onReflect: () -> Unit,
    onDecide: () -> Unit,
    onOpenFunMoney: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ItemDetailViewModel =
        hiltViewModel<ItemDetailViewModel, ItemDetailViewModel.Factory> { it.create(itemId) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources by rememberUpdatedState(LocalResources.current)
    val locale = currentLocale()
    LaunchedEffect(viewModel, locale) { viewModel.setLocale(locale) }
    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.message.collect {
            val text = when (it) {
                ItemDetailMessage.CHANGED_MEANWHILE -> resources.getString(R.string.item_changed_meanwhile)
                ItemDetailMessage.SAVINGS_CHANGED_MEANWHILE -> resources.getString(R.string.savings_changed_meanwhile)
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
        onReflect = onReflect,
        onDecide = onDecide,
        onMarkAsNeed = viewModel::markAsNeed,
        onOpenFunMoney = onOpenFunMoney,
        savingsActions = viewModel,
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
    onReflect: () -> Unit,
    onDecide: () -> Unit,
    onMarkAsNeed: () -> Unit,
    onOpenFunMoney: () -> Unit,
    savingsActions: SavingsActions,
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

            detail != null ->
                Detail(
                    detail,
                    state.photo,
                    onReflect,
                    onDecide,
                    onMarkAsNeed,
                    onOpenFunMoney,
                    savingsActions,
                    onOpenLink,
                    Modifier.padding(innerPadding)
                )
        }
    }
    val sheet = state.sheet
    if (detail != null && sheet != null) {
        ModalBottomSheet(onDismissRequest = savingsActions::dismissSavings) {
            SavingsSheetContent(sheet, detail.item.name, savingsActions)
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
    photo: File?,
    onReflect: () -> Unit,
    onDecide: () -> Unit,
    onMarkAsNeed: () -> Unit,
    onOpenFunMoney: () -> Unit,
    savingsActions: SavingsActions,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Header(detail)
        // The user's own reasons come before anything about the product (ADR-0013).
        Reasons(detail, onReflect)
        if (photo != null) Photo(detail, photo)
        Savings(detail, onOpenFunMoney, savingsActions)
        Details(detail)
        Decide(detail, onDecide)
        Actions(detail, onMarkAsNeed, onOpenLink)
    }
}

/**
 * What is set aside for a want (brief §4.6), with equal "Set aside" and "Take back" buttons; without fun money, a way
 * to set it up. Nothing for a need, which is never saved for (ADR-0044). Saving can start while it cools off.
 */
@Composable
private fun Savings(detail: ItemDetail, onOpenFunMoney: () -> Unit, actions: SavingsActions) {
    val progress = detail.savings ?: return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(stringResource(R.string.savings_title))
        if (!detail.funMoneySetUp) {
            SecondaryText(stringResource(R.string.savings_not_set_up), Modifier.padding(horizontal = 16.dp))
            // Lines the label up with the text above; the button's own padding stays part of the touch target.
            TextButton(onClick = onOpenFunMoney, modifier = Modifier.padding(horizontal = 16.dp).offset(x = (-12).dp)) {
                Text(stringResource(R.string.savings_set_up))
            }
            return@Column
        }
        SavingsProgressBar(progress, Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = actions::openSetAside, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.savings_set_aside))
            }
            OutlinedButton(
                onClick = actions::openTakeBack,
                enabled = progress.allocated.minorUnits > 0,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.savings_take_back))
            }
        }
    }
}

/**
 * The "Set aside" / "Take back" sheet's content (a bottom sheet is its own window, so screenshots draw this): the
 * amount, how much there is to move, quick chips and the confirm button.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SavingsSheetContent(sheet: SavingsSheet, itemName: String, actions: SavingsActions, modifier: Modifier = Modifier) {
    val locale = currentLocale()
    val setAside = sheet.mode == SavingsMode.SET_ASIDE
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(
                if (setAside) R.string.savings_sheet_set_aside_title else R.string.savings_sheet_take_back_title,
                itemName
            ),
            style = MaterialTheme.typography.titleLarge
        )
        SecondaryText(
            stringResource(
                if (setAside) R.string.savings_not_set_aside else R.string.savings_held,
                sheet.limit.format(locale)
            )
        )
        val error = sheet.error?.let { savingsErrorMessage(it, sheet) }
        OutlinedTextField(
            value = sheet.amount,
            onValueChange = actions::onSavingsAmountChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.savings_amount)) },
            suffix = { Text(currencySymbol(sheet.limit.currency)) },
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { actions.confirmSavings() })
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (setAside) {
                QUICK_AMOUNTS.map { wholeUnits(it, sheet.limit.currency) }.filter { it <= sheet.limit }.forEach {
                    SuggestionChip(
                        onClick = { actions.onSavingsChip(it) },
                        label = { Text(stringResource(R.string.savings_chip_add, it.format(locale))) }
                    )
                }
            }
            sheet.rest?.let { rest ->
                SuggestionChip(
                    onClick = { actions.onSavingsChip(rest) },
                    label = {
                        Text(
                            stringResource(
                                if (setAside) R.string.savings_chip_rest else R.string.savings_chip_all,
                                rest.format(locale)
                            )
                        )
                    }
                )
            }
        }
        // Equal buttons: neither direction is pushed (ADR-0012).
        ButtonPair(
            start = { SecondaryButton(stringResource(R.string.dialog_cancel), actions::dismissSavings, it) },
            end = {
                SecondaryButton(
                    stringResource(if (setAside) R.string.savings_set_aside else R.string.savings_take_back),
                    actions::confirmSavings,
                    it,
                    enabled = sheet.canConfirm
                )
            }
        )
    }
}

/** The chips' amounts in whole units of the currency: +50, +100. */
private val QUICK_AMOUNTS = listOf(50L, 100L)

private fun wholeUnits(units: Long, currency: String): Money =
    Money(units * 10.0.pow(Money.fractionDigits(currency)).toLong(), currency)

@Composable
private fun savingsErrorMessage(error: SavingsError, sheet: SavingsSheet): String = when (error) {
    is SavingsError.Unreadable ->
        priceErrorMessage(error.result, sheet.limit.currency, R.string.savings_amount_zero).orEmpty()

    SavingsError.Zero -> stringResource(R.string.savings_amount_zero)

    SavingsError.MoreThanLimit -> stringResource(
        if (sheet.mode == SavingsMode.SET_ASIDE) {
            R.string.savings_more_than_not_set_aside
        } else {
            R.string.savings_more_than_held
        },
        sheet.limit.format(currentLocale())
    )
}

@Composable
private fun Header(detail: ItemDetail) {
    val item = detail.item
    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(item.name, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        MoneyText(item.price, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StageChip(detail.stage, waiting = detail.recheckOn != null)
            StageStatus(detail.stage, detail.daysLeft, detail.recheckOn, detail.savings?.toGo)
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

/**
 * The item's photo, after the reasons. While cooling off it's a small thumbnail to tap: imagining the product
 * increases desire (ADR-0013). Once cooled off it shows larger, cropped to a band. A tap shows it whole, full screen.
 */
@Composable
private fun Photo(detail: ItemDetail, photo: File) {
    var viewing by rememberSaveable { mutableStateOf(false) }
    val description = stringResource(R.string.item_photo_description, detail.item.name)
    val size = if (detail.imageProminent) MAX_IMAGE_SIZE_PX else THUMBNAIL_SIZE_PX
    // Nothing until it's loaded, and nothing at all if the file is gone: never an empty "Tap to view".
    val bitmap = rememberPhoto(photo, size) ?: return
    if (detail.imageProminent) {
        // A fixed-height crop; the viewer shows the whole photo.
        ItemPhoto(
            bitmap = bitmap,
            contentDescription = description,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(200.dp)
                .clip(MaterialTheme.shapes.medium)
                .clickable { viewing = true }
        )
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewing = true }
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ItemPhoto(
                bitmap = bitmap,
                contentDescription = description,
                modifier = Modifier.size(48.dp).clip(MaterialTheme.shapes.small)
            )
            Column {
                Text(stringResource(R.string.item_photo), style = MaterialTheme.typography.bodyLarge)
                SecondaryText(stringResource(R.string.item_photo_tap_to_view))
            }
        }
    }
    if (viewing) PhotoViewer(photo, description, onClose = { viewing = false })
}

/** The photo on its own, as large as the screen allows. A tap or back closes it. */
@Composable
private fun PhotoViewer(photo: File, description: String, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            rememberPhoto(photo, MAX_IMAGE_SIZE_PX)?.let {
                ItemPhoto(
                    bitmap = it,
                    contentDescription = description,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * The reflection answers given so far. While the required ones are missing, a card asking for them opens the
 * questions; once they're there, "Edit answers" does. A need without answers gets only "Add your reasons". Editing
 * them never changes the wait.
 */
@Composable
private fun Reasons(detail: ItemDetail, onReflect: () -> Unit) {
    val reflection = detail.reflection
    val answers = reflection?.let { answersOf(it) }.orEmpty()
    if (answers.isEmpty() && detail.reflected) {
        // Only a need is reflected without answers. It skips reflection (ADR-0008), so its reasons are optional:
        // a quiet way in, no card asking for them (ADR-0039).
        ReflectLink(R.string.item_reasons_add, onReflect)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(stringResource(R.string.item_reasons_title))
        answers.forEach { (label, value) -> Answer(label, value) }
        if (!detail.reflected) {
            ReflectPrompt(started = answers.isNotEmpty(), onReflect = onReflect)
        } else {
            ReflectLink(R.string.item_reasons_edit, onReflect)
        }
    }
}

/** A plain text button that opens the questions. */
@Composable
private fun ReflectLink(@StringRes label: Int, onReflect: () -> Unit) {
    // Lines the label up with the text around it; the button's own padding stays part of the touch target.
    TextButton(onClick = onReflect, modifier = Modifier.padding(horizontal = 16.dp).offset(x = (-12).dp)) {
        Text(stringResource(label))
    }
}

/** Asks for the reasons; once some are given, for the rest. The whole card opens the questions. */
@Composable
private fun ReflectPrompt(started: Boolean, onReflect: () -> Unit) {
    Card(
        onClick = onReflect,
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
            Text(
                stringResource(if (started) R.string.item_reflect_continue else R.string.item_reflect_start),
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** Each answer that was given, labelled, in the order the questions are asked (brief §4.3). */
@Composable
internal fun answersOf(reflection: Reflection): List<Pair<String, String>> = buildList {
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
internal fun Answer(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        SecondaryText(label, style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * Opens the decision screen (brief §6.5). Once the item is Ready, or for a need, it's a button like the decision
 * buttons; before that a quieter one: deciding now means letting it go or buying early (ADR-0006).
 */
@Composable
private fun Decide(detail: ItemDetail, onDecide: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        // A Ready item waiting after "Wait" isn't shown as Ready until its date.
        if (detail.stage == Stage.READY && detail.recheckOn == null) {
            PrimaryButton(stringResource(R.string.item_decide), onDecide, Modifier.fillMaxWidth())
        } else {
            OutlinedButton(onClick = onDecide) { Text(stringResource(R.string.item_decide_now)) }
        }
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
            text = { MarkAsNeedBody(detail.savings?.allocated) },
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

/**
 * Why a need is different, and, if money is set aside for the want, that it goes back to the fun money (ADR-0044).
 * Edit asks the same when its switch makes a want with savings a need.
 */
@Composable
internal fun MarkAsNeedBody(allocated: Money?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.item_mark_as_need_body))
        if (allocated != null && allocated.minorUnits > 0) {
            Text(stringResource(R.string.item_mark_as_need_release, allocated.format(currentLocale())))
        }
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
