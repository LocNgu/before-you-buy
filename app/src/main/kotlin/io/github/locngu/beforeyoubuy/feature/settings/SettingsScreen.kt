package io.github.locngu.beforeyoubuy.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.locngu.beforeyoubuy.R
import io.github.locngu.beforeyoubuy.core.domain.backup.BackupPreview
import io.github.locngu.beforeyoubuy.core.domain.backup.BackupProblem
import io.github.locngu.beforeyoubuy.core.domain.settings.NotificationNotice
import io.github.locngu.beforeyoubuy.core.domain.settings.NotificationPrefs
import io.github.locngu.beforeyoubuy.feature.priorities.PrioritySlotsDialog
import io.github.locngu.beforeyoubuy.feature.priorities.priorityMessage
import io.github.locngu.beforeyoubuy.notifications.Notifier
import io.github.locngu.beforeyoubuy.ui.components.SectionHeader
import io.github.locngu.beforeyoubuy.ui.format.currentLocale
import io.github.locngu.beforeyoubuy.ui.format.formatDate
import java.time.ZoneId
import kotlinx.coroutines.launch

/**
 * What Settings can do besides changing its own state: open other screens and system pages. Defaults do nothing, for
 * screenshots.
 */
class SettingsActions(
    val onOpenFunMoney: () -> Unit = {},
    val onChangePrioritySlots: (Int) -> Unit = {},
    val onWaitingPeriods: (Boolean) -> Unit = {},
    val onReflectionReminders: (Boolean) -> Unit = {},
    val onOpenAppNotifications: () -> Unit = {},
    val onOpenChannel: (channelId: String) -> Unit = {},
    val onOpenLanguage: () -> Unit = {},
    val onExportBackup: () -> Unit = {},
    val onImportBackup: () -> Unit = {},
    val onCancelImport: () -> Unit = {},
    val onConfirmImport: () -> Unit = {},
    val onOpenLicence: () -> Unit = {},
    val onOpenSource: () -> Unit = {}
)

/**
 * Settings (brief §6.7, ADR-0054), opened from Home's top bar; back returns to Home. [onOpenFunMoney] opens the fun
 * money screen. The phone's notification switches are read again each time the screen comes back to the front.
 */
@Composable
fun SettingsScreen(
    onOpenFunMoney: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    backupViewModel: BackupViewModel = hiltViewModel()
) {
    val backupState by backupViewModel.uiState.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val uriHandler = LocalUriHandler.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshSystemNotifications()
        onPauseOrDispose {}
    }
    LaunchedEffect(viewModel) {
        viewModel.priorityEvents.collect { event ->
            priorityMessage(event, resources)?.let { snackbarHostState.showSnackbar(it) }
        }
    }
    LaunchedEffect(backupViewModel) {
        backupViewModel.backupEvents.collect { snackbarHostState.showSnackbar(backupMessage(it, resources)) }
    }
    // The system's file pickers: no storage permission (ADR-0055).
    val version = remember(context) { appVersion(context) }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE)) {
        backupViewModel.export(it, version)
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        backupViewModel.startImport(it)
    }

    // A system page or browser that isn't there (a stripped-down phone): say so instead of crashing.
    fun open(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.settings_no_app)) }
        }
    }
    fun openWeb(url: String) {
        if (runCatching { uriHandler.openUri(url) }.isFailure) {
            scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.settings_no_app)) }
        }
    }
    // Remembered, so a new state (a switch, a resume) doesn't hand the sections new callbacks and recompose them all.
    val actions = remember(viewModel, backupViewModel, context, onOpenFunMoney) {
        SettingsActions(
            onOpenFunMoney = onOpenFunMoney,
            onChangePrioritySlots = viewModel::changePrioritySlots,
            onWaitingPeriods = viewModel::setWaitingPeriods,
            onReflectionReminders = viewModel::setReflectionReminders,
            onOpenAppNotifications = { open(SettingsIntents.appNotifications(context)) },
            onOpenChannel = { open(SettingsIntents.channel(context, it)) },
            onOpenLanguage = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) open(SettingsIntents.appLanguage(context))
            },
            onExportBackup = { exportPicker.launch(backupViewModel.suggestedFileName()) },
            onImportBackup = { importPicker.launch(BACKUP_OPEN_MIME_TYPES) },
            onCancelImport = backupViewModel::cancelImport,
            onConfirmImport = backupViewModel::confirmReplace,
            onOpenLicence = { openWeb(LICENCE_URL) },
            onOpenSource = { openWeb(SOURCE_URL) }
        )
    }
    SettingsContent(
        state = state,
        version = version,
        backup = backupState,
        perAppLanguage = hasPerAppLanguageSettings(),
        actions = actions,
        snackbarHostState = snackbarHostState,
        modifier = modifier
    )
}

