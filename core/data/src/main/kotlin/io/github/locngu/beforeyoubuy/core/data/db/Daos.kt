package io.github.locngu.beforeyoubuy.core.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
internal interface ItemDao {
    /** Inserts [item] with a new id (pass id 0) and returns that id. */
    @Insert
    suspend fun insert(item: ItemEntity): Long

    /** Returns the number of rows changed: 0 if the item doesn't exist. */
    @Update
    suspend fun update(item: ItemEntity): Int

    /** The stored status as written, or null if the item doesn't exist. */
    @Query("SELECT status FROM items WHERE id = :id")
    suspend fun statusOf(id: Long): String?

    @Query("SELECT * FROM items WHERE status = :status ORDER BY added_at, id")
    fun observeByStatus(status: String): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE id = :id")
    fun observeById(id: Long): Flow<ItemEntity?>

    /**
     * Items with [status] and the decision that ended them: the last one appended. Not the latest `decided_at`, since
     * a purchase may be dated back before an earlier "Wait". Newest `decided_at` first.
     */
    @Query(
        """
        SELECT items.*,
            d.id AS decision_id, d.item_id AS decision_item_id, d.type AS decision_type,
            d.decided_at AS decision_decided_at, d.source AS decision_source,
            d.actual_price_minor AS decision_actual_price_minor,
            d.actual_price_currency AS decision_actual_price_currency,
            d.early_reason AS decision_early_reason, d.wait_until AS decision_wait_until
        FROM items
        JOIN decisions d ON d.id = (SELECT MAX(id) FROM decisions WHERE item_id = items.id)
        WHERE items.status = :status
        ORDER BY d.decided_at DESC, d.id DESC
        """
    )
    fun observeHistory(status: String): Flow<List<HistoryRow>>
}

@Dao
internal interface ReflectionDao {
    @Upsert
    suspend fun upsert(reflection: ReflectionEntity)

    @Query("SELECT * FROM reflections WHERE item_id = :itemId")
    fun observe(itemId: Long): Flow<ReflectionEntity?>
}

/** Append-only: no update or delete (decisions go only when their item is deleted). */
@Dao
internal interface DecisionDao {
    @Insert
    suspend fun insert(decision: DecisionEntity): Long

    @Query("SELECT * FROM decisions WHERE item_id = :itemId ORDER BY id")
    fun observeForItem(itemId: Long): Flow<List<DecisionEntity>>
}

internal data class HistoryRow(
    @Embedded val item: ItemEntity,
    @Embedded(prefix = "decision_") val decision: DecisionEntity
)
