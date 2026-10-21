package io.github.locngu.beforeyoubuy.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.locngu.beforeyoubuy.core.domain.history.BoughtEntry
import io.github.locngu.beforeyoubuy.core.domain.history.DecidedAgainst
import io.github.locngu.beforeyoubuy.core.domain.history.HistoryPeriod
import io.github.locngu.beforeyoubuy.core.domain.history.allInCurrency
import io.github.locngu.beforeyoubuy.core.domain.history.boughtOf
import io.github.locngu.beforeyoubuy.core.domain.history.decidedAgainstOf
import io.github.locngu.beforeyoubuy.core.domain.history.untilHistoryChanges
import io.github.locngu.beforeyoubuy.core.domain.item.ItemRepository
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.settings.PolicyRepository
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/** The two lists of history (brief §6.6). */
enum class HistoryTab { BOUGHT, DECIDED_AGAINST }

/**
 * What history shows: the selected [tab] and [period], and both lists for that period. [bought] and [decidedAgainst]
 * are null until the first load; empty means nothing in the period.
 */
data class HistoryUiState(
    val tab: HistoryTab = HistoryTab.BOUGHT,
    val period: HistoryPeriod = HistoryPeriod.ALL,
    val bought: List<BoughtEntry>? = null,
    val decidedAgainst: DecidedAgainst? = null
)

/**
 * History (brief §6.6, issue #19): "Bought" and "Decided against" with a plain total, filtered by period. The lists
 * and the total come from `:core:domain` `history/`; this feeds them the stored history, the app's currency and the
 * time, and recomputes at midnight, when "this month" or "this year" may change (ADR-0018).
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    items: ItemRepository,
    policies: PolicyRepository,
    private val clock: Clock
) : ViewModel() {
    private val tab = MutableStateFlow(HistoryTab.BOUGHT)
    private val period = MutableStateFlow(HistoryPeriod.ALL)

    val uiState: StateFlow<HistoryUiState> =
        combine(
            items.observeHistory(ItemStatus.PURCHASED),
            items.observeHistory(ItemStatus.REJECTED),
            policies.policy
        ) { bought, rejected, policy -> Triple(bought, rejected, policy.currency) }
            // History and policy are read apart: during a currency change one may be read before the other has
            // been relabelled. Wait for the read that matches instead of totalling two currencies (ADR-0033).
            .filter { (bought, rejected, currency) -> allInCurrency(bought + rejected, currency) }
            .combine(period, ::Pair)
            .flatMapLatest { (history, selected) ->
                val (bought, rejected, currency) = history
                flow {
                    while (true) {
                        val now = clock.instant()
                        emit(
                            HistoryUiState(
                                period = selected,
                                bought = boughtOf(bought, currency, selected, now, clock.zone),
                                decidedAgainst = decidedAgainstOf(rejected, currency, selected, now, clock.zone)
                            )
                        )
                        delay(untilHistoryChanges(now, clock.zone).toMillis().coerceAtLeast(1))
                    }
                }
            }
            // Switching tabs only changes what's shown; both lists are already computed.
            .combine(tab) { state, selected -> state.copy(tab = selected) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun selectTab(tab: HistoryTab) {
        this.tab.value = tab
    }

    fun selectPeriod(period: HistoryPeriod) {
        this.period.value = period
    }
}
