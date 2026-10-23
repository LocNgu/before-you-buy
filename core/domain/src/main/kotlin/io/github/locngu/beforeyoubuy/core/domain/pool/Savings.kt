package io.github.locngu.beforeyoubuy.core.domain.pool

import io.github.locngu.beforeyoubuy.core.domain.item.Decision
import io.github.locngu.beforeyoubuy.core.domain.item.DecisionType
import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.Funding
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.coversPrice
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.core.domain.money.relabeled
import io.github.locngu.beforeyoubuy.core.domain.money.shownWholeUnits
import io.github.locngu.beforeyoubuy.core.domain.money.total
import java.time.Instant

/**
 * Money set aside for an item from what isn't set aside yet (brief §4.6): a positive [amount] sets aside, a negative
 * one takes back. Allocations are only ever appended.
 */
data class Allocation(val itemId: ItemId, val amount: Money, val at: Instant)

/**
 * The money set aside per item (brief §4.6, ADR-0046), from [savingsOf].
 *
 * Only an active want holds money: a need never does (ADR-0044), and a decision ends what an item holds. Rejecting
 * returns it to what isn't set aside; buying spends it ([poolPurchaseOf]).
 *
 * @property setUp fun money is set up, so money can be set aside.
 * @property required wants need their price set aside to be Ready ([AllowanceSettings.wantsNeedFunding]); never
 *   without fun money.
 * @property byItem what each active want holds, more than zero, in the item's currency.
 */
data class Savings(val setUp: Boolean, val required: Boolean, val byItem: Map<ItemId, Money>) {
    /** What [item] holds; zero if nothing. */
    fun allocatedFor(item: Item): Money =
        byItem[item.id]?.relabeled(item.price.currency) ?: Money(0, item.price.currency)

    /** Ready's money condition for [item] (brief §5). */
    fun fundingOf(item: Item): Funding = Funding(allocatedFor(item), required)

    /** Everything set aside for wants, in [currency] (brief §4.5 `committed`). */
    fun committed(currency: String): Money = byItem.values.map { it.relabeled(currency) }.total(currency)

    companion object {
        /** No fun money: nothing set aside, nothing required. */
        val NONE = Savings(setUp = false, required = false, byItem = emptyMap())
    }
}

/**
 * The savings of the [active] items from the stored [allowance] and [allocations]. Allocations of items that aren't
 * active wants don't count: that is how a decision releases them (brief §4.6). An item's amounts are relabeled to its
 * currency, as a currency change does (ADR-0033); a total below zero, which no write produces, counts as nothing.
 */
fun savingsOf(allowance: AllowanceSettings?, allocations: List<Allocation>, active: List<Item>): Savings {
    val wants = active.filter { it.status == ItemStatus.ACTIVE && it.kind == ItemKind.WANT }.associateBy { it.id }
    val byItem = allocations.groupBy { it.itemId }.mapNotNull { (id, rows) ->
        val currency = wants[id]?.price?.currency ?: return@mapNotNull null
        val total = rows.map { it.amount.relabeled(currency) }.total(currency)
        if (total.minorUnits > 0) id to total else null
    }.toMap()
    return Savings(setUp = allowance != null, required = allowance?.wantsNeedFunding == true, byItem = byItem)
}

/**
 * An item's savings as item detail, the wishlist and Home show them: [allocated] of [price] set aside.
 *
 * - [complete]: the price is covered as shown ([coversPrice]), the same rule that makes the item funded.
 * - [toGo]: exactly what's still missing, zero once complete; what "The rest" sets aside.
 * - [shownToGo]: what the screen says is still to go, the difference of the two amounts as shown (whole units), so
 *   "650 € of 1.600 €" always reads "950 € to go" and never "0 € to go" while saving (ADR-0046).
 * - [fraction]: 0–1 for a progress bar; a free item counts as complete.
 */
data class SavingsProgress(val allocated: Money, val price: Money) {
    val complete: Boolean = coversPrice(allocated, price)

    val toGo: Money = if (complete || allocated >= price) Money(0, price.currency) else price - allocated

    val shownToGo: Money
        get() = if (complete) {
            Money(0, price.currency)
        } else {
            Money.ofWholeUnits(price.shownWholeUnits() - allocated.shownWholeUnits(), price.currency)
        }

    val fraction: Float
        get() = if (complete) 1f else (allocated.minorUnits.toDouble() / price.minorUnits).toFloat().coerceIn(0f, 1f)
}

/** [item]'s progress, or null for a need, which is never saved for (ADR-0044). */
fun Savings.progressOf(item: Item): SavingsProgress? =
    if (item.kind == ItemKind.NEED) null else SavingsProgress(allocatedFor(item), item.price)

/** Outcome of [setAside] and [takeBack]. A refusal says why, so the screen can explain it. */
sealed interface AllocationResult {
    /** Allowed: store [allocation]. */
    data class Allowed(val allocation: Allocation) : AllocationResult

    sealed interface Refused : AllocationResult

    /** Fun money isn't set up, so there is nothing to set aside. */
    data object NotSetUp : Refused

    /** The item was purchased or rejected meanwhile. */
    data object NotActive : Refused

    /** Needs never hold fun money (ADR-0044). */
    data object IsNeed : Refused

    /** Zero or less. */
    data object NotPositive : Refused

    /** Setting aside more than isn't set aside yet: allocation never takes the balance below zero (brief §4.5). */
    data object MoreThanNotSetAside : Refused

    /** Setting aside more than is still to go: an item never holds more than its price (ADR-0046). */
    data object MoreThanToGo : Refused

    /** Taking back more than the item holds. */
    data object MoreThanSetAside : Refused
}

