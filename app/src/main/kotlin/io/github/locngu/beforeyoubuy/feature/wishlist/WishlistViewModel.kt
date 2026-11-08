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
import io.github.locngu.beforeyoubuy.core.domain.wishlist.Wishlist
import io.github.locngu.beforeyoubuy.core.domain.wishlist.WishlistSort
import io.github.locngu.beforeyoubuy.core.domain.wishlist.distinctCategories
import io.github.locngu.beforeyoubuy.core.domain.wishlist.untilWishlistChanges
import io.github.locngu.beforeyoubuy.core.domain.wishlist.wishlistOf
import io.github.locngu.beforeyoubuy.feature.money.observeSavings
import io.github.locngu.beforeyoubuy.feature.priorities.PriorityEvent
import io.github.locngu.beforeyoubuy.feature.priorities.makePriority
import java.io.File
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the wishlist shows (brief §4.7, §6.3, ADR-0049): the priorities, the needs and the parked wants ([Wishlist]),
 * null until the first load. Nothing at all: the empty state. [photos] has the photo file of each item that has one,
 * for its thumbnail. [prioritySlots] and [active] are what the slots dialog needs. [sort] and [category] are the
 * chosen order of the needs and the wishlist and the category filter (#83); [category] is null for "All".
 */
data class WishlistUiState(
    val wishlist: Wishlist? = null,
    val photos: Map<ItemId, File> = emptyMap(),
    val prioritySlots: Int = DEFAULT_PRIORITY_SLOTS,
    val active: List<Item> = emptyList(),
    val sort: WishlistSort = WishlistSort.DEFAULT,
    val category: String? = null
)

/**
 * The wishlist: the priorities, the needs, then the wants without a slot (brief §4.7, §6.3, ADR-0049). The sections
 * are `wishlistOf` in `:core:domain`; this only feeds it items, reflections and the time, and recomputes when a
 * cooling-off ends or the date changes (ADR-0018), not only when the database does.
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

    // Not remembered: back to the defaults whenever the wishlist is opened again (#83, ADR-0028).
    private val sort = MutableStateFlow(WishlistSort.DEFAULT)
    private val category = MutableStateFlow<String?>(null)

    val uiState: StateFlow<WishlistUiState> =
        combine(
            combine(items.observeActive(), items.observeReflections(), observeSavings(items, pool), policies.policy) {
                    active,
                    reflections,
                    savings,
                    policy
                ->
                Loaded(active, reflections, savings, policy.prioritySlots)
            },
            sort,
            category
        ) { loaded, sort, category -> Triple(loaded, sort, shownCategory(loaded.active, category)) }
            .flatMapLatest { (loaded, sort, category) ->
                val (active, reflections, savings, slots) = loaded
                val photos = active.mapNotNull { item ->
                    item.imageRef?.let(images::fileFor)?.let { item.id to it }
                }.toMap()
                flow {
                    while (true) {
                        val now = clock.instant()
                        val wishlist = wishlistOf(active, reflections, savings, now, clock.zone, sort, category)
                        emit(WishlistUiState(wishlist, photos, slots, active, sort, category))
                        delay(untilWishlistChanges(active, now, clock.zone).toMillis().coerceAtLeast(1))
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WishlistUiState())

    /** The sort menu's choice. */
    fun sortBy(sort: WishlistSort) {
        this.sort.value = sort
    }

    /** A category chip's choice; null is "All". */
    fun filterByCategory(category: String?) {
        this.category.value = category
    }

    /** Back to the defaults, when the wishlist is left. */
    fun resetView() {
        sort.value = WishlistSort.DEFAULT
        category.value = null
    }

    // A chosen category that's gone (its last items ended) or too few left to filter by shows everything.
    private fun shownCategory(active: List<Item>, chosen: String?): String? {
        val all = distinctCategories(active.map { it.category })
        return chosen?.takeIf { c -> all.size >= 2 && all.any { it.equals(c.trim(), ignoreCase = true) } }
    }

    /** What "Make it a priority" did: a message, or [PriorityEvent.Compare] to open the head-to-head. */
    val priorityEvents: Flow<PriorityEvent> = events.receiveAsFlow()

    // One priority write at a time: a double tap would otherwise find its own change and report a conflict.
    private var writingPriorities = false

    /** "Make it a priority" on a row (brief §4.7). */
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
