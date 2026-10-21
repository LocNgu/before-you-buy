package io.github.locngu.beforeyoubuy.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.locngu.beforeyoubuy.core.domain.history.HistoryDetail
import io.github.locngu.beforeyoubuy.core.domain.history.historyDetailOf
import io.github.locngu.beforeyoubuy.core.domain.history.wantedAgain
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemRepository
import io.github.locngu.beforeyoubuy.core.domain.settings.PolicyRepository
import java.time.Clock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What a history entry shows. [detail] is null until loaded and while [gone]: the entry was deleted (or isn't
 * history, e.g. a stale link to an item that is active). [busy] while "Want it again?" or "Delete" is being written.
 */
data class HistoryItemUiState(val detail: HistoryDetail? = null, val gone: Boolean = false, val busy: Boolean = false)

/** How an action ended the screen. */
sealed interface HistoryItemOutcome {
    data object Deleted : HistoryItemOutcome

    /** "Want it again?" added [id], a new item on the wishlist. */
    data class WantedAgain(val id: ItemId) : HistoryItemOutcome
}

/** A message for the snackbar. */
enum class HistoryItemMessage {
    /** The entry changed meanwhile; nothing was written. */
    CHANGED_MEANWHILE
}

/**
 * One history entry (brief §6.6, issue #19), read-only: what was bought or decided against, with the user's original
 * reasons. A decided-against item can be wanted again (a **new** item, `wantedAgain` in `:core:domain`; the entry
 * stays), and any entry can be deleted, only through this explicit, confirmed action.
 */
@HiltViewModel(assistedFactory = HistoryItemViewModel.Factory::class)
class HistoryItemViewModel @AssistedInject constructor(
    @Assisted itemId: Long,
    private val items: ItemRepository,
    private val policies: PolicyRepository,
    private val clock: Clock
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(itemId: Long): HistoryItemViewModel
    }

    private val id = ItemId(itemId)
    private val busy = MutableStateFlow(false)

    // Set while deleting and after, so the screen keeps showing the entry instead of flashing "gone" until it closes.
    private val deleting = MutableStateFlow(false)
    private val outcomes = Channel<HistoryItemOutcome>(Channel.BUFFERED)
    private val messages = Channel<HistoryItemMessage>(Channel.BUFFERED)

    val uiState: StateFlow<HistoryItemUiState> =
        combine(items.observeById(id), items.observeDecisions(id), items.observeReflection(id)) { item, decisions, r ->
            item?.let { historyDetailOf(it, decisions, r, clock.zone) }
        }
            .combine(busy, ::Pair)
            .combine(deleting) { (detail, isBusy), isDeleting -> Triple(detail, isBusy, isDeleting) }
            .scan(HistoryItemUiState()) { previous, (detail, isBusy, isDeleting) ->
                when {
                    detail != null -> HistoryItemUiState(detail, busy = isBusy)
                    isDeleting -> previous.copy(busy = true)
                    else -> HistoryItemUiState(gone = true)
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryItemUiState())

    /** One-shot: the screen closes. */
    val outcome: Flow<HistoryItemOutcome> = outcomes.receiveAsFlow()

    /** One-shot messages for the snackbar. */
    val message: Flow<HistoryItemMessage> = messages.receiveAsFlow()

    /**
     * "Want it again?": adds a new want with the same facts that cools off from now (issue #19). The entry stays in
     * "Decided against". Does nothing for a purchase or while another action is being written.
     */
    fun wantAgain() {
        val detail = uiState.value.detail as? HistoryDetail.DecidedAgainst ?: return
        if (!busy.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            try {
                val policy = policies.policy.first()
                val item = detail.entry.item
                // Only while a currency change is being written could they differ.
                if (item.price.currency != policy.currency) {
                    return@launch messages.send(HistoryItemMessage.CHANGED_MEANWHILE)
                }
                val newId = items.add(wantedAgain(item, policy, clock.instant(), clock.zone))
                outcomes.send(HistoryItemOutcome.WantedAgain(newId))
            } finally {
                busy.value = false
            }
        }
    }

    /** Deletes the entry with its answers and decisions, after the screen has asked (issue #19). */
    fun delete() {
        val detail = uiState.value.detail ?: return
        if (!busy.compareAndSet(expect = false, update = true)) return
        val item = when (detail) {
            is HistoryDetail.Bought -> detail.entry.item
            is HistoryDetail.DecidedAgainst -> detail.entry.item
        }
        viewModelScope.launch {
            try {
                deleting.value = true
                if (items.delete(item)) {
                    outcomes.send(HistoryItemOutcome.Deleted)
                } else {
                    deleting.value = false
                    messages.send(HistoryItemMessage.CHANGED_MEANWHILE)
                }
            } finally {
                busy.value = false
            }
        }
    }
}
