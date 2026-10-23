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
 * The fun money from the stored [allowance] and ledger [entries], with nothing set aside yet (#23 adds savings).
 *
 * An entry in another currency than the allowance is relabeled to it, as a currency change does (ADR-0033): that
 * happens for a moment while a currency change is read (the allowance and the entries arrive separately), and must
 * never hide the fun money if a stray row stays (ADR-0032).
 */
fun funMoneyOf(allowance: AllowanceSettings?, entries: List<PoolEntry>): FunMoney {
    if (allowance == null) return FunMoney.NotSetUp
    val currency = allowance.monthlyAmount.currency
    return FunMoney.SetUp(
        allowance = allowance,
        balance = PoolBalance(
            pool = entries.map { it.change.relabeled(currency) }.total(currency),
            committed = Money(0, currency)
        ),
        nextAccrual = nextAccrualDate(allowance, entries.lastAccruedMonth()),
        startingBalance = entries.filterIsInstance<PoolEntry.StartingBalance>()
            .map { it.change.relabeled(currency) }
            .total(currency)
    )
}
