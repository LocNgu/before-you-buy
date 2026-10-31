package io.github.locngu.beforeyoubuy.core.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.locngu.beforeyoubuy.core.data.item.toDomain
import io.github.locngu.beforeyoubuy.core.data.item.toEntity
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.isParked
import io.github.locngu.beforeyoubuy.core.domain.item.DecisionType
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
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

    // 2 November 2026, 10:15 in Berlin (CET): "now" for migrations that need it.
    private val clock: Clock = Clock.fixed(Instant.parse("2026-11-02T09:15:00Z"), ZoneId.of("Europe/Berlin"))

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
            .addMigrations(*migrations(clock))
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

    @Test
    fun `2 to 3 adds an empty notification log that follows its items`() = runTest {
        createFromSchema(2) { db ->
            db.execSQL("INSERT INTO policy (id, currency, cooling_off_rules) VALUES (1, 'EUR', NULL)")
            db.execSQL(
                "INSERT INTO items (id, name, price_minor, currency, added_at, cooling_ends_at, kind, status) " +
                    "VALUES (7, 'Camera', 120000, 'EUR', 1000, 2000, 'WANT', 'ACTIVE')"
            )
            db.execSQL(
                "INSERT INTO decisions (item_id, type, decided_at, source, note) " +
                    "VALUES (7, 'WAIT', 3000, 'APP', NULL)"
            )
        }

        val database = Room.databaseBuilder(context, BeforeYouBuyDatabase::class.java, name)
            .addMigrations(*migrations(clock))
            .withPolicyRow { "USD" }
            .allowMainThreadQueries()
            .build()
        try {
            val notifications = database.notificationDao()
            assertEquals(emptyList<ItemNotificationsEntity>(), notifications.getAll())
            assertEquals("Camera", database.itemDao().getById(7)?.name)
            assertEquals(DecisionType.WAIT, database.decisionDao().observeForItem(7).first().single().toDomain()?.type)

            notifications.setIfItemExists(7, waitingPeriodOverAt = 4000, reflectionNudgedAt = null)
            assertEquals(listOf(ItemNotificationsEntity(7, 4000, null)), notifications.getAll())
            database.itemDao().delete(7)
            assertEquals(emptyList<ItemNotificationsEntity>(), notifications.getAll())
        } finally {
            database.close()
        }
    }

    @Test
    fun `3 to 4 adds an empty allowance and pool ledger and keeps every row`() = runTest {
        createFromSchema(3) { db ->
            db.execSQL("INSERT INTO policy (id, currency, cooling_off_rules) VALUES (1, 'EUR', NULL)")
            db.execSQL(
                "INSERT INTO items (id, name, price_minor, currency, added_at, cooling_ends_at, kind, status) " +
                    "VALUES (7, 'Camera', 120000, 'EUR', 1000, 2000, 'WANT', 'ACTIVE')"
            )
            db.execSQL("INSERT INTO item_notifications (item_id, waiting_period_over_at) VALUES (7, 4000)")
        }

        val database = Room.databaseBuilder(context, BeforeYouBuyDatabase::class.java, name)
            .addMigrations(*migrations(clock))
            .withPolicyRow { "USD" }
            .allowMainThreadQueries()
            .build()
        try {
            val pool = database.poolDao()
            assertNull(pool.getAllowance())
            assertEquals(emptyList<PoolEntryEntity>(), pool.getEntries())
            assertEquals("Camera", database.itemDao().getById(7)?.name)
            assertEquals(listOf(ItemNotificationsEntity(7, 4000, null)), database.notificationDao().getAll())

            // The unique month and the item reference work as in a fresh database.
            val accrual =
                PoolEntryEntity(type = "ACCRUAL", amountMinor = 1, currency = "EUR", at = 0, accrualMonth = "2026-11")
            pool.insertEntry(accrual)
            assertEquals(-1L, pool.insertEntry(accrual))
            pool.insertEntry(PoolEntryEntity(type = "PURCHASE", amountMinor = -1, currency = "EUR", at = 0, itemId = 7))
            database.itemDao().delete(7)
            assertEquals(listOf(null, null), pool.getEntries().map { it.itemId })
        } finally {
            database.close()
        }
    }

    @Test
    fun `4 to 5 adds empty allocations and turns the funding switch on`() = runTest {
        createFromSchema(4) { db ->
            db.execSQL("INSERT INTO policy (id, currency, cooling_off_rules) VALUES (1, 'EUR', NULL)")
            db.execSQL(
                "INSERT INTO items (id, name, price_minor, currency, added_at, cooling_ends_at, kind, status) " +
                    "VALUES (7, 'Camera', 120000, 'EUR', 1000, 2000, 'WANT', 'ACTIVE')"
            )
            db.execSQL(
                "INSERT INTO allowance (id, monthly_minor, currency, accrual_day, start_date) " +
                    "VALUES (1, 30000, 'EUR', 1, '2026-10-23')"
            )
            db.execSQL(
                "INSERT INTO pool_entries (type, amount_minor, currency, at) VALUES ('STARTING_BALANCE', 5000, 'EUR', 0)"
            )
        }

        val database = Room.databaseBuilder(context, BeforeYouBuyDatabase::class.java, name)
            .addMigrations(*migrations(clock))
            .withPolicyRow { "USD" }
            .allowMainThreadQueries()
            .build()
        try {
            val pool = database.poolDao()
            assertEquals(
                AllowanceEntity(1, 30_000, "EUR", 1, "2026-10-23", wantsNeedFunding = true),
                pool.getAllowance()
            )
            assertEquals(1, pool.getEntries().size)
            assertEquals(emptyList<AllocationEntity>(), pool.getAllocations())

            // Allocations follow their item, as in a fresh database.
            pool.insertAllocation(AllocationEntity(itemId = 7, amountMinor = 100, currency = "EUR", at = 0))
            database.itemDao().delete(7)
            assertEquals(emptyList<AllocationEntity>(), pool.getAllocations())
        } finally {
            database.close()
        }
    }

    @Test
    fun `5 to 6 gives the policy 3 priority slots and keeps the items' slots`() = runTest {
        createFromSchema(5) { db ->
            db.execSQL("INSERT INTO policy (id, currency, cooling_off_rules) VALUES (1, 'EUR', NULL)")
            db.execSQL(
                "INSERT INTO items (id, name, price_minor, currency, added_at, cooling_ends_at, kind, status, " +
                    "priority_slot) VALUES (7, 'Camera', 120000, 'EUR', 1000, 2000, 'WANT', 'ACTIVE', 1)"
            )
        }

        val database = Room.databaseBuilder(context, BeforeYouBuyDatabase::class.java, name)
            .addMigrations(*migrations(clock))
            .withPolicyRow { "USD" }
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(
                PolicyEntity(currency = "EUR", coolingOffRules = null, prioritySlots = 3),
                database.policyDao().get()
            )
            assertEquals(1, database.itemDao().getById(7)?.prioritySlot)
        } finally {
            database.close()
        }
    }

    @Test
    fun `6 to 7 keeps the priorities' waits, parks the other wants with the days since they were added`() = runTest {
        createFromSchema(6) { db ->
            db.execSQL(
                "INSERT INTO policy (id, currency, cooling_off_rules, priority_slots) VALUES (1, 'EUR', NULL, 3)"
            )
            fun insert(id: Int, added: String, ends: String, kind: String, status: String, slot: Int?, need: String?) =
                db.execSQL(
                    "INSERT INTO items (id, name, price_minor, currency, added_at, cooling_ends_at, kind, status, " +
                        "priority_slot, marked_as_need_at) VALUES (?, ?, 12000, 'EUR', ?, ?, ?, ?, ?, ?)",
                    arrayOf<Any?>(id, "Item $id", millis(added), millis(ends), kind, status, slot, need?.let(::millis))
                )
            // A priority: 1 Oct 10:00 CEST, 30 calendar days to 31 Oct 10:00 CET (30 × 24 h plus the autumn hour).
            insert(1, "2026-10-01T08:00:00Z", "2026-10-31T09:00:00Z", "WANT", "ACTIVE", 1, null)
            // Parked: 20 Oct 10:30 CEST. At 10:15 on 2 Nov that's 12 calendar days, though over 13 × 24 h.
            insert(2, "2026-10-20T08:30:00Z", "2026-11-19T09:30:00Z", "WANT", "ACTIVE", null, null)
            // Parked, its wait long over: 1 Aug, 7 days. 93 days have passed.
            insert(3, "2026-08-01T08:00:00Z", "2026-08-08T08:00:00Z", "WANT", "ACTIVE", null, null)
            // A want that became a need after 3 days, and one captured as a need.
            insert(4, "2026-10-01T08:00:00Z", "2026-10-31T09:00:00Z", "NEED", "ACTIVE", null, "2026-10-04T08:00:00Z")
            insert(5, "2026-10-10T08:00:00Z", "2026-10-10T08:00:00Z", "NEED", "ACTIVE", null, null)
            // Bought, with its decision and answers.
            insert(6, "2026-09-01T08:00:00Z", "2026-10-01T08:00:00Z", "WANT", "PURCHASED", null, null)
            db.execSQL(
                "INSERT INTO decisions (item_id, type, decided_at, source, actual_price_minor, " +
                    "actual_price_currency) VALUES (6, 'BUY', ${millis("2026-10-02T08:00:00Z")}, 'APP', 11000, 'EUR')"
            )
            db.execSQL("INSERT INTO reflections (item_id, reasons_against) VALUES (6, '[\"Heavy\"]')")
        }

        val database = Room.databaseBuilder(context, BeforeYouBuyDatabase::class.java, name)
            .addMigrations(*migrations(clock))
            .withPolicyRow { "USD" }
            .allowMainThreadQueries()
            .build()
        try {
            val items = database.itemDao()
            suspend fun waitOf(id: Long) = items.getById(id)!!
                .let { listOf(it.coolingOffDays.toLong(), it.waitedDays.toLong(), it.waitingSince, it.coolingEndsAt) }

            // Days to wait, days waited, running since, stored end.
            assertEquals(listOf(30L, 0L, millis("2026-10-01T08:00:00Z"), millis("2026-10-31T09:00:00Z")), waitOf(1))
            assertEquals(listOf(30L, 12L, null, millis("2026-10-20T08:30:00Z")), waitOf(2))
            assertEquals(listOf(7L, 93L, null, millis("2026-08-01T08:00:00Z")), waitOf(3))
            assertEquals(listOf(30L, 3L, null, millis("2026-10-01T08:00:00Z")), waitOf(4))
            assertEquals(listOf(0L, 0L, null, millis("2026-10-10T08:00:00Z")), waitOf(5))
            assertEquals(listOf(30L, 0L, null, millis("2026-09-01T08:00:00Z")), waitOf(6))

            val active = items.getByStatus("ACTIVE").map { it.toDomain() }
            assertEquals(listOf(1L), active.filter { !it.isParked && it.kind == ItemKind.WANT }.map { it.id.value })
            assertEquals(listOf(2L, 3L), active.filter { it.isParked }.map { it.id.value })
            assertEquals(Instant.parse("2026-10-31T09:00:00Z"), active.first { it.id.value == 1L }.coolingEndsAt)
            // Every row reads back as it is stored, so compare-and-set writes still match (ADR-0032).
            items.getAll().forEach { assertEquals(it, it.toDomain().toEntity()) }
            assertEquals(1, database.decisionDao().observeForItem(6).first().size)
            assertEquals(1, database.reflectionDao().observeAll().first().size)
        } finally {
            database.close()
        }
    }

    private fun millis(instant: String): Long = Instant.parse(instant).toEpochMilli()

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
