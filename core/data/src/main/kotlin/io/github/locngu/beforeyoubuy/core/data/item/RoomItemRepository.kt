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

    override suspend fun add(item: Item): ItemId = ItemId(items.insert(item.copy(id = ItemId.NEW).toEntity()))

    override suspend fun update(item: Item): Boolean = database.withTransaction {
        // Compares the stored text, so a status this version can't read is never overwritten either.
        if (items.statusOf(item.id.value) != item.status.name) return@withTransaction false
        items.update(item.toEntity()) == 1
    }

    override fun observeActive(): Flow<List<Item>> =
        items.observeByStatus(ItemStatus.ACTIVE.name).map { rows -> rows.map { it.toDomain() } }

    override fun observeById(id: ItemId): Flow<Item?> = items.observeById(id.value).map { it?.toDomain() }

    override fun observeHistory(status: ItemStatus): Flow<List<HistoryEntry>> =
        items.observeHistory(status.name).map { rows ->
            rows.mapNotNull { row -> row.decision.toDomain()?.let { HistoryEntry(row.item.toDomain(), it) } }
        }

    override fun observeReflection(id: ItemId): Flow<Reflection?> = reflections.observe(id.value).map { it?.toDomain() }

    override fun observeDecisions(id: ItemId): Flow<List<Decision>> =
        decisions.observeForItem(id.value).map { rows -> rows.mapNotNull { it.toDomain() } }

    override suspend fun setReflection(id: ItemId, reflection: Reflection) = reflections.upsert(reflection.toEntity(id))

    override suspend fun recordDecision(item: Item, decision: Decision): Boolean = database.withTransaction {
        if (items.statusOf(item.id.value) != ItemStatus.ACTIVE.name) return@withTransaction false
        decisions.insert(decision.toEntity(item.id))
        // The row exists (checked above, same transaction), so this writes exactly one row or throws and rolls back.
        items.update(item.toEntity())
        true
    }
}
