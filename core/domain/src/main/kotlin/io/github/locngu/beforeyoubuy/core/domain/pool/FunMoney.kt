package io.github.locngu.beforeyoubuy.core.domain.pool

import io.github.locngu.beforeyoubuy.core.domain.money.Money
import java.time.LocalDate

/** The fun money as Home and its screen show it (brief §4.5, §6.1). */
sealed interface FunMoney {
    /** No monthly amount yet: Home offers to set it up. */
    data object NotSetUp : FunMoney

    /** [nextAccrual] is when the next monthly amount is added. */
    data class SetUp(val allowance: AllowanceSettings, val balance: PoolBalance, val nextAccrual: LocalDate) :
        FunMoney
}

/**
 * The fun money from the stored [allowance] and ledger [entries], with nothing set aside yet (#23 adds savings).
 * Null while a currency change is half read (the allowance and the entries arrive separately), so a screen keeps
 * what it showed instead of mixing currencies.
 */
fun funMoneyOf(allowance: AllowanceSettings?, entries: List<PoolEntry>): FunMoney? {
    if (allowance == null) return FunMoney.NotSetUp
    val currency = allowance.monthlyAmount.currency
    if (entries.any { it.change.currency != currency }) return null
    return FunMoney.SetUp(
        allowance = allowance,
        balance = poolBalanceOf(entries, committed = Money(0, currency)),
        nextAccrual = nextAccrualDate(allowance, entries.lastAccruedMonth())
    )
}
