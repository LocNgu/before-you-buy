package io.github.locngu.beforeyoubuy.feature.wishlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.locngu.beforeyoubuy.core.domain.coolingoff.changeKind
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.ItemRepository
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.settings.PolicyRepository
import io.github.locngu.beforeyoubuy.core.domain.wishlist.ItemDetail
import io.github.locngu.beforeyoubuy.core.domain.wishlist.canMarkAsNeed
import io.github.locngu.beforeyoubuy.core.domain.wishlist.itemDetailOf
import io.github.locngu.beforeyoubuy.core.domain.wishlist.untilWishlistChanges
import java.time.Clock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What item detail shows (brief §6.3). [detail] is null until loaded and while [gone]: the item was ended (e.g.
 * "Let it go" from a notification) or deleted.
 */
data class ItemDetailUiState(val detail: ItemDetail? = null, val gone: Boolean = false)

/** A message for the snackbar. */
enum class ItemDetailMessage {
    /** "Mark as need" found the item changed meanwhile and wrote nothing. */
    CHANGED_MEANWHILE
}

/**
 * One item's detail (brief §6.3, ADR-0013): the derived stage and countdown come from `itemDetailOf` in
 * `:core:domain`, recomputed when its cooling-off ends or the date changes (ADR-0018), like the wishlist.
 */
@HiltViewModel(assistedFactory = ItemDetailViewModel.Factory::class)
class ItemDetailViewModel @AssistedInject constructor(
    @Assisted itemId: Long,
    private val items: ItemRepository,
    private val policies: PolicyRepository,
    private val clock: Clock
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(itemId: Long): ItemDetailViewModel
    }

    private val id = ItemId(itemId)
    private val messages = Channel<ItemDetailMessage>(Channel.BUFFERED)

    val uiState: StateFlow<ItemDetailUiState> =
        combine(items.observeById(id), items.observeReflection(id), ::Pair)
            .flatMapLatest { (item, reflection) ->
                flow {
                    if (item == null || item.status != ItemStatus.ACTIVE) {
                        emit(ItemDetailUiState(gone = true))
                        return@flow
                    }
                    while (true) {
                        val now = clock.instant()
                        emit(ItemDetailUiState(itemDetailOf(item, reflection, now, clock.zone)))
                        delay(untilWishlistChanges(listOf(item), now, clock.zone).toMillis().coerceAtLeast(1))
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ItemDetailUiState())

    /** One-shot messages for the snackbar. */
    val message: Flow<ItemDetailMessage> = messages.receiveAsFlow()

    /**
     * Turns the want into a need (ADR-0008): it skips the rest of its wait and reflection, and history keeps that it
     * was marked as a need. Does nothing for a need.
     */
    fun markAsNeed() {
        val item = uiState.value.detail?.item ?: return
        if (!item.canMarkAsNeed) return
        viewModelScope.launch {
            val rules = policies.policy.first().coolingOffRules
            // Only while a currency change is being written could they differ.
            if (rules.currency != item.price.currency) return@launch messages.send(ItemDetailMessage.CHANGED_MEANWHILE)
            val need = changeKind(item, ItemKind.NEED, clock.instant(), rules, clock.zone)
            if (!items.update(item, need)) messages.send(ItemDetailMessage.CHANGED_MEANWHILE)
        }
    }
}
