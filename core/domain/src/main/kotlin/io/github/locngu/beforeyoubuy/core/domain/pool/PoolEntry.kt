package io.github.locngu.beforeyoubuy.core.domain.pool

import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import java.time.Instant
import java.time.YearMonth

/**
 * One line of the pool's append-only ledger (brief §4.5): `pool = startingBalance + accruals + adjustments −
 * purchases` is the sum of every entry's [change]. Entries are never edited or removed; a correction is an
 * [Adjustment]. Needs never appear here (ADR-0044).
 */
sealed interface PoolEntry {
    /** What this entry adds to the pool; negative for what it takes out. */
    val change: Money

    val at: Instant

    /** The balance the user started with, entered once at setup; ≥ 0. */
    data class StartingBalance(override val change: Money, override val at: Instant) : PoolEntry {
        init {
            require(change.minorUnits >= 0) { "A starting balance can't be negative, was $change" }
        }
    }

    /** [month]'s allowance, credited at [at]; ≥ 0. At most one per month. */
    data class Accrual(val month: YearMonth, override val change: Money, override val at: Instant) : PoolEntry {
        init {
            require(change.minorUnits >= 0) { "An accrual can't be negative, was $change" }
        }
    }

    /** A manual correction, + or −, so the pool can follow reality (ADR-0010). */
    data class Adjustment(override val change: Money, val note: String?, override val at: Instant) : PoolEntry

    /**
     * A want bought from the pool for [paid] (#23). [itemId] is null once the history entry was deleted; the money
     * stays spent.
     */
    data class Purchase(val itemId: ItemId?, val paid: Money, override val at: Instant) : PoolEntry {
        init {
            require(paid.minorUnits >= 0) { "A purchase can't pay a negative amount, was $paid" }
        }

        override val change: Money get() = Money(-paid.minorUnits, paid.currency)
    }
}

/** The newest month already credited, or null if none is: where [accrualsDue] continues. */
fun Iterable<PoolEntry>.lastAccruedMonth(): YearMonth? = filterIsInstance<PoolEntry.Accrual>().maxOfOrNull { it.month }
