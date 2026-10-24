package io.github.locngu.beforeyoubuy.feature.wishlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemImageStore
import io.github.locngu.beforeyoubuy.core.domain.item.ItemRepository
import io.github.locngu.beforeyoubuy.core.domain.pool.PoolRepository
import io.github.locngu.beforeyoubuy.core.domain.wishlist.WishlistGroup
import io.github.locngu.beforeyoubuy.core.domain.wishlist.untilWishlistChanges
import io.github.locngu.beforeyoubuy.core.domain.wishlist.wishlistOf
import io.github.locngu.beforeyoubuy.feature.money.observeSavings
import java.io.File
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * What the wishlist shows (brief §6.3). [groups] is null until the first load; empty means nothing to list.
 * [photos] has the photo file of each item that has one, for its thumbnail.
 */
data class WishlistUiState(val groups: List<WishlistGroup>? = null, val photos: Map<ItemId, File> = emptyMap())

/**
 * The wishlist: active items grouped by derived stage (brief §6.3). The grouping is `wishlistOf` in `:core:domain`;
 * this only feeds it items, reflections and the time, and recomputes when a cooling-off ends or the date changes
 * (ADR-0018), not only when the database does.
 */
@HiltViewModel
class WishlistViewModel @Inject constructor(
    items: ItemRepository,
    pool: PoolRepository,
    private val images: ItemImageStore,
    private val clock: Clock
) : ViewModel() {
    val uiState: StateFlow<WishlistUiState> =
        combine(items.observeActive(), items.observeReflections(), observeSavings(items, pool), ::Triple)
            .flatMapLatest { (active, reflections, savings) ->
                val photos = active.mapNotNull { item ->
                    item.imageRef?.let(images::fileFor)?.let { item.id to it }
                }.toMap()
                flow {
                    while (true) {
                        val now = clock.instant()
                        emit(WishlistUiState(wishlistOf(active, reflections, savings, now, clock.zone), photos))
                        delay(untilWishlistChanges(active, now, clock.zone).toMillis().coerceAtLeast(1))
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WishlistUiState())
}
