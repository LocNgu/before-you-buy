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
        assertEquals(FunMoney.NotSetUp, funMoneyOf(null, emptyList(), Savings.NONE))
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
                nextAccrual = LocalDate.parse("2026-12-01"),
                startingBalance = Money(10_000, "EUR")
            ),
            funMoneyOf(allowance, entries, Savings.NONE)
        )
    }

    @Test
    fun `entries in another currency are relabeled instead of hiding the fun money`() {
        // A half-read currency change, or a stray row: the numbers stay, as a currency change keeps them.
        val inYen = allowance.copy(monthlyAmount = Money(300, "JPY"))
        val money = funMoneyOf(
            inYen,
            listOf(PoolEntry.StartingBalance(Money(5_050, "EUR"), at)),
            Savings.NONE
        ) as FunMoney.SetUp
        assertEquals(Money(51, "JPY"), money.balance.pool)
    }
}
