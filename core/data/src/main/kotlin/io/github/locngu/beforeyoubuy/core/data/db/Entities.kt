package io.github.locngu.beforeyoubuy.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Storage conventions (ADR-0018, ADR-0019, ADR-0032): money is `*_minor` Long + currency code, instants are epoch
// millis, dates are ISO-8601 text, enums are their names and are read defensively. Never a stage column (ADR-0007).
// M2 adds `allocations` and `pool_entries` next to these, referencing `items.id`.

/**
 * The app's currency and cooling-off tiers (`Policy`): one row, id [ID]. Next to the items so a tier or currency
 * change rewrites them in one transaction (ADR-0031, ADR-0033). [coolingOffRules] is `CoolingOffRulesJson`, or null
 * for the defaults in [currency].
 */
@Entity(tableName = "policy")
internal data class PolicyEntity(
    @PrimaryKey val id: Int = ID,
    val currency: String,
    @ColumnInfo(name = "cooling_off_rules") val coolingOffRules: String?
) {
    companion object {
        const val ID = 1
    }
}

/** One row per item: the stored facts of `Item` (brief §4.1). */
@Entity(tableName = "items", indices = [Index("status")])
internal data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val name: String,
    @ColumnInfo(name = "price_minor") val priceMinor: Long,
    val currency: String,
    @ColumnInfo(name = "added_at") val addedAt: Long,
    @ColumnInfo(name = "cooling_ends_at") val coolingEndsAt: Long,
    val kind: String,
    val status: String,
    val link: String?,
    @ColumnInfo(name = "image_ref") val imageRef: String?,
    val category: String?,
    val notes: String?,
    @ColumnInfo(name = "target_date") val targetDate: String?,
    @ColumnInfo(name = "recheck_at") val recheckAt: Long?,
    @ColumnInfo(name = "priority_slot") val prioritySlot: Int?,
    @ColumnInfo(name = "marked_as_need_at") val markedAsNeedAt: Long?
)

/** An item's reflection answers, 1:1 with the item (brief §4.3). [reasonsAgainst] is a JSON array of strings. */
@Entity(
    tableName = "reflections",
    foreignKeys = [
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
internal data class ReflectionEntity(
    @PrimaryKey @ColumnInfo(name = "item_id") val itemId: Long,
    val why: String?,
    @ColumnInfo(name = "usage_frequency") val usageFrequency: String?,
    @ColumnInfo(name = "reasons_against") val reasonsAgainst: String,
    @ColumnInfo(name = "problem_solved") val problemSolved: String?,
    @ColumnInfo(name = "current_alternative") val currentAlternative: String?,
    val replaces: String?,
    @ColumnInfo(name = "why_now") val whyNow: String?,
    @ColumnInfo(name = "upgrade_or_new") val upgradeOrNew: String?
)

/** One decision about an item (brief §5). Append-only: rows are inserted, never updated. */
@Entity(
    tableName = "decisions",
    foreignKeys = [
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("item_id")]
)
internal data class DecisionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    @ColumnInfo(name = "item_id") val itemId: Long,
    val type: String,
    @ColumnInfo(name = "decided_at") val decidedAt: Long,
    val source: String,
    @ColumnInfo(name = "actual_price_minor") val actualPriceMinor: Long?,
    @ColumnInfo(name = "actual_price_currency") val actualPriceCurrency: String?,
    @ColumnInfo(name = "early_reason") val earlyReason: String?,
    @ColumnInfo(name = "wait_until") val waitUntil: Long?,
    /** The optional "Why not?" of a reject; added in version 2 ([MIGRATION_1_2]). */
    val note: String?
)

/**
 * What was notified about an item (`ItemNotifications`, brief §8), 1:1 with the item; added in version 3
 * ([MIGRATION_2_3]). Instants are epoch millis; null means never.
 */
@Entity(
    tableName = "item_notifications",
    foreignKeys = [
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
internal data class ItemNotificationsEntity(
    @PrimaryKey @ColumnInfo(name = "item_id") val itemId: Long,
    @ColumnInfo(name = "waiting_period_over_at") val waitingPeriodOverAt: Long?,
    @ColumnInfo(name = "reflection_nudged_at") val reflectionNudgedAt: Long?
)
