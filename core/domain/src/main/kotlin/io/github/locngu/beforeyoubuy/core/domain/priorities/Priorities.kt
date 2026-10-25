package io.github.locngu.beforeyoubuy.core.domain.priorities

import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus

/** The fewest priority slots the user can choose (brief §4.7, ADR-0011). */
const val MIN_PRIORITY_SLOTS = 1

/** The most priority slots the user can choose (brief §4.7, ADR-0011). */
const val MAX_PRIORITY_SLOTS = 5

/** Priority slots until the user chooses otherwise (brief §4.7, ADR-0011). */
const val DEFAULT_PRIORITY_SLOTS = 3

/** A stored slot count that doesn't fit 1–5 reads as the nearest that does (ADR-0032). */
fun clampPrioritySlots(slots: Int): Int = slots.coerceIn(MIN_PRIORITY_SLOTS, MAX_PRIORITY_SLOTS)

/**
 * The priorities among [items], rank 1 first: active wants with a [Item.prioritySlot] (brief §4.7). Only wants hold a
 * slot (ADR-0047); a need or an ended item with a stored slot isn't a priority. Equal slots, which no write produces,
 * go by `addedAt`, then id, so the order is always the same.
 */
fun prioritiesOf(items: List<Item>): List<Item> = items
    .filter { it.status == ItemStatus.ACTIVE && it.kind == ItemKind.WANT && it.prioritySlot != null }
    .sortedWith(compareBy({ it.prioritySlot }, { it.addedAt }, { it.id.value }))

/** Whether [this] can become a priority: an active want that isn't one yet (ADR-0047). */
val Item.canBePriority: Boolean
    get() = status == ItemStatus.ACTIVE && kind == ItemKind.WANT && prioritySlot == null

/**
 * The slot writes that make [ranking] the priorities among the [active] items: the n-th id gets slot n, every other
 * active item none. Only items whose slot changes are in the result (null = no slot).
 */
fun slotChanges(active: List<Item>, ranking: List<ItemId>): Map<ItemId, Int?> {
    require(ranking.distinct().size == ranking.size) { "An item can hold one slot only: $ranking" }
    val slots = ranking.withIndex().associate { (index, id) -> id to index + 1 }
    return active
        .filter { it.status == ItemStatus.ACTIVE }
        .mapNotNull { item -> slots[item.id].let { slot -> if (slot != item.prioritySlot) item.id to slot else null } }
        .toMap()
}

/**
 * Closes the gaps once a priority leaves (bought, decided against, made a need, removed): the remaining priorities
 * keep their order and move up to 1…n; any other active item that still has a slot loses it (brief §4.7).
 */
fun compactedSlots(active: List<Item>): Map<ItemId, Int?> = slotChanges(active, prioritiesOf(active).map { it.id })

/** The priorities that go back to the wishlist when the slots become [slots]: the lowest-ranked beyond it. */
fun demotedBySlots(active: List<Item>, slots: Int): List<Item> {
    require(slots in MIN_PRIORITY_SLOTS..MAX_PRIORITY_SLOTS) { "Priority slots are 1–5, was $slots" }
    return prioritiesOf(active).drop(slots)
}

/** The ranking after "Remove from priorities" on [item]: the others keep their order (ADR-0047). */
fun rankingWithout(active: List<Item>, item: ItemId): List<ItemId> = prioritiesOf(active).map { it.id } - item

/** What "Make priority" does with an item (brief §4.7). */
sealed interface Promotion {
    /** A slot is free: the item takes the lowest free [rank]; store [ranking]. */
    data class Promoted(val rank: Int, val ranking: List<ItemId>) : Promotion

    /** Every slot is taken: the item has to earn its place in a [headToHead] first (ADR-0011). */
    data class Compare(val headToHead: HeadToHead) : Promotion

    /** A need, an ended item or one that already is a priority. */
    data object NotEligible : Promotion
}

/**
 * "Make priority" for [item] among the [active] items with [slots] slots (brief §4.7): with a free slot it takes the
 * lowest free rank; with all of them taken, it has to win at least one comparison ([HeadToHead]), so a new want never
 * takes a full set of slots without one (ADR-0011).
 */
fun promote(item: Item, active: List<Item>, slots: Int): Promotion {
    if (!item.canBePriority) return Promotion.NotEligible
    val priorities = prioritiesOf(active).filter { it.id != item.id }
    return if (priorities.size < slots) {
        Promotion.Promoted(priorities.size + 1, priorities.map { it.id } + item.id)
    } else {
        Promotion.Compare(HeadToHead.start(item, priorities, slots))
    }
}
