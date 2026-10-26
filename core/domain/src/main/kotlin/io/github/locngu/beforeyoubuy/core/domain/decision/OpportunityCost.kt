package io.github.locngu.beforeyoubuy.core.domain.decision

import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.core.domain.money.relabeled
import io.github.locngu.beforeyoubuy.core.domain.money.total
import io.github.locngu.beforeyoubuy.core.domain.pool.FunMoney
import io.github.locngu.beforeyoubuy.core.domain.pool.Savings
import io.github.locngu.beforeyoubuy.core.domain.priorities.prioritiesOf
import java.math.BigInteger
import java.math.RoundingMode

/**
 * One line of "What else this money could do" (brief §7, research §1 "Opportunity cost neglect"): the alternative,
 * described concretely. Lines only use what the user entered; without the inputs a line needs there is no line.
 */
sealed interface OpportunityCostLine {
    /** A line about another priority: "Don't buy — keep … for your [priority] (#[rank])" can name it. */
    sealed interface AboutPriority : OpportunityCostLine {
        val priority: Item
        val rank: Int
    }

    /**
     * Buying delays [priority] by about [months] monthly amounts, ≥ 1: the priorities are funded in rank order from
     * what isn't set aside yet plus one monthly amount a month, once with and once without this purchase.
     */
    data class DelaysPriority(override val priority: Item, override val rank: Int, val months: Int) : AboutPriority

    /**
     * The price is [percent] % of [priority]'s price, rounded; 0 means less than 1 %. Only up to 100 %: a pricier item
     * isn't "300 % of" a cheaper goal, and "Don't buy" shouldn't keep its price for one.
     */
    data class ShareOfGoal(override val priority: Item, override val rank: Int, val percent: Long) : AboutPriority

    /**
     * The price in monthly amounts of fun money: 0 means less than a month; otherwise rounded to whole months,
     * [exact] when the price is exactly that many.
     */
    data class MonthsOfAllowance(val months: Long, val exact: Boolean) : OpportunityCostLine

    /**
     * [items], the cheapest other items on the list, cost [total] together, no more than the price; at least two.
     * [all]: they are all the other items on the list.
     */
    data class SmallerItemsCombined(val items: List<Item>, val total: Money, val all: Boolean) : OpportunityCostLine
}

/** How many lines item detail shows (issue #25: a compact version); the decision screen shows them all. */
const val OPPORTUNITY_COST_DETAIL_LINES = 2

/**
 * What else [item]'s price could do (brief §7), at most one line per kind, most relevant first (ADR-0048): a delay to a
 * priority, the share of the highest-ranked other priority, months of fun money, the cheapest other items combined.
 *
 * [active] are the user's active items (the item itself may be among them), [savings] and [funMoney] as the screens
 * read them. A need never uses fun money (ADR-0044), so it only gets the price
 * comparisons. A free item gets no lines. Amounts in another currency (a half-written currency change) are relabeled
 * to the item's, as elsewhere (ADR-0033).
 */
fun opportunityCostOf(item: Item, active: List<Item>, savings: Savings, funMoney: FunMoney): List<OpportunityCostLine> {
    val price = item.price
    if (price.minorUnits <= 0) return emptyList()
    val currency = price.currency
    val others = active.filter { it.status == ItemStatus.ACTIVE && it.id != item.id }
    val priorities = prioritiesOf(others)
    // Only a want comes out of the fun money, and only with a monthly amount to measure it in.
    val money = (funMoney as? FunMoney.SetUp)?.takeIf {
        item.kind == ItemKind.WANT && it.allowance.monthlyAmount.minorUnits > 0
    }
    return listOfNotNull(
        money?.let { delayOf(item, priorities, savings, it) },
        shareOf(price, priorities),
        money?.let { monthsOf(price, it.allowance.monthlyAmount.relabeled(currency)) },
        smallerItemsOf(price, others)
    )
}

/** The priority "Don't buy — keep …" names: the top line's, if it is about a priority (ADR-0048). */
fun List<OpportunityCostLine>.keepFor(): OpportunityCostLine.AboutPriority? =
    firstOrNull() as? OpportunityCostLine.AboutPriority

