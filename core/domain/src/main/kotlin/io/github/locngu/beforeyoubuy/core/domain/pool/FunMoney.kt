package io.github.locngu.beforeyoubuy.core.domain.pool

import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.core.domain.money.relabeled
import io.github.locngu.beforeyoubuy.core.domain.money.total
import java.time.LocalDate

/** The fun money as Home and its screen show it (brief §4.5, §6.1). */
sealed interface FunMoney {
    /** No monthly amount yet: Home offers to set it up. */
    data object NotSetUp : FunMoney

    /** [nextAccrual] is when the next monthly amount is added; [startingBalance] the editable starting amount. */
    data class SetUp(
        val allowance: AllowanceSettings,
        val balance: PoolBalance,
        val nextAccrual: LocalDate,
        val startingBalance: Money
    ) : FunMoney
}

/**
 * The fun money from the stored [allowance] and ledger [entries], with what [savings] sets aside for wants as
 * committed (brief §4.5).
 *
 * Entries in another currency than the allowance are relabeled to it ([poolBalanceOf]): the allowance and the entries
 * arrive separately, so for a moment during a currency change they differ.
 */
fun funMoneyOf(allowance: AllowanceSettings?, entries: List<PoolEntry>, savings: Savings): FunMoney {
    if (allowance == null) return FunMoney.NotSetUp
    val currency = allowance.monthlyAmount.currency
    return FunMoney.SetUp(
        allowance = allowance,
        balance = poolBalanceOf(entries, committed = savings.committed(currency)),
        nextAccrual = nextAccrualDate(allowance, entries.lastAccruedMonth()),
        startingBalance = entries.filterIsInstance<PoolEntry.StartingBalance>()
            .map { it.change.relabeled(currency) }
            .total(currency)
    )
}
