package io.github.locngu.beforeyoubuy.feature.priorities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemRepository
import io.github.locngu.beforeyoubuy.core.domain.item.Reflection
import io.github.locngu.beforeyoubuy.core.domain.pool.PoolRepository
import io.github.locngu.beforeyoubuy.core.domain.pool.Savings
import io.github.locngu.beforeyoubuy.core.domain.pool.SavingsProgress
import io.github.locngu.beforeyoubuy.core.domain.pool.progressOf
import io.github.locngu.beforeyoubuy.core.domain.priorities.HeadToHead
import io.github.locngu.beforeyoubuy.core.domain.priorities.OutcomeLine
import io.github.locngu.beforeyoubuy.core.domain.priorities.Promotion
import io.github.locngu.beforeyoubuy.core.domain.priorities.prioritiesOf
import io.github.locngu.beforeyoubuy.core.domain.priorities.promote
import io.github.locngu.beforeyoubuy.core.domain.settings.PolicyRepository
import io.github.locngu.beforeyoubuy.feature.money.observeSavings
import java.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * What the head-to-head shows (brief §4.7): one [comparison] at a time, then the [summary]. Nothing (all null, not
 * [gone]) while loading or storing the result.
 *
 * @property gone the challenger can't become a priority (ended, a need, already one).
 * @property changedMeanwhile the priorities changed while comparing, so the result wasn't stored.
 */
data class HeadToHeadUiState(
    val comparison: Comparison? = null,
    val summary: List<OutcomeLine>? = null,
    val gone: Boolean = false,
    val changedMeanwhile: Boolean = false
)

/** The two items side by side: the [challenger] (left) and the priority it faces now (right). */
data class Comparison(val challenger: ComparisonCard, val opponent: ComparisonCard)

/**
 * One side of a comparison: the item, its [rank] (null for the challenger), its reasons and what is set aside for it
 * ([savings], null without fun money). No photo: reasons, not images (ADR-0013, ADR-0047).
 */
data class ComparisonCard(val item: Item, val rank: Int?, val reflection: Reflection?, val savings: SavingsProgress?)

/**
 * "Which would you rather have?" for one challenger (brief §4.7, ADR-0011). The comparisons are `HeadToHead` in
 * `:core:domain`, against the priorities as they were when the screen opened; the result is stored once at the end,
 * compare-and-set ([ItemRepository.setPriorities]), so leaving halfway changes nothing.
 */
@HiltViewModel(assistedFactory = HeadToHeadViewModel.Factory::class)
class HeadToHeadViewModel @AssistedInject constructor(
    @Assisted itemId: Long,
    private val items: ItemRepository,
    pool: PoolRepository,
    policies: PolicyRepository,
    private val clock: Clock
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(itemId: Long): HeadToHeadViewModel
    }

    private val id = ItemId(itemId)
    private val state = MutableStateFlow(HeadToHeadUiState())
    val uiState: StateFlow<HeadToHeadUiState> = state.asStateFlow()

    private var headToHead: HeadToHead? = null
    private var reflections: Map<ItemId, Reflection> = emptyMap()
    private var savings: Savings = Savings.NONE

    init {
        viewModelScope.launch {
            val active = items.observeActive().first()
            reflections = items.observeReflections().first()
            savings = observeSavings(items, pool).first()
            val challenger = active.find { it.id == id }
            val promotion = challenger?.let { promote(it, active, policies.policy.first().prioritySlots) }
            when (promotion) {
                null, Promotion.NotEligible -> state.value = HeadToHeadUiState(gone = true)

                // A slot became free since "Make priority": no comparison needed.
                is Promotion.Promoted -> store(
                    prioritiesOf(active).map { it.id },
                    promotion.ranking,
                    listOf(OutcomeLine(challenger, null, promotion.rank))
                )

                is Promotion.Compare -> show(promotion.headToHead)
            }
        }
    }

    /**
     * The user would rather have [winner], in the comparison against [opponent]: the priority the screen showed. A
     * tap meant for a comparison that's already answered (a double tap) is ignored.
     */
    fun choose(winner: ItemId, opponent: ItemId) {
        val current = headToHead ?: return
        if (current.opponent?.id != opponent) return
        val next = current.choose(winner)
        val outcome = next.outcome
        if (outcome == null) {
            show(next)
            return
        }
        headToHead = null
        state.value = HeadToHeadUiState()
        if (!outcome.changed) {
            state.value = HeadToHeadUiState(summary = outcome.lines)
            return
        }
        viewModelScope.launch { store(outcome.before, outcome.after, outcome.lines) }
    }

    private suspend fun store(expected: List<ItemId>, ranking: List<ItemId>, lines: List<OutcomeLine>) {
        state.value = if (items.setPriorities(expected, ranking, clock.instant(), clock.zone)) {
            HeadToHeadUiState(summary = lines)
        } else {
            HeadToHeadUiState(changedMeanwhile = true)
        }
    }

    private fun show(headToHead: HeadToHead) {
        this.headToHead = headToHead
        val opponent = checkNotNull(headToHead.opponent)
        state.value = HeadToHeadUiState(
            comparison = Comparison(card(headToHead.challenger, null), card(opponent, headToHead.opponentRank))
        )
    }

    private fun card(item: Item, rank: Int?) =
        ComparisonCard(item, rank, reflections[item.id], savings.progressOf(item).takeIf { savings.setUp })
}
