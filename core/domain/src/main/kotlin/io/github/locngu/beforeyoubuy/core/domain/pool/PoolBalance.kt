package io.github.locngu.beforeyoubuy.core.domain.pool

import io.github.locngu.beforeyoubuy.core.domain.money.Money

/**
 * The fun money as Home shows it (brief §4.5, §6.1; labels in ADR-0045): [pool] is everything in it, [committed]
 * what is set aside for wants, [unallocated] what isn't set aside yet. All in one currency.
 */
data class PoolBalance(val pool: Money, val committed: Money) {
    val unallocated: Money = pool - committed
}

/**
 * The balance of the ledger [entries] with [committed] set aside for active wants. Until savings exist (#23),
 * callers pass zero. Every amount must be in [committed]'s currency; anything else throws.
 */
fun poolBalanceOf(entries: Iterable<PoolEntry>, committed: Money): PoolBalance =
    PoolBalance(pool = entries.fold(Money(0, committed.currency)) { sum, entry -> sum + entry.change }, committed)

/**
 * Whether adjusting by [change] takes money out and leaves less than nothing not set aside, so the user confirms
 * first (issue #22). Adding money never needs a confirmation, even while the balance is below zero.
 */
fun needsConfirmation(balance: PoolBalance, change: Money): Boolean =
    change.minorUnits < 0 && (balance.unallocated + change).minorUnits < 0
