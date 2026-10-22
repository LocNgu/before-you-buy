package io.github.locngu.beforeyoubuy.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.locngu.beforeyoubuy.core.domain.home.HomeOverview
import io.github.locngu.beforeyoubuy.core.domain.home.homeOverviewOf
import io.github.locngu.beforeyoubuy.core.domain.item.ItemRepository
import io.github.locngu.beforeyoubuy.core.domain.wishlist.untilWishlistChanges
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/** What Home shows (brief §6.1). [overview] is null until the first load. */
data class HomeUiState(val overview: HomeOverview? = null)

/**
 * Home: what can be decided, what's being considered (brief §6.1). The sections are `homeOverviewOf` in
 * `:core:domain`; like the wishlist, this recomputes when a cooling-off or "Wait" ends or the date changes
 * (ADR-0018), not only when the database does.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(items: ItemRepository, private val clock: Clock) : ViewModel() {
    val uiState: StateFlow<HomeUiState> =
        combine(items.observeActive(), items.observeReflections(), ::Pair)
            .flatMapLatest { (active, reflections) ->
                flow {
                    while (true) {
                        val now = clock.instant()
                        emit(HomeUiState(homeOverviewOf(active, reflections, now, clock.zone)))
                        delay(untilWishlistChanges(active, now, clock.zone).toMillis().coerceAtLeast(1))
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
