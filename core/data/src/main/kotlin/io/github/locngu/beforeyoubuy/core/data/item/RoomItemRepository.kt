package io.github.locngu.beforeyoubuy.core.data.item

import androidx.room.withTransaction
import io.github.locngu.beforeyoubuy.core.data.db.BeforeYouBuyDatabase
import io.github.locngu.beforeyoubuy.core.domain.item.Decision
import io.github.locngu.beforeyoubuy.core.domain.item.HistoryEntry
import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemRepository
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.item.Reflection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomItemRepository(private val database: BeforeYouBuyDatabase) : ItemRepository {
    private val items = database.itemDao()
    private val reflections = database.reflectionDao()
    private val decisions = database.decisionDao()

    override suspend fun add(item: Item): ItemId {
        require(item.status == ItemStatus.ACTIVE) { "New items are active, was ${item.status}" }
        return ItemId(items.insert(item.copy(id = ItemId.NEW).toEntity()))
    }

    override suspend fun update(current: Item, edited: Item): Boolean {
        require(edited.id == current.id) { "Can't change the id from ${current.id} to ${edited.id}" }
        require(edited.status == current.status) { "Status changes go through recordDecision" }
        return writeIfUnchanged(current) { items.update(edited.toEntity()) }
    }

    override fun observeActive(): Flow<List<Item>> =
        items.observeByStatus(ItemStatus.ACTIVE.name).map { rows -> rows.map { it.toDomain() } }

    override fun observeById(id: ItemId): Flow<Item?> = items.observeById(id.value).map { it?.toDomain() }

    override fun observeHistory(status: ItemStatus): Flow<List<HistoryEntry>> {
        require(status != ItemStatus.ACTIVE) { "History holds purchased and rejected items only" }
        return items.observeHistory(status.name).map { rows ->
            rows.mapNotNull { row -> row.decision.toDomain()?.let { HistoryEntry(row.item.toDomain(), it) } }
        }
    }

    override fun observeReflection(id: ItemId): Flow<Reflection?> = reflections.observe(id.value).map { it?.toDomain() }

    override fun observeDecisions(id: ItemId): Flow<List<Decision>> =
        decisions.observeForItem(id.value).map { rows -> rows.mapNotNull { it.toDomain() } }

    override suspend fun setReflection(id: ItemId, reflection: Reflection) = reflections.upsert(reflection.toEntity(id))

    override suspend fun recordDecision(current: Item, decision: Decision, result: Item): Boolean {
        require(current.status == ItemStatus.ACTIVE) { "Only active items can be decided, was ${current.status}" }
        require(result.id == current.id) { "The result is item ${result.id}, not ${current.id}" }
        return writeIfUnchanged(current) {
            decisions.insert(decision.toEntity(current.id))
            items.update(result.toEntity())
        }
    }

    /**
     * Runs [write] in a transaction if the stored row still equals [current], so nothing computed from a stale read
     * overwrites a newer change (ADR-0032). Comparing the stored row, not the mapped item, also keeps values this
     * version can't read (an unknown status) from being overwritten. Inside the transaction, [write] either succeeds
     * completely or throws and rolls back.
     */
    private suspend fun writeIfUnchanged(current: Item, write: suspend () -> Unit): Boolean = database.withTransaction {
        if (items.getById(current.id.value) != current.toEntity()) return@withTransaction false
        write()
        true
    }
}
