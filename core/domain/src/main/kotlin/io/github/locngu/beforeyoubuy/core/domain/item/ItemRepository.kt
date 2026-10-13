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
}

/** A purchased or rejected [item] with the [decision] that ended it (brief §6.6 History). */
data class HistoryEntry(val item: Item, val decision: Decision)
