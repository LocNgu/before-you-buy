package io.github.locngu.beforeyoubuy.core.domain.pool

import io.github.locngu.beforeyoubuy.core.domain.money.Money
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

/**
 * Stores the monthly allowance and the pool's ledger (brief §4.5). Implemented in `:core:data`, next to the items,
 * so a currency change relabels them in one transaction (ADR-0033). Every amount must be in the app's currency;
 * anything else throws.
 */
interface PoolRepository {
    /** The allowance, or null until it is set up; emits again on every change. */
    val allowance: Flow<AllowanceSettings?>

    /** Every ledger entry, oldest first; emits again on every change. */
    val entries: Flow<List<PoolEntry>>

    /**
     * Sets the allowance up, starting at today in [zone], with [startingBalance] as the first entry (none if zero),
     * then credits whatever is already due (the accrual day may be today). Returns false and writes nothing if it
     * was already set up meanwhile.
     */
    suspend fun setUp(
        monthlyAmount: Money,
        accrualDay: Int,
        startingBalance: Money,
        now: Instant,
        zone: ZoneId
    ): Boolean

    /**
     * Changes the amount and accrual day. In one transaction it first credits every month due under the old
     * settings, so the new amount applies from the next accrual on (issue #22). The first credited month stays
     * ([AllowanceSettings.changed]). Throws if the allowance isn't set up.
     */
    suspend fun change(monthlyAmount: Money, accrualDay: Int, now: Instant, zone: ZoneId)

    /** Appends an [PoolEntry.Adjustment]. Whether to confirm first is the caller's ([needsConfirmation]). */
    suspend fun adjust(change: Money, note: String?, at: Instant)

    /**
     * Credits every month [accrualsDue] at [now] in [zone] and returns how many. Idempotent and safe to run
     * concurrently (app start and the daily worker): a month is never credited twice. Nothing without an allowance.
     */
    suspend fun accrue(now: Instant, zone: ZoneId): Int
}
