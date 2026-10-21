package io.github.locngu.beforeyoubuy.core.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.locngu.beforeyoubuy.core.data.item.toDomain
import io.github.locngu.beforeyoubuy.core.domain.item.DecisionType
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Each migration against the committed schema it starts from (ADR-0019): a database built from `schemas/<n>.json`
 * with rows in it is opened by the app's Room setup, which runs the migrations and then checks the result against the
 * current entities (a mismatch throws). The rows must survive.
 */
@RunWith(RobolectricTestRunner::class)
internal class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-test.db"

    @Before
    @After
    fun deleteDatabase() {
        context.deleteDatabase(name)
    }

    @Test
    fun `1 to 2 adds an empty note to existing decisions and keeps every row`() = runTest {
        createFromSchema(1) { db ->
            db.execSQL("INSERT INTO policy (id, currency, cooling_off_rules) VALUES (1, 'EUR', NULL)")
            db.execSQL(
                "INSERT INTO items (id, name, price_minor, currency, added_at, cooling_ends_at, kind, status) " +
                    "VALUES (7, 'Camera', 120000, 'EUR', 1000, 2000, 'WANT', 'REJECTED')"
            )
            db.execSQL(
                "INSERT INTO decisions (item_id, type, decided_at, source) VALUES (7, 'REJECT', 3000, 'NOTIFICATION')"
            )
        }

        val database = Room.databaseBuilder(context, BeforeYouBuyDatabase::class.java, name)
            .addMigrations(*MIGRATIONS)
            .withPolicyRow { "USD" }
            .allowMainThreadQueries()
            .build()
        try {
            val decision = database.decisionDao().observeForItem(7).first().single()
            assertEquals(DecisionType.REJECT, decision.toDomain()?.type)
            assertNull(decision.note)
            assertEquals("Camera", database.itemDao().getById(7)?.name)
            // The policy row of version 1 stays; withPolicyRow doesn't replace it.
            assertEquals("EUR", database.policyDao().get()?.currency)
        } finally {
            database.close()
        }
    }

    /** A database file as [version] of the committed schema created it, with [fill]'s rows. */
    private fun createFromSchema(version: Int, fill: (SQLiteDatabase) -> Unit) {
        val schema = File(SCHEMAS, "$version.json").readText()
        val database = Json.parseToJsonElement(schema).jsonObject.getValue("database").jsonObject
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            database.getValue("entities").jsonArray.forEach { element ->
                val entity = element.jsonObject
                val table = entity.getValue("tableName").jsonPrimitive.content
                val statements = listOf(entity.getValue("createSql")) + entity["indices"]?.jsonArray.orEmpty().map {
                    it.jsonObject.getValue("createSql")
                }
                statements.forEach { db.execSQL(it.jsonPrimitive.content.replace("\${TABLE_NAME}", table)) }
            }
            database.getValue("setupQueries").jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
            fill(db)
            db.version = version
        }
    }

    private companion object {
        // Unit tests run in the module's directory.
        val SCHEMAS = File("schemas/${BeforeYouBuyDatabase::class.java.name}")
    }
}
