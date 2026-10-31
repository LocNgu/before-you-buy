package io.github.locngu.beforeyoubuy.core.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
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

    /** Deletes the item; its reflection and decisions go with it (ON DELETE CASCADE). */
    @Query("DELETE FROM items WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getById(id: Long): ItemEntity?

    @Query("SELECT * FROM items WHERE status = :status ORDER BY added_at, id")
    fun observeByStatus(status: String): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE status = :status ORDER BY added_at, id")
    suspend fun getByStatus(status: String): List<ItemEntity>

    @Query("SELECT * FROM items")
    suspend fun getAll(): List<ItemEntity>

    @Query("SELECT DISTINCT image_ref FROM items WHERE image_ref IS NOT NULL")
    suspend fun getImageRefs(): List<String>

    /** Changes only the wait's columns (ADR-0050), so columns this version can't read are kept as stored. */
    @Query(
        "UPDATE items SET cooling_off_days = :coolingOffDays, waited_days = :waitedDays, " +
            "waiting_since = :waitingSince, cooling_ends_at = :coolingEndsAt WHERE id = :id"
    )
    suspend fun setWait(id: Long, coolingOffDays: Int, waitedDays: Int, waitingSince: Long?, coolingEndsAt: Long)

    /** Changes only the slot and the wait's columns, like [setWait]. */
    @Query(
        "UPDATE items SET priority_slot = :slot, waited_days = :waitedDays, waiting_since = :waitingSince, " +
            "cooling_ends_at = :coolingEndsAt WHERE id = :id"
    )
    suspend fun setSlotAndWait(id: Long, slot: Int?, waitedDays: Int, waitingSince: Long?, coolingEndsAt: Long)

    /** Changes only the price, for a currency relabel (ADR-0033). */
    @Query("UPDATE items SET price_minor = :priceMinor, currency = :currency WHERE id = :id")
    suspend fun setPrice(id: Long, priceMinor: Long, currency: String)

    /** Only the slot, so unreadable columns of the row survive (ADR-0032). */
    @Query("UPDATE items SET priority_slot = :slot WHERE id = :id")
    suspend fun setPrioritySlot(id: Long, slot: Int?)

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
            d.early_reason AS decision_early_reason, d.wait_until AS decision_wait_until,
            d.note AS decision_note
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

/** What was notified about each item (`NotificationLogRepository`). Rows go with their item (ON DELETE CASCADE). */
@Dao
internal interface NotificationDao {
    @Query("SELECT * FROM item_notifications")
    suspend fun getAll(): List<ItemNotificationsEntity>

    /** Replaces the item's row; writes nothing if the item doesn't exist (any more). */
    @Query(
        """
        INSERT OR REPLACE INTO item_notifications (item_id, waiting_period_over_at, reflection_nudged_at)
        SELECT :itemId, :waitingPeriodOverAt, :reflectionNudgedAt WHERE EXISTS (SELECT 1 FROM items WHERE id = :itemId)
        """
    )
    suspend fun setIfItemExists(itemId: Long, waitingPeriodOverAt: Long?, reflectionNudgedAt: Long?)
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

/**
 * The allowance row, the pool's ledger and the allocations (`PoolRepository`). Ledger and allocation rows are only
 * ever inserted, except the starting amount, which is a setting and replaced when edited.
 */
@Dao
internal interface PoolDao {
    @Query("SELECT * FROM allowance WHERE id = ${AllowanceEntity.ID}")
    fun observeAllowance(): Flow<AllowanceEntity?>

    @Query("SELECT * FROM allowance WHERE id = ${AllowanceEntity.ID}")
    suspend fun getAllowance(): AllowanceEntity?

    /** Returns -1 and writes nothing if the allowance exists already. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllowance(allowance: AllowanceEntity): Long

    @Update
    suspend fun updateAllowance(allowance: AllowanceEntity): Int

    @Query("SELECT * FROM pool_entries ORDER BY at, id")
    fun observeEntries(): Flow<List<PoolEntryEntity>>

    @Query("SELECT * FROM pool_entries")
    suspend fun getEntries(): List<PoolEntryEntity>

    /** The newest credited month (`2026-11`; ISO text sorts by date), or null if none is. */
    @Query("SELECT MAX(accrual_month) FROM pool_entries")
    suspend fun lastAccruedMonth(): String?

    /** Returns the new id, or -1 and writes nothing for a month that is already credited. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntry(entry: PoolEntryEntity): Long

    /** When the starting amount was first entered (epoch millis), or null if there is none. */
    @Query("SELECT MIN(at) FROM pool_entries WHERE type = 'STARTING_BALANCE'")
    suspend fun firstStartingBalanceAt(): Long?

    /** The starting amount is a setting (ADR-0045): replacing it is the one delete in the ledger. */
    @Query("DELETE FROM pool_entries WHERE type = 'STARTING_BALANCE'")
    suspend fun deleteStartingBalances()

    /** Changes only the amount, for a currency relabel (ADR-0033); what happened never changes. */
    @Query("UPDATE pool_entries SET amount_minor = :minor, currency = :currency WHERE id = :id")
    suspend fun setEntryAmount(id: Long, minor: Long, currency: String)

    @Query("SELECT * FROM allocations ORDER BY at, id")
    fun observeAllocations(): Flow<List<AllocationEntity>>

    @Query("SELECT * FROM allocations")
    suspend fun getAllocations(): List<AllocationEntity>

    @Query("SELECT * FROM allocations WHERE item_id = :itemId")
    suspend fun getAllocationsFor(itemId: Long): List<AllocationEntity>

    @Insert
    suspend fun insertAllocation(allocation: AllocationEntity): Long

    /** Changes only the amount, for a currency relabel (ADR-0033). */
    @Query("UPDATE allocations SET amount_minor = :minor, currency = :currency WHERE id = :id")
    suspend fun setAllocationAmount(id: Long, minor: Long, currency: String)
}

internal data class HistoryRow(
    @Embedded val item: ItemEntity,
    @Embedded(prefix = "decision_") val decision: DecisionEntity
)
