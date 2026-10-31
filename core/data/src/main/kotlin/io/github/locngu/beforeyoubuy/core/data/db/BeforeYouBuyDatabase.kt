package io.github.locngu.beforeyoubuy.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.locngu.beforeyoubuy.core.domain.coolingoff.wholeDaysBetween
import java.time.Clock
import java.time.Instant

/**
 * The app's database. Every version's schema is committed in `core/data/schemas/`; from the first public release on,
 * every version change ships an explicit `Migration` and its test. There is no destructive fallback: a missing
 * migration crashes rather than deleting the user's data (ADR-0019).
 */
@Database(
    entities = [
        PolicyEntity::class,
        ItemEntity::class,
        ReflectionEntity::class,
        DecisionEntity::class,
        ItemNotificationsEntity::class,
        AllowanceEntity::class,
        PoolEntryEntity::class,
        AllocationEntity::class
    ],
    version = 7,
    exportSchema = true
)
internal abstract class BeforeYouBuyDatabase : RoomDatabase() {
    abstract fun policyDao(): PolicyDao

    abstract fun itemDao(): ItemDao

    abstract fun reflectionDao(): ReflectionDao

    abstract fun decisionDao(): DecisionDao

    abstract fun notificationDao(): NotificationDao

    abstract fun poolDao(): PoolDao

    companion object {
        const val NAME = "before-you-buy.db"

        /**
         * [defaultCurrency] is asked once, when the database is first created, to fix the app's currency. [clock] is
         * "now" for migrations that need it ([migration6To7]).
         */
        fun create(context: Context, clock: Clock, defaultCurrency: () -> String): BeforeYouBuyDatabase =
            Room.databaseBuilder(context, BeforeYouBuyDatabase::class.java, NAME)
                .addMigrations(*migrations(clock))
                .withPolicyRow(defaultCurrency)
                .build()
    }
}

/**
 * 1 → 2 (issue #18): `decisions.note`, the optional "Why not?" of a reject. Existing decisions have none.
 */
internal val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE decisions ADD COLUMN note TEXT")
    }
}

/**
 * 2 → 3 (issue #20): `item_notifications`, what was notified about each item. Nothing was notified before, so it
 * starts empty.
 */
internal val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `item_notifications` (`item_id` INTEGER NOT NULL, " +
                "`waiting_period_over_at` INTEGER, `reflection_nudged_at` INTEGER, PRIMARY KEY(`item_id`), " +
                "FOREIGN KEY(`item_id`) REFERENCES `items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
    }
}

/**
 * 3 → 4 (issue #22): `allowance` and the `pool_entries` ledger. There was no fun money before, so both start empty
 * and Home offers to set it up.
 */
internal val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `allowance` (`id` INTEGER NOT NULL, `monthly_minor` INTEGER NOT NULL, " +
                "`currency` TEXT NOT NULL, `accrual_day` INTEGER NOT NULL, `start_date` TEXT NOT NULL, " +
                "PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `pool_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`type` TEXT NOT NULL, `amount_minor` INTEGER NOT NULL, `currency` TEXT NOT NULL, " +
                "`at` INTEGER NOT NULL, `accrual_month` TEXT, `item_id` INTEGER, `note` TEXT, " +
                "FOREIGN KEY(`item_id`) REFERENCES `items`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_pool_entries_accrual_month` ON `pool_entries` (`accrual_month`)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pool_entries_item_id` ON `pool_entries` (`item_id`)")
    }
}

/**
 * 4 → 5 (issue #23): `allocations`, money set aside per item, and the allowance's "Wants need their price set aside"
 * switch, on (ADR-0046). Nothing was set aside before, so the table starts empty.
 */
internal val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE allowance ADD COLUMN `wants_need_funding` INTEGER NOT NULL DEFAULT 1")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `allocations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`item_id` INTEGER NOT NULL, `amount_minor` INTEGER NOT NULL, `currency` TEXT NOT NULL, " +
                "`at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`item_id`) REFERENCES `items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_allocations_item_id` ON `allocations` (`item_id`)")
    }
}