/**
 * Stateless Settings, for screenshots. The sections in order: fun money, priorities, notifications, language, about
 * (backup sits between language and about; #97 waiting periods and currency, #98 the third-party licences). [state] null is "not loaded":
 * the sections that depend on it wait, so the switches never flip after the first frame. [perAppLanguage]: Android
 * 13+ (a row to the system's language page), else one line.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    state: SettingsUiState?,
    version: String,
    perAppLanguage: Boolean,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    backup: BackupUiState = BackupUiState.Idle,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    var changingSlots by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        if (state == null) return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item(key = "fun_money") {
                Column {
                    SectionHeader(stringResource(R.string.fun_money_title))
                    LinkRow(stringResource(R.string.settings_fun_money_row), onClick = actions.onOpenFunMoney)
                }
            }
            item(key = "priorities") {
                Column {
                    SectionHeader(stringResource(R.string.priorities_title))
                    LinkRow(
                        headline = stringResource(R.string.priority_slots_title),
                        supporting = pluralStringResource(
                            R.plurals.priority_slots_used,
                            state.prioritySlots,
                            state.priorityCount,
                            state.prioritySlots
                        ),
                        onClick = { changingSlots = true }
                    )
                }
            }
            item(key = "notifications") { NotificationsSection(state.notifications, state.notice, actions) }
            item(key = "language") { LanguageSection(perAppLanguage, actions.onOpenLanguage) }
            item(key = "backup") { BackupSection(backup, actions) }
            item(key = "about") { AboutSection(version, actions) }
        }
        if (backup is BackupUiState.Preview) {
            ImportDialogs(backup.preview, actions.onConfirmImport, actions.onCancelImport)
        }
        if (changingSlots) {
            PrioritySlotsDialog(
                current = state.prioritySlots,
                active = state.active,
                onSave = {
                    changingSlots = false
                    actions.onChangePrioritySlots(it)
                },
                onDismiss = { changingSlots = false }
            )
        }
    }
}

/**
 * The two switches of [NotificationPrefs], named like their channels in the system settings, and what the phone does
 * with them ([notice]): blocked for the whole app (one line and the app's notification page) or a channel switched off
 * (a line under its switch and the channel's page).
 */
@Composable
private fun NotificationsSection(prefs: NotificationPrefs, notice: NotificationNotice, actions: SettingsActions) {
    Column {
        SectionHeader(stringResource(R.string.settings_notifications_title))
        if (notice == NotificationNotice.AppBlocked) {
            Notice(
                stringResource(R.string.settings_notifications_blocked),
                onOpen = actions.onOpenAppNotifications
            )
        }
        val channelsOff = notice as? NotificationNotice.ChannelsOff
        SwitchRow(
            headline = stringResource(R.string.notification_channel_waiting_periods),
            supporting = stringResource(R.string.notification_channel_waiting_periods_description),
            checked = prefs.waitingPeriods,
            onCheckedChange = actions.onWaitingPeriods
        )
        if (channelsOff?.waitingPeriods == true) {
            Notice(
                stringResource(R.string.settings_notifications_channel_off),
                onOpen = { actions.onOpenChannel(Notifier.CHANNEL_WAITING_PERIODS) }
            )
        }
        SwitchRow(
            headline = stringResource(R.string.notification_channel_reflection),
            supporting = stringResource(R.string.notification_channel_reflection_description),
            checked = prefs.reflectionReminders,
            onCheckedChange = actions.onReflectionReminders
        )
        if (channelsOff?.reflectionReminders == true) {
            Notice(
                stringResource(R.string.settings_notifications_channel_off),
                onOpen = { actions.onOpenChannel(Notifier.CHANNEL_REFLECTION) }
            )
        }
    }
}

/** The language follows the phone; Android 13+ also lets the app have its own, chosen in the system settings. */
@Composable
private fun LanguageSection(perAppLanguage: Boolean, onOpenLanguage: () -> Unit) {
    Column {
        SectionHeader(stringResource(R.string.settings_language_title))
        if (perAppLanguage) {
            LinkRow(
                headline = stringResource(R.string.settings_language_app),
                supporting = stringResource(R.string.settings_language_current),
                onClick = onOpenLanguage
            )
        } else {
            QuietLine(stringResource(R.string.settings_language_follows_system))
        }
    }
}

/** Export and import of the whole app (brief §9, ADR-0055). The rows are inert while a file is being handled. */
@Composable
private fun BackupSection(state: BackupUiState, actions: SettingsActions) {
    val idle = state == BackupUiState.Idle
    Column {
        SectionHeader(stringResource(R.string.backup_title))
        LinkRow(
            headline = stringResource(R.string.backup_export),
            supporting = stringResource(R.string.backup_export_description),
            onClick = { if (idle) actions.onExportBackup() }
        )
        LinkRow(
            headline = stringResource(R.string.backup_import),
            supporting = stringResource(R.string.backup_import_description),
            onClick = { if (idle) actions.onImportBackup() }
        )
        if (state == BackupUiState.Working) QuietLine(stringResource(R.string.backup_working))
    }
}

