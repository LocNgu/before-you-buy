package io.github.locngu.beforeyoubuy.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.locngu.beforeyoubuy.R
import io.github.locngu.beforeyoubuy.core.domain.item.ItemRepository
import io.github.locngu.beforeyoubuy.core.domain.settings.PreferencesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Whether to explain notifications and offer the Android 13+ permission: once, as soon as there is something on the
 * wishlist (the first item added, in the app or by sharing), whatever the answer. App-wide, like quick add.
 */
@HiltViewModel
class NotificationPermissionViewModel @Inject constructor(
    items: ItemRepository,
    private val preferences: PreferencesRepository
) : ViewModel() {
    /** False until both are read, so the prompt never flashes for a user who already answered. */
    val shouldAsk: StateFlow<Boolean> =
        combine(preferences.preferences, items.observeActive()) { prefs, active ->
            !prefs.notificationPermissionAsked && active.isNotEmpty()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), false)

    /** "Allow" or "Not now": the app doesn't ask again. */
    fun answered() {
        viewModelScope.launch { preferences.setNotificationPermissionAsked(true) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/**
 * The one-line explanation before the system's permission dialog (Android 13+ only; earlier versions need no
 * permission). The app works fully without notifications, so "Not now" is as easy as "Allow".
 */
@Composable
fun NotificationPermissionPrompt(viewModel: NotificationPermissionViewModel) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val shouldAsk by viewModel.shouldAsk.collectAsStateWithLifecycle()
    // The system dialog's answer needs no handling here: the daily check reads the permission each time.
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    if (!shouldAsk || hasNotificationPermission(context)) return
    NotificationPermissionDialog(
        onAllow = {
            viewModel.answered()
            request.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
        onNotNow = viewModel::answered
    )
}

/** Stateless, for screenshots. Dismissing it (back, outside) counts as "Not now". */
@Composable
fun NotificationPermissionDialog(onAllow: () -> Unit, onNotNow: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNotNow,
        text = { Text(stringResource(R.string.notification_permission_text)) },
        confirmButton = {
            TextButton(onClick = onAllow) { Text(stringResource(R.string.notification_permission_allow)) }
        },
        dismissButton = {
            TextButton(onClick = onNotNow) { Text(stringResource(R.string.notification_permission_not_now)) }
        }
    )
}

private fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
