package io.github.locngu.beforeyoubuy.core.data.pool

import app.cash.turbine.test
import io.github.locngu.beforeyoubuy.core.data.db.DatabaseTest
import io.github.locngu.beforeyoubuy.core.data.db.PoolEntryEntity
import io.github.locngu.beforeyoubuy.core.data.item.RoomItemRepository
import io.github.locngu.beforeyoubuy.core.data.item.newItem
import io.github.locngu.beforeyoubuy.core.data.testing.assertThrowsIllegalArgument
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.core.domain.pool.AllowanceSettings
import io.github.locngu.beforeyoubuy.core.domain.pool.PoolEntry
import io.github.locngu.beforeyoubuy.core.domain.pool.poolBalanceOf
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class RoomPoolRepositoryTest : DatabaseTest() {
    private lateinit var pool: RoomPoolRepository

    private val zone: ZoneId = ZoneId.of("Europe/Berlin")
    private val monthly = eur(30_000)

    @Before
    fun createRepository() {
        pool = RoomPoolRepository(database)
    }

    private fun eur(minor: Long) = Money(minor, "EUR")

    /** Noon in Berlin on [date]. */
    private fun on(date: String): Instant = LocalDate.parse(date).atTime(12, 0).atZone(zone).toInstant()

    private suspend fun accruals() = pool.entries.first().filterIsInstance<PoolEntry.Accrual>()

    private suspend fun balance() = poolBalanceOf(pool.entries.first(), eur(0)).pool

    @Test
    fun `nothing is set up at first and accruing does nothing`() = runTest {
        assertNull(pool.allowance.first())
        assertEquals(0, pool.accrue(on("2026-11-01"), zone))
        assertEquals(emptyList<PoolEntry>(), pool.entries.first())
    }

    @Test
    fun `setting up stores the allowance from today with the starting balance`() = runTest {
        assertTrue(pool.setUp(monthly, 1, eur(10_000), on("2026-10-23"), zone))

        assertEquals(AllowanceSettings(monthly, 1, LocalDate.parse("2026-10-23")), pool.allowance.first())
        assertEquals(listOf(PoolEntry.StartingBalance(eur(10_000), on("2026-10-23"))), pool.entries.first())
    }

    @Test
    fun `a zero starting balance writes no entry`() = runTest {
        pool.setUp(monthly, 1, eur(0), on("2026-10-23"), zone)
        assertEquals(emptyList<PoolEntry>(), pool.entries.first())
    }

    @Test
    fun `setting up on the accrual day credits this month at once`() = runTest {
        pool.setUp(monthly, 23, eur(0), on("2026-10-23"), zone)
        assertEquals(listOf(YearMonth.of(2026, 10)), accruals().map { it.month })
    }

    @Test
    fun `setting up twice writes nothing the second time`() = runTest {
        pool.setUp(monthly, 1, eur(10_000), on("2026-10-23"), zone)
        assertFalse(pool.setUp(eur(1), 5, eur(99), on("2026-10-24"), zone))

        assertEquals(monthly, pool.allowance.first()?.monthlyAmount)
        assertEquals(eur(10_000), balance())
    }

    @Test
    fun `the first accrual lands on the next accrual day`() = runTest {
        pool.setUp(monthly, 1, eur(10_000), on("2026-10-23"), zone)

        assertEquals(0, pool.accrue(on("2026-10-31"), zone))
        assertEquals(1, pool.accrue(on("2026-11-01"), zone))
        assertEquals(listOf(PoolEntry.Accrual(YearMonth.of(2026, 11), monthly, on("2026-11-01"))), accruals())
        assertEquals(eur(40_000), balance())
    }

    @Test
    fun `missed months are caught up and never credited twice`() = runTest {
        pool.setUp(monthly, 1, eur(0), on("2026-10-23"), zone)

        assertEquals(4, pool.accrue(on("2027-02-10"), zone))
        assertEquals(0, pool.accrue(on("2027-02-10"), zone))
        assertEquals(0, pool.accrue(on("2027-02-28"), zone))

        assertEquals(
            listOf("2026-11", "2026-12", "2027-01", "2027-02"),
            accruals().map { it.month.toString() }
        )
        assertEquals(eur(120_000), balance())
    }

    @Test
    fun `app start and the daily worker at the same moment credit each month once`() = runTest {
        pool.setUp(monthly, 1, eur(0), on("2026-10-23"), zone)

        // Repeated so that, without the checks inside the transaction, a double credit would very likely show.
        val months = List(12) { on("2026-11-01").plusSeconds(it * 31L * 24 * 3_600) }
        withContext(Dispatchers.IO) {
            months.forEach { now -> List(4) { async { pool.accrue(now, zone) } }.awaitAll() }
        }

        val credited = accruals().map { it.month }
        assertEquals(credited.distinct(), credited)
        assertEquals(12, credited.size)
    }

    @Test
    fun `the unique month ignores a second credit written by anyone`() = runTest {
        val dao = database.poolDao()
        val row = PoolEntryEntity(type = "ACCRUAL", amountMinor = 1, currency = "EUR", at = 0, accrualMonth = "2026-11")
        assertTrue(dao.insertEntry(row) > 0)
        assertEquals(-1L, dao.insertEntry(row.copy(amountMinor = 2)))
        // Entries without a month don't collide.
        assertTrue(dao.insertEntry(row.copy(type = "ADJUSTMENT", accrualMonth = null)) > 0)
        assertTrue(dao.insertEntry(row.copy(type = "ADJUSTMENT", accrualMonth = null)) > 0)
    }

    @Test
    fun `a new amount applies from the next accrual, missed months keep the old one`() = runTest {
        pool.setUp(monthly, 1, eur(0), on("2026-10-23"), zone)
        pool.accrue(on("2026-11-01"), zone)

        // Not opened December to February; changed on 15 Feb.
        pool.change(eur(50_000), 1, eur(0), on("2027-02-15"), zone)
        pool.accrue(on("2027-03-01"), zone)

        assertEquals(
            listOf(monthly, monthly, monthly, monthly, eur(50_000)),
            accruals().map { it.change }
        )
        assertEquals(AllowanceSettings(eur(50_000), 1, LocalDate.parse("2026-10-23")), pool.allowance.first())
    }

    @Test
    fun `an earlier accrual day credits this month at once, a later one waits`() = runTest {
        pool.setUp(monthly, 20, eur(0), on("2026-10-01"), zone)
        pool.accrue(on("2026-10-20"), zone)

        // 15 Nov: the 20th hasn't come; moving to the 1st makes November due now.
        pool.change(monthly, 1, eur(0), on("2026-11-15"), zone)
        assertEquals(listOf("2026-10", "2026-11"), accruals().map { it.month.toString() })

        // Moving back to the 20th doesn't credit November again.
        pool.change(monthly, 20, eur(0), on("2026-11-25"), zone)
        assertEquals(2, accruals().size)
    }

    @Test
    fun `the starting amount is replaced, keeps its date, and zero removes it`() = runTest {
        pool.setUp(monthly, 1, eur(10_000), on("2026-10-23"), zone)
        pool.accrue(on("2026-11-01"), zone)

        pool.change(monthly, 1, eur(25_000), on("2026-11-05"), zone)
        assertEquals(
            listOf(
                PoolEntry.StartingBalance(eur(25_000), on("2026-10-23")),
                PoolEntry.Accrual(YearMonth.of(2026, 11), monthly, on("2026-11-01"))
            ),
            pool.entries.first()
        )

        pool.change(monthly, 1, eur(0), on("2026-11-06"), zone)
        assertEquals(listOf(YearMonth.of(2026, 11)), pool.entries.first().map { (it as PoolEntry.Accrual).month })

        // Added again later, it's dated then.
        pool.change(monthly, 1, eur(5_000), on("2026-11-07"), zone)
        assertEquals(PoolEntry.StartingBalance(eur(5_000), on("2026-11-07")), pool.entries.first().last())
    }

    @Test
    fun `changing needs an allowance`() = runTest {
        val thrown = runCatching { pool.change(monthly, 1, eur(0), on("2026-10-23"), zone) }.exceptionOrNull()
        assertTrue("Expected IllegalStateException, was $thrown", thrown is IllegalStateException)
    }

    @Test
    fun `adjustments add or take out, with an optional note`() = runTest {
        pool.setUp(monthly, 1, eur(10_000), on("2026-10-23"), zone)
        pool.adjust(eur(2_500), "  Birthday  ", on("2026-10-24"))
        pool.adjust(eur(-20_000), " ", on("2026-10-25"))

        assertEquals(
            listOf(
                PoolEntry.Adjustment(eur(2_500), "Birthday", on("2026-10-24")),
                PoolEntry.Adjustment(eur(-20_000), null, on("2026-10-25"))
            ),
            pool.entries.first().drop(1)
        )
        // Confirmed below zero: the ledger keeps it.
        assertEquals(eur(-7_500), balance())
    }

    @Test
    fun `amounts must be in the app's currency`() = runTest {
        assertThrowsIllegalArgument { pool.setUp(Money(30_000, "USD"), 1, eur(0), on("2026-10-23"), zone) }
        assertThrowsIllegalArgument { pool.setUp(monthly, 1, Money(1, "USD"), on("2026-10-23"), zone) }
        assertNull(pool.allowance.first())
        pool.setUp(monthly, 1, eur(0), on("2026-10-23"), zone)
        assertThrowsIllegalArgument { pool.adjust(Money(1, "USD"), null, on("2026-10-23")) }
        assertThrowsIllegalArgument { pool.change(Money(1, "USD"), 1, eur(0), on("2026-10-23"), zone) }
    }

    @Test
    fun `a purchase stays in the ledger when its item is deleted`() = runTest {
        val itemId = RoomItemRepository(database).add(newItem())
        database.poolDao().insertEntry(PoolEntry.Purchase(itemId, eur(12_000), on("2026-11-02")).toEntity())
        assertEquals(PoolEntry.Purchase(itemId, eur(12_000), on("2026-11-02")), pool.entries.first().single())

        database.itemDao().delete(itemId.value)

        assertEquals(PoolEntry.Purchase(null, eur(12_000), on("2026-11-02")), pool.entries.first().single())
    }

    @Test
    fun `rows this version can't read keep the balance as adjustments`() = runTest {
        execSql(
            "INSERT INTO pool_entries (type, amount_minor, currency, at, accrual_month) VALUES " +
                "('FROM_THE_FUTURE', 700, 'EUR', 1, NULL), ('ACCRUAL', 300, 'EUR', 2, 'not a month')"
        )
        assertEquals(
            listOf(
                PoolEntry.Adjustment(eur(700), null, Instant.ofEpochMilli(1)),
                PoolEntry.Adjustment(eur(300), null, Instant.ofEpochMilli(2))
            ),
            pool.entries.first()
        )
    }

    @Test
    fun `a ledger row in an unknown currency is left out instead of failing the flow`() = runTest {
        execSql(
            "INSERT INTO pool_entries (type, amount_minor, currency, at) VALUES " +
                "('ADJUSTMENT', 700, 'XYZ', 1), ('ADJUSTMENT', 300, 'EUR', 2)"
        )
        assertEquals(listOf(PoolEntry.Adjustment(eur(300), null, Instant.ofEpochMilli(2))), pool.entries.first())
    }

    @Test
    fun `an allowance in an unknown currency or with a negative amount reads as not set up`() = runTest {
        execSql(
            "INSERT INTO allowance (id, monthly_minor, currency, accrual_day, start_date) " +
                "VALUES (1, 30000, 'XYZ', 1, '2026-10-23')"
        )
        assertNull(pool.allowance.first())
        execSql("UPDATE allowance SET currency = 'EUR', monthly_minor = -1")
        assertNull(pool.allowance.first())
        assertTrue(pool.setUp(monthly, 1, eur(0), on("2026-10-23"), zone))
    }

    @Test
    fun `an out-of-range stored accrual day is clamped, an unreadable start date reads as not set up`() = runTest {
        execSql(
            "INSERT INTO allowance (id, monthly_minor, currency, accrual_day, start_date) " +
                "VALUES (1, 30000, 'EUR', 31, '2026-10-23')"
        )
        assertEquals(28, pool.allowance.first()?.accrualDay)
        execSql("UPDATE allowance SET start_date = 'soon'")
        assertNull(pool.allowance.first())

        // It shows as not set up, so setting up again must work and replace the row.
        assertTrue(pool.setUp(monthly, 5, eur(0), on("2026-10-23"), zone))
        assertEquals(AllowanceSettings(monthly, 5, LocalDate.parse("2026-10-23")), pool.allowance.first())
    }

    @Test
    fun `changing the day before the first credit neither skips nor adds a month`() = runTest {
        pool.setUp(monthly, 10, eur(0), on("2026-10-12"), zone)
        pool.change(monthly, 20, eur(0), on("2026-10-15"), zone)

        assertEquals(0, pool.accrue(on("2026-10-20"), zone))
        assertEquals(1, pool.accrue(on("2026-11-20"), zone))
    }

    @Test
    fun `entries and allowance emit on every change`() = runTest {
        pool.entries.test {
            assertEquals(emptyList<PoolEntry>(), awaitItem())
            pool.setUp(monthly, 1, eur(10_000), on("2026-10-23"), zone)
            assertEquals(1, awaitItem().size)
            pool.adjust(eur(1), null, on("2026-10-24"))
            assertEquals(2, awaitItem().size)
        }
    }

    @Test
    fun `purchase rows round-trip with a negative stored amount`() = runTest {
        val entity = PoolEntry.Purchase(ItemId(3), eur(4_200), Instant.ofEpochMilli(5)).toEntity()
        assertEquals(-4_200, entity.amountMinor)
        assertEquals(PoolEntry.Purchase(ItemId(3), eur(4_200), Instant.ofEpochMilli(5)), entity.toDomain())
    }
}
