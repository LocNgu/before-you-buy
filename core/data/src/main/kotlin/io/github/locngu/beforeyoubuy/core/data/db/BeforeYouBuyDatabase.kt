package io.github.locngu.beforeyoubuy.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
        PoolEntryEntity::class
    ],
    version = 4,
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

        /** [defaultCurrency] is asked once, when the database is first created, to fix the app's currency. */
        fun create(context: Context, defaultCurrency: () -> String): BeforeYouBuyDatabase =
            Room.databaseBuilder(context, BeforeYouBuyDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
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

/** Every migration, oldest first. A new schema version adds its own here, with a test in `MigrationTest`. */
internal val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

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
