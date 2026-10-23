package io.github.locngu.beforeyoubuy.core.domain.pool

import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

/**
 * Stores the monthly allowance, the pool's ledger and the money set aside per item (brief §4.5, §4.6). Implemented in
 * `:core:data`, next to the items, so a currency change relabels them in one transaction (ADR-0033) and a purchase
 * is spent from the pool together with its decision. Every amount must be in the app's currency; anything else
 * throws.
 */
interface PoolRepository {
    /** The allowance, or null until it is set up; emits again on every change. */
    val allowance: Flow<AllowanceSettings?>

    /** Every ledger entry, oldest first; emits again on every change. */
    val entries: Flow<List<PoolEntry>>

    /**
     * Every allocation of every item, oldest first, including those of decided items, which no longer count
     * ([savingsOf]); emits again on every change.
     */
    val allocations: Flow<List<Allocation>>

    /**
     * Sets the allowance up, starting at today in [zone], with [startingBalance] as the first entry (none if zero),
     * then credits whatever is already due (the accrual day may be today). Returns false and writes nothing if it
     * was already set up meanwhile.
     */
    suspend fun setUp(
        monthlyAmount: Money,
        accrualDay: Int,
        startingBalance: Money,
        wantsNeedFunding: Boolean,
        now: Instant,
        zone: ZoneId
    ): Boolean

    /**
     * Changes the amount, accrual day, starting amount and the "Wants need their price set aside" switch. In one
     * transaction it first credits every month due under the old settings, so the new amount applies from the next
     * accrual on (issue #22). The first credited month stays ([AllowanceSettings.changed]). The starting amount
     * replaces the stored one (none if zero): it's a setting, not an event (ADR-0045). Throws if the allowance isn't
     * set up.
     */
    suspend fun change(
        monthlyAmount: Money,
        accrualDay: Int,
        startingBalance: Money,
        wantsNeedFunding: Boolean,
        now: Instant,
        zone: ZoneId
    )

    /**
     * Sets [amount] aside for the stored item [itemId] at [at] if [setAside] allows it, checked against the stored
     * item, ledger and allocations in one transaction, so two at once can't both use the same money. Returns the
     * result; only [AllocationResult.Allowed] wrote anything. [AllocationResult.NotActive] if the item is gone.
     */
    suspend fun setAside(itemId: ItemId, amount: Money, at: Instant): AllocationResult

    /** Takes [amount] back from the stored item [itemId] at [at] if [takeBack] allows it; like [setAside]. */
    suspend fun takeBack(itemId: ItemId, amount: Money, at: Instant): AllocationResult

    /** Appends an [PoolEntry.Adjustment]. Whether to confirm first is the caller's ([needsConfirmation]). */
    suspend fun adjust(change: Money, note: String?, at: Instant)

    /**
     * Credits every month [accrualsDue] at [now] in [zone] and returns how many. Idempotent and safe to run
     * concurrently (app start and the daily worker): a month is never credited twice. Nothing without an allowance.
     */
    suspend fun accrue(now: Instant, zone: ZoneId): Int
}