/**
 * 5 → 6 (issue #24): `policy.priority_slots`, how many wants can be priorities at once, 3 until the user changes it
 * (ADR-0011, ADR-0047). `items.priority_slot` existed from the start and stays as it is.
 */
internal val MIGRATION_5_6: Migration = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE policy ADD COLUMN `priority_slots` INTEGER NOT NULL DEFAULT 3")
    }
}

/**
 * 6 → 7 (issue #82, ADR-0049, ADR-0050): the wait runs only while a want holds a priority slot, so each item gets
 * `cooling_off_days`, `waited_days` and `waiting_since`.
 *
 * - The days each item waits are those from when it was added to its stored end, rounded to whole days (a
 *   daylight-saving change moves the end by an hour). They keep any extension from a price edit or a tier change.
 * - Priorities keep their slot and their end: their wait has run since they were added.
 * - Every other active want is parked and isn't Ready any more. The whole calendar days since it was added, in
 *   [clock]'s zone, count as waited, so promoting it later loses nothing. A want that became a need keeps the days it
 *   waited as a want, should it become a want again.
 * - Nothing runs for parked wants, needs and ended items: their `cooling_ends_at` becomes `added_at`, as the app
 *   writes it, so each row reads back as it is stored (ADR-0032).
 */
internal fun migration6To7(clock: Clock): Migration = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE items ADD COLUMN `cooling_off_days` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE items ADD COLUMN `waited_days` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE items ADD COLUMN `waiting_since` INTEGER")
        db.execSQL("UPDATE items SET cooling_off_days = MAX(0, (cooling_ends_at - added_at + 43200000) / 86400000)")
        db.execSQL(
            "UPDATE items SET waiting_since = added_at " +
                "WHERE status = 'ACTIVE' AND kind = 'WANT' AND priority_slot IS NOT NULL"
        )
        val now = clock.instant()
        db.query(
            "SELECT id, added_at, kind, marked_as_need_at FROM items " +
                "WHERE status = 'ACTIVE' AND waiting_since IS NULL AND (kind = 'WANT' OR marked_as_need_at IS NOT NULL)"
        ).use { rows ->
            while (rows.moveToNext()) {
                val added = Instant.ofEpochMilli(rows.getLong(1))
                val waitedUntil = if (rows.getString(2) == "WANT") now else Instant.ofEpochMilli(rows.getLong(3))
                db.execSQL(
                    "UPDATE items SET waited_days = ? WHERE id = ?",
                    arrayOf<Any>(wholeDaysBetween(added, waitedUntil, clock.zone), rows.getLong(0))
                )
            }
        }
        db.execSQL("UPDATE items SET cooling_ends_at = added_at WHERE waiting_since IS NULL")
    }
}

/**
 * Every migration, oldest first, with [clock] for those that need "now". A new schema version adds its own here, with
 * a test in `MigrationTest`.
 */
internal fun migrations(clock: Clock): Array<Migration> =
    arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, migration6To7(clock))

/**
 * Makes sure the policy row exists whenever the database opens, before anything reads it: the currency is fixed
 * from [defaultCurrency] on first start and never follows the device region afterwards (ADR-0033). `OR IGNORE` keeps
 * an existing row.
 */
internal fun RoomDatabase.Builder<BeforeYouBuyDatabase>.withPolicyRow(
    defaultCurrency: () -> String
): RoomDatabase.Builder<BeforeYouBuyDatabase> = addCallback(
    object : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            db.execSQL(
                "INSERT OR IGNORE INTO policy (id, currency, cooling_off_rules) VALUES (?, ?, NULL)",
                arrayOf<Any>(PolicyEntity.ID, defaultCurrency())
            )
        }
    }
)
