package io.github.locngu.beforeyoubuy.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The app's database. Every version's schema is committed in `core/data/schemas/`; from the first public release on,
 * every version change ships an explicit `Migration` and its test. There is no destructive fallback: a missing
 * migration crashes rather than deleting the user's data (ADR-0019).
 */
@Database(
    entities = [ItemEntity::class, ReflectionEntity::class, DecisionEntity::class],
    version = 1,
    exportSchema = true
)
internal abstract class BeforeYouBuyDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao

    abstract fun reflectionDao(): ReflectionDao

    abstract fun decisionDao(): DecisionDao

    companion object {
        const val NAME = "before-you-buy.db"

        fun create(context: Context): BeforeYouBuyDatabase =
            Room.databaseBuilder(context, BeforeYouBuyDatabase::class.java, NAME).build()
    }
}