/**
 * What the picked file holds and "Replace all", then one more confirmation: an import replaces everything and can't
 * be undone. Both ways out are equally easy (ADR-0012's spirit: no pressure).
 */
@Composable
private fun ImportDialogs(preview: BackupPreview, onConfirm: () -> Unit, onCancel: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    if (!confirming) {
        val counts = stringResource(
            R.string.backup_import_counts,
            pluralStringResource(R.plurals.backup_count_items, preview.items, preview.items),
            pluralStringResource(R.plurals.backup_count_decisions, preview.decisions, preview.decisions),
            pluralStringResource(R.plurals.backup_count_photos, preview.photos, preview.photos)
        )
        val date = formatDate(preview.exportedAt.atZone(ZoneId.systemDefault()).toLocalDate(), currentLocale())
        AlertDialog(
            onDismissRequest = onCancel,
            title = { Text(stringResource(R.string.backup_import_title)) },
            text = { Text(stringResource(R.string.backup_import_body, date, counts)) },
            confirmButton = {
                TextButton(onClick = { confirming = true }) { Text(stringResource(R.string.backup_import_replace)) }
            },
            dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.dialog_cancel)) } }
        )
    } else {
        AlertDialog(
            onDismissRequest = onCancel,
            title = { Text(stringResource(R.string.backup_import_confirm_title)) },
            text = { Text(stringResource(R.string.backup_import_confirm_body)) },
            confirmButton = {
                TextButton(onClick = onConfirm) { Text(stringResource(R.string.backup_import_confirm)) }
            },
            dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.dialog_cancel)) } }
        )
    }
}

internal fun backupMessage(event: BackupEvent, resources: android.content.res.Resources): String = resources.getString(
    when (event) {
        BackupEvent.Exported -> R.string.backup_exported

        BackupEvent.ExportFailed -> R.string.backup_export_failed

        BackupEvent.Imported -> R.string.backup_imported

        BackupEvent.ImportFailed -> R.string.backup_import_failed

        BackupEvent.ImportUnreadable -> R.string.backup_import_unreadable

        is BackupEvent.ImportRejected -> when (event.problem) {
            BackupProblem.NOT_A_BACKUP -> R.string.backup_rejected_not_a_backup
            BackupProblem.NEWER_VERSION -> R.string.backup_rejected_newer_version
            BackupProblem.DAMAGED -> R.string.backup_rejected_damaged
        }
    }
)

private const val BACKUP_MIME_TYPE = "application/zip"
private val BACKUP_OPEN_MIME_TYPES =
    arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")

/** Version, licence, source and what happens with the data (brief §9). Source available, never "open source". */
@Composable
private fun AboutSection(version: String, actions: SettingsActions) {
    Column {
        SectionHeader(stringResource(R.string.settings_about_title))
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_version)) },
            supportingContent = { Text(version) }
        )
        LinkRow(
            headline = stringResource(R.string.settings_licence),
            supporting = stringResource(R.string.settings_licence_name),
            onClick = actions.onOpenLicence
        )
        LinkRow(
            headline = stringResource(R.string.settings_source),
            supporting = stringResource(R.string.settings_source_location),
            onClick = actions.onOpenSource
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_privacy_title)) },
            supportingContent = { Text(stringResource(R.string.settings_privacy_body)) }
        )
    }
}

/** A row that opens something. */
@Composable
private fun LinkRow(headline: String, onClick: () -> Unit, supporting: String? = null) {
    ListItem(
        headlineContent = { Text(headline) },
        supportingContent = supporting?.let { { Text(it) } },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

/** A switch with its label; the whole row toggles it. */
@Composable
private fun SwitchRow(headline: String, supporting: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(headline) },
        supportingContent = { Text(supporting) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
    )
}

/** What the phone does differently from the switches, and the way to its settings page. */
@Composable
private fun Notice(text: String, onOpen: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // Lined up with the text above; the button's own padding stays part of the touch target.
        TextButton(onClick = onOpen, modifier = Modifier.offset(x = (-12).dp)) {
            Text(stringResource(R.string.settings_open_system_settings))
        }
    }
}

/** One quiet line under a section heading. */
@Composable
private fun QuietLine(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** The installed version (`versionName`, from the release tag, ADR-0029). */
private fun appVersion(context: Context): String {
    val packages = context.packageManager
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packages.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION") // The flags overload exists from Android 13.
        packages.getPackageInfo(context.packageName, 0)
    }
    return info.versionName.orEmpty()
}
