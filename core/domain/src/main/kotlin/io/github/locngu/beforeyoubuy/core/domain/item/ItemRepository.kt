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
    /** Stores a new item and returns its id. The id of [item] is ignored; pass [ItemId.NEW]. */
    suspend fun add(item: Item): ItemId

    /**
     * Saves edits to a stored item (name, price, cooling-off end, kind, …). The status can't change here: that's
     * [recordDecision]. Returns false and writes nothing if the item is gone or its stored status differs from
     * [item]'s, e.g. "Let it go" from a notification ended it meanwhile; reload and show the current state.
     */
    suspend fun update(item: Item): Boolean

    /** Active items, oldest first. */
    fun observeActive(): Flow<List<Item>>

    /** The item, or null once it doesn't exist. */
    fun observeById(id: ItemId): Flow<Item?>

    /**
     * Items with [status] (`PURCHASED` or `REJECTED`, brief §6.6) together with the decision that ended them,
     * most recently decided first.
     */
    fun observeHistory(status: ItemStatus): Flow<List<HistoryEntry>>

    /** The item's reflection answers, or null if none were given yet (ADR-0009). */
    fun observeReflection(id: ItemId): Flow<Reflection?>

    /** Every decision about the item, oldest first. Decisions are only ever appended. */
    fun observeDecisions(id: ItemId): Flow<List<Decision>>

    /** Replaces the item's reflection answers; reflection is filled in a bit at a time. */
    suspend fun setReflection(id: ItemId, reflection: Reflection)

    /**
     * Appends [decision] and stores [item], the item's new state from
     * [io.github.locngu.beforeyoubuy.core.domain.lifecycle.applyDecision], in one transaction: both are written or
     * neither is. Returns false and writes nothing if the stored item is gone or no longer active, so an item is
     * never ended twice (e.g. "Let it go" in a notification and "Buy" in the app at the same moment).
     */
    suspend fun recordDecision(item: Item, decision: Decision): Boolean
}

/** A purchased or rejected [item] with the [decision] that ended it (brief §6.6 History). */
data class HistoryEntry(val item: Item, val decision: Decision)
