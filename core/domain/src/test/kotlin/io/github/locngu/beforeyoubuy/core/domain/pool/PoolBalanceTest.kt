package io.github.locngu.beforeyoubuy.core.domain.pool

import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PoolBalanceTest {
    private val at = Instant.parse("2026-10-23T10:00:00Z")
    private val zero = eur(0)

    private fun eur(minor: Long) = Money(minor, "EUR")

    @Test
    fun `the pool is starting balance plus accruals plus adjustments minus purchases`() {
        val entries = listOf(
            PoolEntry.StartingBalance(eur(10_000), at),
            PoolEntry.Accrual(YearMonth.of(2026, 11), eur(30_000), at),
            PoolEntry.Adjustment(eur(2_550), "Birthday money", at),
            PoolEntry.Adjustment(eur(-1_000), null, at),
            PoolEntry.Purchase(ItemId(1), eur(12_000), at)
        )
        val balance = poolBalanceOf(entries, zero)
        assertEquals(eur(29_550), balance.pool)
        assertEquals(zero, balance.committed)
        assertEquals(eur(29_550), balance.unallocated)
    }

    @Test
    fun `nothing set aside is zero`() {
        assertEquals(PoolBalance(zero, zero), poolBalanceOf(emptyList(), zero))
        assertEquals(zero, poolBalanceOf(emptyList(), zero).unallocated)
    }

    @Test
    fun `what is set aside isn't unallocated`() {
        val balance = poolBalanceOf(listOf(PoolEntry.StartingBalance(eur(30_000), at)), eur(20_000))
        assertEquals(eur(30_000), balance.pool)
        assertEquals(eur(10_000), balance.unallocated)
    }

    @Test
    fun `mixed currencies throw`() {
        assertThrows(IllegalArgumentException::class.java) {
            poolBalanceOf(listOf(PoolEntry.StartingBalance(Money(100, "USD"), at)), zero)
        }
    }

    @Test
    fun `taking out more than is left needs a confirmation`() {
        val balance = PoolBalance(eur(5_000), zero)
        assertFalse(needsConfirmation(balance, eur(-5_000)))
        assertTrue(needsConfirmation(balance, eur(-5_001)))
        // What is set aside counts: only 1.000 is left to take out.
        assertTrue(needsConfirmation(PoolBalance(eur(5_000), eur(4_000)), eur(-2_000)))
    }

    @Test
    fun `adding money never needs a confirmation`() {
        assertFalse(needsConfirmation(PoolBalance(eur(0), zero), eur(100)))
        assertFalse(needsConfirmation(PoolBalance(eur(-5_000), zero), eur(100)))
        assertFalse(needsConfirmation(PoolBalance(eur(0), zero), zero))
    }

    @Test
    fun `entries that only add can't be negative`() {
        assertThrows(IllegalArgumentException::class.java) { PoolEntry.StartingBalance(eur(-1), at) }
        assertThrows(IllegalArgumentException::class.java) { PoolEntry.Accrual(YearMonth.of(2026, 11), eur(-1), at) }
        assertThrows(IllegalArgumentException::class.java) { PoolEntry.Purchase(null, eur(-1), at) }
    }

    @Test
    fun `the last accrued month is the newest accrual`() {
        assertNull(emptyList<PoolEntry>().lastAccruedMonth())
        val entries = listOf(
            PoolEntry.Accrual(YearMonth.of(2026, 12), eur(1), at),
            PoolEntry.Adjustment(eur(1), null, at),
            PoolEntry.Accrual(YearMonth.of(2026, 11), eur(1), at)
        )
        assertEquals(YearMonth.of(2026, 12), entries.lastAccruedMonth())
    }

    @Test
    fun `a new amount applies from the next accrual, missed months keep the old one`() {
        // The repository's change(): credit what's due under the old settings, then store the new ones.
        val zone = ZoneOffset.UTC
        val old = AllowanceSettings(eur(30_000), 1, LocalDate.parse("2026-10-23"))
        val now = Instant.parse("2027-01-15T10:00:00Z")
        val ledger = accrualsDue(old, null, now, zone).map { PoolEntry.Accrual(it, old.monthlyAmount, now) }
        val new = old.copy(monthlyAmount = eur(50_000))
        assertEquals(emptyList<YearMonth>(), accrualsDue(new, ledger.lastAccruedMonth(), now, zone))
        assertEquals(listOf(eur(30_000), eur(30_000), eur(30_000)), ledger.map { it.change })
        assertEquals(
            listOf(YearMonth.of(2027, 2)),
            accrualsDue(new, ledger.lastAccruedMonth(), Instant.parse("2027-02-01T10:00:00Z"), zone)
        )
    }
}
