package io.github.locngu.beforeyoubuy.core.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A fresh in-memory [BeforeYouBuyDatabase] per test, created with [DEFAULT_CURRENCY] as the app's currency. */
@RunWith(RobolectricTestRunner::class)
internal abstract class DatabaseTest {
    protected lateinit var database: BeforeYouBuyDatabase

    @Before
    fun openDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BeforeYouBuyDatabase::class.java
        ).withPolicyRow { DEFAULT_CURRENCY }.allowMainThreadQueries().build()
    }

    @After
    fun closeDatabase() = database.close()

    /**
     * Runs raw SQL, for setting up states the app itself never writes. Room only notices writes outside its own
     * transactions after a refresh, so observed queries re-emit.
     */
    protected fun execSql(sql: String) {
        database.openHelper.writableDatabase.execSQL(sql)
        database.invalidationTracker.refreshAsync()
    }

    /** The first column of every row [sql] returns, as text. */
    protected fun queryStrings(sql: String): List<String> =
        database.openHelper.readableDatabase.query(sql).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }

    companion object {
        const val DEFAULT_CURRENCY = "EUR"
    }
}
