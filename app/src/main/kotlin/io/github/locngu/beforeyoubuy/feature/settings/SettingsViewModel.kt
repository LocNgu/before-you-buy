package io.github.locngu.beforeyoubuy.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemRepository
import io.github.locngu.beforeyoubuy.core.domain.priorities.prioritiesOf
import io.github.locngu.beforeyoubuy.core.domain.settings.NotificationNotice
import io.github.locngu.beforeyoubuy.core.domain.settings.NotificationPrefs
import io.github.locngu.beforeyoubuy.core.domain.settings.PolicyRepository
import io.github.locngu.beforeyoubuy.core.domain.settings.PreferencesRepository
import io.github.locngu.beforeyoubuy.core.domain.settings.SystemNotifications
import io.github.locngu.beforeyoubuy.core.domain.settings.notificationNoticeOf
import io.github.locngu.beforeyoubuy.feature.priorities.PriorityEvent
import io.github.locngu.beforeyoubuy.notifications.SystemNotificationsReader
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * What Settings shows (brief §6.7, ADR-0054): the notification switches and what the phone does with them
 * ([notice]), and the priority slots with the active items the slots dialog lists.
 */
data class SettingsUiState(
    val notifications: NotificationPrefs,
    val notice: NotificationNotice,
    val prioritySlots: Int,
    val active: List<Item>
) {
    val priorityCount: Int get() = prioritiesOf(active).size
}

/**
 * Settings: the notification switches (stored in [PreferencesRepository]) and the priority slots (through
 * [PolicyRepository.changePrioritySlots], like the wishlist's "Change", ADR-0047). The phone's own notification
 * switches are read with [SystemNotificationsReader] only by [refreshSystemNotifications], which the screen calls each
 * time it comes to the front (the first time too), since the user may have changed them in the system settings.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: PreferencesRepository,
    private val policies: PolicyRepository,
    items: ItemRepository,
    private val system: SystemNotificationsReader,
    private val clock: Clock
) : ViewModel() {
    // Null until the first resume reads it, so each visit asks the system once, not also at construction.
    private val systemNotifications = MutableStateFlow<SystemNotifications?>(null)
    private val notificationWrites = Mutex()
    private val events = Channel<PriorityEvent>(Channel.BUFFERED)

    /** Null until the preferences, the policy, the items and the phone's notification state are read. */
    val uiState: StateFlow<SettingsUiState?> =
        combine(
            preferences.preferences,
            systemNotifications.filterNotNull(),
            policies.policy,
            items.observeActive()
        ) {
                prefs,
                system,
                policy,
                active
            ->
            SettingsUiState(
                notifications = prefs.notifications,
                notice = notificationNoticeOf(prefs.notifications, system),
                prioritySlots = policy.prioritySlots,
                active = active
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** [PriorityEvent.ChangedMeanwhile] when a slot change found the priorities changed; for the snackbar. */
    val priorityEvents: Flow<PriorityEvent> = events.receiveAsFlow()

    // One slot write at a time, as on the wishlist: a double tap would otherwise find its own change.
    private var writingSlots = false

    /** Reads the phone's notification switches again, e.g. after a return from the system settings. */
    fun refreshSystemNotifications() {
        systemNotifications.value = system.read()
    }

    fun setWaitingPeriods(on: Boolean) = updateNotifications { it.copy(waitingPeriods = on) }

    fun setReflectionReminders(on: Boolean) = updateNotifications { it.copy(reflectionReminders = on) }

    // Applied to the stored preferences, not the shown ones, one change at a time, so two quick taps on different
    // switches both stick.
    private fun updateNotifications(change: (NotificationPrefs) -> NotificationPrefs) {
        viewModelScope.launch {
            notificationWrites.withLock {
                preferences.setNotificationPrefs(change(preferences.preferences.first().notifications))
            }
        }
    }

    /**
     * The slots dialog's Save: fewer slots send the lowest-ranked back, which the dialog listed from [uiState]
     * (ADR-0047). If the priorities changed since, nothing is written and the snackbar says so.
     */
    fun changePrioritySlots(slots: Int) {
        if (writingSlots) return
        val shown = prioritiesOf(uiState.value?.active.orEmpty()).map { it.id }
        writingSlots = true
        viewModelScope.launch {
            try {
                if (!policies.changePrioritySlots(slots, shown, clock.instant(), clock.zone)) {
                    events.send(PriorityEvent.ChangedMeanwhile)
                }
            } finally {
                writingSlots = false
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