/**
 * The highest-ranked priority that buying [item] delays by at least one monthly amount. Buying spends the price
 * (its own savings first, the rest from what isn't set aside); not buying returns its savings, so the two differ by
 * the full price. A priority that holds its price already isn't delayed.
 */
private fun delayOf(
    item: Item,
    priorities: List<Item>,
    savings: Savings,
    funMoney: FunMoney.SetUp
): OpportunityCostLine.DelaysPriority? {
    val currency = item.price.currency
    val monthly = funMoney.allowance.monthlyAmount.relabeled(currency).big()
    val notBuying = funMoney.balance.unallocated.relabeled(currency).big() + savings.allocatedFor(item).big()
    val buying = notBuying - item.price.big()
    var needed = BigInteger.ZERO
    priorities.forEach { priority ->
        val rest = (priority.price.relabeled(currency).big() - savings.allocatedFor(priority).big())
            .max(BigInteger.ZERO)
        // What a priority already holds is its own: going below zero elsewhere can't delay it.
        if (rest.signum() == 0) return@forEach
        needed += rest
        val months = monthsToFund(needed, buying, monthly) - monthsToFund(needed, notBuying, monthly)
        if (months >= BigInteger.ONE) {
            return OpportunityCostLine.DelaysPriority(priority, rankOf(priority), months.toIntCapped())
        }
    }
    return null
}

/** Monthly amounts until [have] reaches [needed]; none if it already does. */
private fun monthsToFund(needed: BigInteger, have: BigInteger, monthly: BigInteger): BigInteger {
    if (needed <= have) return BigInteger.ZERO
    val (months, rest) = (needed - have).divideAndRemainder(monthly)
    return if (rest.signum() == 0) months else months + BigInteger.ONE
}

private fun shareOf(price: Money, priorities: List<Item>): OpportunityCostLine.ShareOfGoal? {
    val goal = priorities.firstOrNull() ?: return null
    val goalPrice = goal.price.relabeled(price.currency).big()
    if (goalPrice.signum() <= 0) return null
    val percent = price.big().multiply(BigInteger.valueOf(100)).toBigDecimal()
        .divide(goalPrice.toBigDecimal(), 0, RoundingMode.HALF_UP)
        .toBigInteger()
    if (percent > BigInteger.valueOf(100)) return null
    return OpportunityCostLine.ShareOfGoal(goal, rankOf(goal), percent.toLong())
}

private fun monthsOf(price: Money, monthly: Money): OpportunityCostLine.MonthsOfAllowance {
    val (whole, rest) = price.big().divideAndRemainder(monthly.big())
    if (whole.signum() == 0) return OpportunityCostLine.MonthsOfAllowance(0, exact = false)
    val rounded = price.big().toBigDecimal().divide(monthly.big().toBigDecimal(), 0, RoundingMode.HALF_UP)
    return OpportunityCostLine.MonthsOfAllowance(rounded.toBigInteger().toLongCapped(), exact = rest.signum() == 0)
}

/** The largest set of the cheapest other items that together cost no more than [price]; two or more. */
private fun smallerItemsOf(price: Money, others: List<Item>): OpportunityCostLine.SmallerItemsCombined? {
    val priced = others
        .filter { it.price.minorUnits > 0 }
        .sortedWith(compareBy({ it.price.relabeled(price.currency) }, { it.addedAt }, { it.id.value }))
    var sum = BigInteger.ZERO
    val fitting = priced.takeWhile {
        sum += it.price.relabeled(price.currency).big()
        sum <= price.big()
    }
    if (fitting.size < 2) return null
    return OpportunityCostLine.SmallerItemsCombined(
        items = fitting,
        total = fitting.map { it.price.relabeled(price.currency) }.total(price.currency),
        all = fitting.size == others.size
    )
}

private fun rankOf(priority: Item): Int = checkNotNull(priority.prioritySlot)

private fun Money.big(): BigInteger = BigInteger.valueOf(minorUnits)

private fun BigInteger.toLongCapped(): Long = min(BigInteger.valueOf(Long.MAX_VALUE)).toLong()

private fun BigInteger.toIntCapped(): Int = min(BigInteger.valueOf(Int.MAX_VALUE.toLong())).toInt()
