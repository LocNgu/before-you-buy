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

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getById(id: Long): ItemEntity?

    @Query("SELECT * FROM items WHERE status = :status ORDER BY added_at, id")
    fun observeByStatus(status: String): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE status = :status ORDER BY added_at, id")
    suspend fun getByStatus(status: String): List<ItemEntity>

    @Query("SELECT * FROM items")
    suspend fun getAll(): List<ItemEntity>

    /** Changes only the cooling-off end, so columns this version can't read are kept as stored. */
    @Query("UPDATE items SET cooling_ends_at = :coolingEndsAt WHERE id = :id")
    suspend fun setCoolingEnd(id: Long, coolingEndsAt: Long)

    /** Changes only the price, for a currency relabel (ADR-0033). */
    @Query("UPDATE items SET price_minor = :priceMinor, currency = :currency WHERE id = :id")
    suspend fun setPrice(id: Long, priceMinor: Long, currency: String)

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

    @Query("SELECT * FROM reflections")
    fun observeAll(): Flow<List<ReflectionEntity>>
}

/**
 * Append-only: no update or delete (decisions go only when their item is deleted). The one exception is
 * [setActualPrice], which relabels the currency of what was paid when the app's currency changes (ADR-0033); what was
 * decided never changes.
 */
@Dao
internal interface DecisionDao {
    @Insert
    suspend fun insert(decision: DecisionEntity): Long

    @Query("SELECT * FROM decisions WHERE item_id = :itemId ORDER BY id")
    fun observeForItem(itemId: Long): Flow<List<DecisionEntity>>

    @Query("SELECT * FROM decisions WHERE actual_price_minor IS NOT NULL AND actual_price_currency IS NOT NULL")
    suspend fun getWithActualPrice(): List<DecisionEntity>

    @Query("UPDATE decisions SET actual_price_minor = :minor, actual_price_currency = :currency WHERE id = :id")
    suspend fun setActualPrice(id: Long, minor: Long, currency: String)
}

/** The single policy row (id [PolicyEntity.ID]). [BeforeYouBuyDatabase] creates it on first open. */
@Dao
internal interface PolicyDao {
    @Query("SELECT * FROM policy WHERE id = ${PolicyEntity.ID}")
    fun observe(): Flow<PolicyEntity?>

    @Query("SELECT * FROM policy WHERE id = ${PolicyEntity.ID}")
    suspend fun get(): PolicyEntity?

    @Update
    suspend fun update(policy: PolicyEntity): Int
}

internal data class HistoryRow(
    @Embedded val item: ItemEntity,
    @Embedded(prefix = "decision_") val decision: DecisionEntity
)
