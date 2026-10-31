package io.github.locngu.beforeyoubuy.feature.wishlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemImageStore
import io.github.locngu.beforeyoubuy.core.domain.item.ItemRepository
import io.github.locngu.beforeyoubuy.core.domain.item.Reflection
import io.github.locngu.beforeyoubuy.core.domain.pool.PoolRepository
import io.github.locngu.beforeyoubuy.core.domain.pool.Savings
import io.github.locngu.beforeyoubuy.core.domain.priorities.DEFAULT_PRIORITY_SLOTS
import io.github.locngu.beforeyoubuy.core.domain.priorities.prioritiesOf
import io.github.locngu.beforeyoubuy.core.domain.settings.PolicyRepository
import io.github.locngu.beforeyoubuy.core.domain.wishlist.Priority
import io.github.locngu.beforeyoubuy.core.domain.wishlist.WishlistGroup
import io.github.locngu.beforeyoubuy.core.domain.wishlist.untilWishlistChanges
import io.github.locngu.beforeyoubuy.core.domain.wishlist.wishlistOf
import io.github.locngu.beforeyoubuy.core.domain.wishlist.wishlistSectionsOf
import io.github.locngu.beforeyoubuy.feature.money.observeSavings
import io.github.locngu.beforeyoubuy.feature.priorities.PriorityEvent
import io.github.locngu.beforeyoubuy.feature.priorities.makePriority
import java.io.File
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the wishlist shows (brief §4.7, §6.3). [groups] is null until the first load; it holds the items that aren't
 * [priorities], which come first by rank. Nothing in either: the empty state. [photos] has the photo file of each item
 * that has one, for its thumbnail. [prioritySlots] and [active] are what the slots dialog needs.
 */
data class WishlistUiState(
    val groups: List<WishlistGroup>? = null,
    val photos: Map<ItemId, File> = emptyMap(),
    val priorities: List<Priority> = emptyList(),
    val prioritySlots: Int = DEFAULT_PRIORITY_SLOTS,
    val active: List<Item> = emptyList()
)

/**
 * The wishlist: the priorities, then the other active items grouped by derived stage (brief §4.7, §6.3). The grouping
 * is `wishlistOf` / `wishlistSectionsOf` in `:core:domain`; this only feeds them items, reflections and the time, and
 * recomputes when a cooling-off ends or the date changes (ADR-0018), not only when the database does.
 */
@HiltViewModel
class WishlistViewModel @Inject constructor(
    private val items: ItemRepository,
    pool: PoolRepository,
    private val policies: PolicyRepository,
    private val images: ItemImageStore,
    private val clock: Clock
) : ViewModel() {
    private val events = Channel<PriorityEvent>(Channel.BUFFERED)

    val uiState: StateFlow<WishlistUiState> =
        combine(items.observeActive(), items.observeReflections(), observeSavings(items, pool), policies.policy) {
                active,
                reflections,
                savings,
                policy
            ->
            Loaded(active, reflections, savings, policy.prioritySlots)
        }
            .flatMapLatest { (active, reflections, savings, slots) ->
                val photos = active.mapNotNull { item ->
                    item.imageRef?.let(images::fileFor)?.let { item.id to it }
                }.toMap()
                flow {
                    while (true) {
                        val now = clock.instant()
                        val sections = wishlistSectionsOf(wishlistOf(active, reflections, savings, now, clock.zone))
                        emit(WishlistUiState(sections.groups, photos, sections.priorities, slots, active))
                        delay(untilWishlistChanges(active, now, clock.zone).toMillis().coerceAtLeast(1))
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WishlistUiState())

    /** What "Make priority" did: a message, or [PriorityEvent.Compare] to open the head-to-head. */
    val priorityEvents: Flow<PriorityEvent> = events.receiveAsFlow()

    // One priority write at a time: a double tap would otherwise find its own change and report a conflict.
    private var writingPriorities = false

    /** "Make priority" on a row (brief §4.7). */
    fun makePriority(id: ItemId) = writePriorities { makePriority(id, items, policies, clock) }

    /**
     * The slots dialog's Save: fewer slots send the lowest-ranked back, which the dialog listed from [uiState]
     * (ADR-0047). If the priorities changed since, nothing is written and the snackbar says so.
     */
    fun changePrioritySlots(slots: Int) {
        val shown = prioritiesOf(uiState.value.active).map { it.id }
        writePriorities {
            val changed = policies.changePrioritySlots(slots, shown, clock.instant(), clock.zone)
            if (changed) null else PriorityEvent.ChangedMeanwhile
        }
    }

    private fun writePriorities(write: suspend () -> PriorityEvent?) {
        if (writingPriorities) return
        writingPriorities = true
        viewModelScope.launch {
            try {
                write()?.let { events.send(it) }
            } finally {
                writingPriorities = false
            }
        }
    }

    private data class Loaded(
        val active: List<Item>,
        val reflections: Map<ItemId, Reflection>,
        val savings: Savings,
        val slots: Int
    )
}