/**
 * Sets [amount] aside for [item], which holds [allocated], at [at] (brief §4.6): an active want, at most what [balance]
 * hasn't set aside yet and what's still to go of its price (ADR-0046). [balance] is null while fun money isn't set up.
 * Saving can start on day 1, while the item still cools off. Amounts must be in the item's currency; anything else
 * throws.
 */
fun setAside(item: Item, amount: Money, balance: PoolBalance?, allocated: Money, at: Instant): AllocationResult {
    require(amount.currency == item.price.currency) { "$amount isn't in the item's currency ${item.price.currency}" }
    return when {
        balance == null -> AllocationResult.NotSetUp
        item.status != ItemStatus.ACTIVE -> AllocationResult.NotActive
        item.kind == ItemKind.NEED -> AllocationResult.IsNeed
        amount.minorUnits <= 0 -> AllocationResult.NotPositive
        amount > balance.unallocated.relabeled(amount.currency) -> AllocationResult.MoreThanNotSetAside
        amount > SavingsProgress(allocated, item.price).toGo -> AllocationResult.MoreThanToGo
        else -> AllocationResult.Allowed(Allocation(item.id, amount, at))
    }
}

/**
 * Takes [amount] back from what [item] holds ([allocated]) at [at]; it returns to what isn't set aside. [amount] must
 * be in the item's currency; anything else throws.
 */
fun takeBack(item: Item, amount: Money, allocated: Money, at: Instant): AllocationResult {
    require(amount.currency == item.price.currency) { "$amount isn't in the item's currency ${item.price.currency}" }
    return when {
        item.status != ItemStatus.ACTIVE -> AllocationResult.NotActive
        amount.minorUnits <= 0 -> AllocationResult.NotPositive
        amount > allocated -> AllocationResult.MoreThanSetAside
        else -> AllocationResult.Allowed(Allocation(item.id, Money(-amount.minorUnits, amount.currency), at))
    }
}

/**
 * The ledger entry for buying [item] with [decision] (brief §4.6): a want's actual price is spent from the fun money,
 * whatever was set aside. Its savings are spent with it, and what they don't cover comes from what isn't set aside.
 * Null for anything else: a need never touches the fun money (ADR-0044), and nothing is spent while fun money isn't
 * set up ([funMoneySetUp], ADR-0046).
 */
fun poolPurchaseOf(item: Item, decision: Decision, funMoneySetUp: Boolean): PoolEntry.Purchase? {
    val paid = decision.actualPrice ?: return null
    if (!funMoneySetUp || item.kind == ItemKind.NEED || decision.type == DecisionType.BUY_NEED) return null
    return PoolEntry.Purchase(item.id, paid, decision.decidedAt)
}

/**
 * What isn't set aside after buying a want for [paid] (brief §4.6): its [allocated] savings are spent first, so paying
 * less returns the rest and paying more takes the difference. In [paid]'s currency.
 */
fun leftAfterPurchase(balance: PoolBalance, allocated: Money, paid: Money): Money =
    balance.unallocated.relabeled(paid.currency) + allocated.relabeled(paid.currency) - paid

/**
 * Whether buying a want for [paid] takes more than it held and leaves less than nothing not set aside
 * ([leftAfterPurchase]), so the user confirms first (issue #23). Paying no more than was set aside never asks.
 */
fun purchaseNeedsConfirmation(balance: PoolBalance, allocated: Money, paid: Money): Boolean =
    paid > allocated.relabeled(paid.currency) && leftAfterPurchase(balance, allocated, paid).minorUnits < 0

/**
 * The most that can be set aside for an item with [progress] now ([setAside]): what [balance] hasn't set aside
 * (never below zero) and what's still to go, whichever is less. Zero disables "Set aside".
 */
fun setAsideLimit(balance: PoolBalance, progress: SavingsProgress): Money {
    val currency = progress.price.currency
    return minOf(maxOf(balance.unallocated.relabeled(currency), Money(0, currency)), progress.toGo)
}

/** What "The rest" sets aside (ADR-0046): all that [limit] ([setAsideLimit]) allows; null when that's nothing. */
fun restToSetAside(limit: Money): Money? = limit.takeIf { it.minorUnits > 0 }

/** The quick amounts the sheet offers, in whole units ([QUICK_AMOUNTS]), those that fit [limit]. */
fun quickAmounts(limit: Money): List<Money> =
    QUICK_AMOUNTS.map { Money.ofWholeUnits(it, limit.currency) }.filter { it <= limit }

/** The sheet's quick amounts in whole units: 50 and 100 (issue #23). */
val QUICK_AMOUNTS: List<Long> = listOf(50L, 100L)

/** Why a typed amount can't be set aside or taken back. */
enum class AmountProblem {
    ZERO,

    /** More than the sheet's limit ([setAsideLimit], or what the item holds). */
    MORE_THAN_LIMIT
}

/** Whether [amount] can be moved within [limit]: null if it can. */
fun checkAmount(amount: Money, limit: Money): AmountProblem? = when {
    amount.minorUnits <= 0 -> AmountProblem.ZERO
    amount > limit -> AmountProblem.MORE_THAN_LIMIT
    else -> null
}

/**
 * The take-back to store when an edit turns [current], a want holding [allocated], into a need (ADR-0044): all of it
 * goes back to what isn't set aside, so it doesn't return if the item becomes a want again. Dated when it was marked
 * as a need. Null if the kind doesn't change that way or nothing is set aside.
 */
fun releaseWhenMarkedAsNeed(current: Item, edited: Item, allocated: Money): Allocation? {
    if (current.kind != ItemKind.WANT || edited.kind != ItemKind.NEED || allocated.minorUnits <= 0) return null
    return Allocation(
        current.id,
        Money(-allocated.minorUnits, allocated.currency),
        edited.markedAsNeedAt ?: edited.addedAt
    )
}
