package io.github.locngu.beforeyoubuy.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The app's database. Every version's schema is committed in `core/data/schemas/`; from the first public release on,
 * every version change ships an explicit `Migration` and its test. There is no destructive fallback: a missing
 * migration crashes rather than deleting the user's data (ADR-0019).
 */
@Database(
    entities = [PolicyEntity::class, ItemEntity::class, ReflectionEntity::class, DecisionEntity::class],
    version = 1,
    exportSchema = true
)
internal abstract class BeforeYouBuyDatabase : RoomDatabase() {
    abstract fun policyDao(): PolicyDao

    abstract fun itemDao(): ItemDao

    abstract fun reflectionDao(): ReflectionDao

    abstract fun decisionDao(): DecisionDao

    companion object {
        const val NAME = "before-you-buy.db"

        /** [defaultCurrency] is asked once, when the database is first created, to fix the app's currency. */
        fun create(context: Context, defaultCurrency: () -> String): BeforeYouBuyDatabase =
            Room.databaseBuilder(context, BeforeYouBuyDatabase::class.java, NAME)
                .withPolicyRow(defaultCurrency)
                .build()
    }
}

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
