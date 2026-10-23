package io.github.locngu.beforeyoubuy.core.domain.pool

import io.github.locngu.beforeyoubuy.core.domain.money.Money
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FunMoneyTest {
    private val at = Instant.parse("2026-11-01T10:00:00Z")
    private val allowance = AllowanceSettings(Money(30_000, "EUR"), 1, LocalDate.parse("2026-10-23"))

    @Test
    fun `without an allowance it isn't set up, whatever the ledger holds`() {
        assertEquals(FunMoney.NotSetUp, funMoneyOf(null, emptyList()))
    }

    @Test
    fun `set up shows the balance with nothing set aside and the next accrual`() {
        val entries = listOf(
            PoolEntry.StartingBalance(Money(10_000, "EUR"), at),
            PoolEntry.Accrual(YearMonth.of(2026, 11), Money(30_000, "EUR"), at)
        )
        assertEquals(
            FunMoney.SetUp(
                allowance = allowance,
                balance = PoolBalance(Money(40_000, "EUR"), Money(0, "EUR")),
                nextAccrual = LocalDate.parse("2026-12-01")
            ),
            funMoneyOf(allowance, entries)
        )
    }

    @Test
    fun `a half-read currency change shows nothing new instead of throwing`() {
        val relabeled = allowance.copy(monthlyAmount = Money(30_000, "USD"))
        assertNull(funMoneyOf(relabeled, listOf(PoolEntry.StartingBalance(Money(1, "EUR"), at))))
    }
}
