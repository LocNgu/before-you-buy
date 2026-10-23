package io.github.locngu.beforeyoubuy.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.locngu.beforeyoubuy.core.domain.home.HomeOverview
import io.github.locngu.beforeyoubuy.core.domain.home.homeOverviewOf
import io.github.locngu.beforeyoubuy.core.domain.item.ItemRepository
import io.github.locngu.beforeyoubuy.core.domain.pool.FunMoney
import io.github.locngu.beforeyoubuy.core.domain.pool.PoolRepository
import io.github.locngu.beforeyoubuy.core.domain.pool.funMoneyOf
import io.github.locngu.beforeyoubuy.core.domain.wishlist.untilWishlistChanges
import io.github.locngu.beforeyoubuy.feature.money.observeSavings
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/** What Home shows (brief §6.1). [overview] is null until the first load; [money] too, separately. */
data class HomeUiState(val overview: HomeOverview? = null, val money: FunMoney? = null)

/**
 * Home: what can be decided, what's being considered, how much fun money there is (brief §6.1). The sections are
 * `homeOverviewOf` and `funMoneyOf` in `:core:domain`; like the wishlist, this recomputes when a cooling-off or
 * "Wait" ends or the date changes (ADR-0018), not only when the database does.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(items: ItemRepository, pool: PoolRepository, private val clock: Clock) :
    ViewModel() {
    private val savings = observeSavings(items, pool)

    private val overview =
        combine(items.observeActive(), items.observeReflections(), savings, ::Triple)
            .flatMapLatest { (active, reflections, savings) ->
                flow {
                    while (true) {
                        val now = clock.instant()
                        emit(homeOverviewOf(active, reflections, savings, now, clock.zone))
                        delay(untilWishlistChanges(active, now, clock.zone).toMillis().coerceAtLeast(1))
                    }
                }
            }

    private val money = combine(pool.allowance, pool.entries, savings, ::funMoneyOf)

    val uiState: StateFlow<HomeUiState> =
        combine(overview.onStart<HomeOverview?> { emit(null) }, money.onStart<FunMoney?> { emit(null) }, ::HomeUiState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
