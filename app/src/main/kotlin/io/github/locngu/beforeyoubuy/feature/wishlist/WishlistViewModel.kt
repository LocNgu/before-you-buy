package io.github.locngu.beforeyoubuy.feature.wishlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.locngu.beforeyoubuy.core.domain.item.ItemRepository
import io.github.locngu.beforeyoubuy.core.domain.wishlist.WishlistGroup
import io.github.locngu.beforeyoubuy.core.domain.wishlist.untilWishlistChanges
import io.github.locngu.beforeyoubuy.core.domain.wishlist.wishlistOf
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** What the wishlist shows (brief §6.3). [groups] is null until the first load; empty means nothing to list. */
data class WishlistUiState(val groups: List<WishlistGroup>? = null)

/**
 * The wishlist: active items grouped by derived stage (brief §6.3). The grouping is `wishlistOf` in `:core:domain`;
 * this only feeds it items, reflections and the time, and recomputes when a cooling-off ends or the date changes
 * (ADR-0018), not only when the database does.
 */
@HiltViewModel
class WishlistViewModel @Inject constructor(items: ItemRepository, private val clock: Clock) : ViewModel() {
    val uiState: StateFlow<WishlistUiState> =
        combine(items.observeActive(), items.observeReflections(), ::Pair)
            .flatMapLatest { (active, reflections) ->
                flow {
                    while (true) {
                        val now = clock.instant()
                        emit(wishlistOf(active, reflections, now, clock.zone))
                        delay(untilWishlistChanges(active, now, clock.zone).toMillis().coerceAtLeast(1))
                    }
                }
            }.map { WishlistUiState(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WishlistUiState())
}
