package io.github.locngu.beforeyoubuy.core.domain.item

import kotlinx.coroutines.flow.Flow

/**
 * Stores items with their reflection and decision history (brief §4.1, §4.3, §5). Implemented in `:core:data`.
 *
 * Only facts are stored; the stage is derived with
 * [io.github.locngu.beforeyoubuy.core.domain.lifecycle.stageOf] (ADR-0007). Instants are stored to the millisecond,
 * so a read-back instant is truncated to milliseconds.
 */
interface ItemRepository {
    /** Stores a new, active item and returns its id. The id of [item] is ignored; pass [ItemId.NEW]. */
    suspend fun add(item: Item): ItemId

    /**
     * Replaces [current], the item as the caller read it, with [edited] (name, price, cooling-off end, kind, …).
     * The id and status can't change here: status changes go through [recordDecision]; anything else throws.
     *
     * Writes only if the stored item still equals [current]. Returns false and writes nothing if it changed
     * meanwhile, e.g. "Let it go" or "Wait" from a notification, so an edit never undoes a decision. Reload and
     * show the current state.
     */
    suspend fun update(current: Item, edited: Item): Boolean

    /** Active items, oldest first. */
    fun observeActive(): Flow<List<Item>>

    /** The item, or null once it doesn't exist. */
    fun observeById(id: ItemId): Flow<Item?>

    /**
     * Items with [status] (`PURCHASED` or `REJECTED`, brief §6.6) together with the decision that ended them,
     * most recently decided first. `ACTIVE` throws: active items have no ending decision.
     */
    fun observeHistory(status: ItemStatus): Flow<List<HistoryEntry>>

    /** The item's reflection answers, or null if none were given yet (ADR-0009). */
    fun observeReflection(id: ItemId): Flow<Reflection?>

    /** The reflection answers of every item that has some, by item id; the wishlist derives all stages from it. */
    fun observeReflections(): Flow<Map<ItemId, Reflection>>

    /**
     * The [Item.imageRef] of every stored item, active or ended: the photos that must be kept
     * ([ItemImageStore.deleteUnreferenced]).
     */
    suspend fun imageRefs(): Set<String>

    /** Every decision about the item, oldest first. Decisions are only ever appended. */
    fun observeDecisions(id: ItemId): Flow<List<Decision>>

    /**
     * Replaces the item's reflection answers; reflection is filled in a bit at a time. Returns false and writes
     * nothing if the item no longer exists.
     */
    suspend fun setReflection(id: ItemId, reflection: Reflection): Boolean

    /**
     * Appends [decision] about [current], the active item as the caller read it, and stores [result], its new state
     * from [io.github.locngu.beforeyoubuy.core.domain.lifecycle.applyDecision], in one transaction: both are written
     * or neither is.
     *
     * Writes only if the stored item still equals [current], the state the decision was checked against. Returns
     * false and writes nothing if it changed meanwhile: ended (e.g. "Let it go" in a notification and "Buy" in the
     * app at the same moment), edited, or gone. A [current] that isn't active, or a [result] with another id, throws.
     */
    suspend fun recordDecision(current: Item, decision: Decision, result: Item): Boolean

    /**
     * Makes [ranking] the priorities, rank 1 first (brief §4.7): the n-th item gets slot n, every other active item
     * none ([io.github.locngu.beforeyoubuy.core.domain.priorities.slotChanges]). Used by "Make priority", the
     * head-to-head and "Remove from priorities" (ADR-0047).
     *
     * Writes only if the stored priorities are still [expected] (ids, rank 1 first), every id in [ranking] is an
     * active want and [ranking] fits the priority slots. Returns false and writes nothing otherwise, e.g. a priority
     * was bought meanwhile or the slots changed.
     *
     * [update] and [recordDecision] keep the ranks without gaps: when a priority ends or becomes a need, the ones
     * below it move up in the same transaction
     * ([io.github.locngu.beforeyoubuy.core.domain.priorities.compactedSlots]).
     */
    suspend fun setPriorities(expected: List<ItemId>, ranking: List<ItemId>): Boolean

    /**
     * Deletes [current], a purchased or rejected item as the caller read it, with its reflection answers and
     * decisions: history entries are only deleted on the user's explicit request (brief §5, issue #19). Its photo
     * stays on disk until the sweep at app start finds no item referencing it (ADR-0038).
     *
     * Writes only if the stored item still equals [current]; returns false and deletes nothing if it changed or is
     * gone. An active [current] throws: active items leave the wishlist through a decision.
     */
    suspend fun delete(current: Item): Boolean
}

/** A purchased or rejected [item] with the [decision] that ended it (brief §6.6 History). */
data class HistoryEntry(val item: Item, val decision: Decision)
