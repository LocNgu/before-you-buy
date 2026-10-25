package io.github.locngu.beforeyoubuy.feature.priorities

import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemRepository
import io.github.locngu.beforeyoubuy.core.domain.priorities.Promotion
import io.github.locngu.beforeyoubuy.core.domain.priorities.prioritiesOf
import io.github.locngu.beforeyoubuy.core.domain.priorities.promote
import io.github.locngu.beforeyoubuy.core.domain.priorities.rankingWithout
import io.github.locngu.beforeyoubuy.core.domain.settings.PolicyRepository
import kotlinx.coroutines.flow.first

/** What "Make priority" or "Remove from priorities" did, for the screen to say or do (brief §4.7). */
sealed interface PriorityEvent {
    /** A slot was free: [name] is now priority #[rank]. */
    data class Promoted(val name: String, val rank: Int) : PriorityEvent

    /** [name] is no longer a priority. */
    data class Removed(val name: String) : PriorityEvent

    /** Every slot is taken: open the head-to-head for [itemId] (ADR-0011). */
    data class Compare(val itemId: ItemId) : PriorityEvent

    /** The item or the priorities changed meanwhile; nothing was written. */
    data object ChangedMeanwhile : PriorityEvent
}

/**
 * "Make priority" for [id], from item detail and the wishlist: with a free slot it's stored at once; with full slots
 * the caller opens the head-to-head, the only way into full slots. The rule is `promote` in `:core:domain`;
 * [ItemRepository.setPriorities] checks it again against the stored priorities.
 */
suspend fun makePriority(id: ItemId, items: ItemRepository, policies: PolicyRepository): PriorityEvent {
    val active = items.observeActive().first()
    val item = active.find { it.id == id } ?: return PriorityEvent.ChangedMeanwhile
    return when (val promotion = promote(item, active, policies.policy.first().prioritySlots)) {
        is Promotion.Promoted ->
            if (items.setPriorities(prioritiesOf(active).map { it.id }, promotion.ranking)) {
                PriorityEvent.Promoted(item.name, promotion.rank)
            } else {
                PriorityEvent.ChangedMeanwhile
            }

        is Promotion.Compare -> PriorityEvent.Compare(id)

        Promotion.NotEligible -> PriorityEvent.ChangedMeanwhile
    }
}

/** "Remove from priorities" for [id]: the ones below move up (ADR-0047). */
suspend fun removePriority(id: ItemId, items: ItemRepository): PriorityEvent {
    val active = items.observeActive().first()
    val expected = prioritiesOf(active).map { it.id }
    val item = active.find { it.id == id }?.takeIf { it.id in expected } ?: return PriorityEvent.ChangedMeanwhile
    return if (items.setPriorities(expected, rankingWithout(active, id))) {
        PriorityEvent.Removed(item.name)
    } else {
        PriorityEvent.ChangedMeanwhile
    }
}
