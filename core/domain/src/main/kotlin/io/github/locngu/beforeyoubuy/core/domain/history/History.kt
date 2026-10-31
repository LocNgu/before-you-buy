package io.github.locngu.beforeyoubuy.core.domain.history

import io.github.locngu.beforeyoubuy.core.domain.coolingoff.coolingOffDaysFor
import io.github.locngu.beforeyoubuy.core.domain.item.Decision
import io.github.locngu.beforeyoubuy.core.domain.item.DecisionType
import io.github.locngu.beforeyoubuy.core.domain.item.HistoryEntry
import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.item.Reflection
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.core.domain.money.total
import io.github.locngu.beforeyoubuy.core.domain.settings.Policy
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Which decisions history lists (issue #19): by the calendar month or year of the decision in the device zone. */
enum class HistoryPeriod {
    THIS_MONTH,
    THIS_YEAR,
    ALL
    ;

    /** Whether a decision made on [day] falls into this period as seen on [today]. */
    fun includes(day: LocalDate, today: LocalDate): Boolean = when (this) {
        THIS_MONTH -> day.year == today.year && day.month == today.month
        THIS_YEAR -> day.year == today.year
        ALL -> true
    }
}

/**
 * One purchase in "Bought" (brief §6.6).
 *
 * @property paid what was really paid ([Decision.actualPrice]).
 * @property wishlistPrice the price on the wishlist, only where it differs from [paid].
 * @property boughtOn the purchase date, which may be dated back (ADR-0040).
 * @property consideredForDays calendar days from adding the item to buying it (ADR-0018); 0 on the same day.
 * @property early bought before it was Ready (ADR-0006).
 * @property need bought as a need (ADR-0008).
 */
data class BoughtEntry(
    val item: Item,
    val decision: Decision,
    val paid: Money,
    val wishlistPrice: Money?,
    val boughtOn: LocalDate,
    val consideredForDays: Int,
    val early: Boolean,
    val need: Boolean
)

/**
 * One item in "Decided against" (brief §6.6, ADR-0012).
 *
 * @property price the item's price when the user let it go.
 * @property decidedOn the day of the reject.
 * @property note the optional "Why not?".
 */
data class DecidedAgainstEntry(
    val item: Item,
    val decision: Decision,
    val price: Money,
    val decidedOn: LocalDate,
    val note: String?
)

/**
 * "Decided against" for a period: the [entries], newest first, and their [total], a plain sum shown as "You decided
 * against 3,420 € of purchases", never as savings (ADR-0012).
 */
data class DecidedAgainst(val entries: List<DecidedAgainstEntry>, val total: Money)

/**
 * "Bought" in [period] as seen at [now] in [zone], in the order of [entries] (the repository's: newest first).
 * Entries that aren't a purchased item ended by a purchase are left out (stored rows are read defensively,
 * ADR-0032). Every amount must be in [currency], the app's one currency (ADR-0033); anything else throws.
 */
fun boughtOf(
    entries: List<HistoryEntry>,
    currency: String,
    period: HistoryPeriod,
    now: Instant,
    zone: ZoneId
): List<BoughtEntry> {
    val today = now.atZone(zone).toLocalDate()
    return entries
        .mapNotNull { (item, decision) -> boughtEntryOf(item, decision, zone) }
        .onEach { requireCurrency(currency, it.item.price, it.paid) }
        .filter { period.includes(it.boughtOn, today) }
}

/**
 * "Decided against" in [period] as seen at [now] in [zone], in the order of [entries], with the total of their
 * prices. Entries that aren't a rejected item ended by a reject are left out, as in [boughtOf]. Every price must be
 * in [currency], the app's one currency (ADR-0033): a total over several currencies would be meaningless, so
 * anything else throws. The total is exact; it is rounded only when shown (ADR-0034).
 */
fun decidedAgainstOf(
    entries: List<HistoryEntry>,
    currency: String,
    period: HistoryPeriod,
    now: Instant,
    zone: ZoneId
): DecidedAgainst {
    val today = now.atZone(zone).toLocalDate()
    val listed = entries
        .mapNotNull { (item, decision) -> decidedAgainstEntryOf(item, decision, zone) }
        .onEach { requireCurrency(currency, it.price) }
        .filter { period.includes(it.decidedOn, today) }
    return DecidedAgainst(listed, listed.map { it.price }.total(currency))
}

/** One history entry with its reflection answers, for the read-only detail (issue #19). */
sealed interface HistoryDetail {
    val item: Item
    val reflection: Reflection?

    data class Bought(val entry: BoughtEntry, override val reflection: Reflection?) : HistoryDetail {
        override val item: Item get() = entry.item
    }

    data class DecidedAgainst(val entry: DecidedAgainstEntry, override val reflection: Reflection?) : HistoryDetail {
        override val item: Item get() = entry.item
    }
}

/**
 * The history detail of [item] in [zone], with its [decisions] (oldest first; the last one ended it, ADR-0032) and
 * reflection answers. Null while the item is active, or if its ending decision doesn't fit its status.
 */
fun historyDetailOf(item: Item, decisions: List<Decision>, reflection: Reflection?, zone: ZoneId): HistoryDetail? {
    val ending = decisions.lastOrNull() ?: return null
    return when (item.status) {
        ItemStatus.ACTIVE -> null

        ItemStatus.PURCHASED -> boughtEntryOf(item, ending, zone)?.let { HistoryDetail.Bought(it, reflection) }

        ItemStatus.REJECTED ->
            decidedAgainstEntryOf(item, ending, zone)?.let { HistoryDetail.DecidedAgainst(it, reflection) }
    }
}

private fun boughtEntryOf(item: Item, decision: Decision, zone: ZoneId): BoughtEntry? {
    val paid = decision.actualPrice
    if (item.status != ItemStatus.PURCHASED || !decision.type.isPurchase || paid == null) return null
    val boughtOn = decision.decidedAt.atZone(zone).toLocalDate()
    return BoughtEntry(
        item = item,
        decision = decision,
        paid = paid,
        wishlistPrice = item.price.takeIf { it != paid },
        boughtOn = boughtOn,
        consideredForDays = daysBetween(item.addedAt.atZone(zone).toLocalDate(), boughtOn),
        early = decision.type == DecisionType.BUY_EARLY,
        need = decision.type == DecisionType.BUY_NEED
    )
}

private fun decidedAgainstEntryOf(item: Item, decision: Decision, zone: ZoneId): DecidedAgainstEntry? {
    if (item.status != ItemStatus.REJECTED || decision.type != DecisionType.REJECT) return null
    return DecidedAgainstEntry(
        item = item,
        decision = decision,
        price = item.price,
        decidedOn = decision.decidedAt.atZone(zone).toLocalDate(),
        note = decision.note
    )
}

/**
 * Whether every amount in [entries] is in [currency]. False only for a moment while a currency change is being
 * written (ADR-0033) and the history and the policy are read apart; a screen waits for the next read instead.
 */
fun allInCurrency(entries: List<HistoryEntry>, currency: String): Boolean = entries.all { (item, decision) ->
    item.price.currency == currency && (decision.actualPrice?.currency ?: currency) == currency
}

/** Time until the next midnight in [zone], when "this month" and "this year" may change. */
fun untilHistoryChanges(now: Instant, zone: ZoneId): Duration =
    Duration.between(now, now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant())

/**
 * "Want it again?" on a decided-against [item] at [now] in [zone] (issue #19): a **new** want with the item's facts
 * (name, price, link, photo, category, notes, a target date that is still ahead), added at [now] with the days the
 * [policy]'s tiers call for and parked on the wishlist like a newly captured want (ADR-0049). It is always a want, even
 * if the old item was a need, and it has no reflection answers: the user thinks it over afresh. The old item stays in
 * history unchanged. Its id is [ItemId.NEW]; store it with
 * [io.github.locngu.beforeyoubuy.core.domain.item.ItemRepository.add].
 *
 * Only rejected items can be wanted again, and the price must be in the policy's currency; anything else throws.
 */
fun wantedAgain(item: Item, policy: Policy, now: Instant, zone: ZoneId): Item {
    require(item.status == ItemStatus.REJECTED) { "Only decided-against items can be wanted again, was ${item.status}" }
    requireCurrency(policy.currency, item.price)
    return Item(
        id = ItemId.NEW,
        name = item.name,
        price = item.price,
        addedAt = now,
        coolingOffDays = coolingOffDaysFor(item.price, policy.coolingOffRules),
        kind = ItemKind.WANT,
        link = item.link,
        imageRef = item.imageRef,
        category = item.category,
        notes = item.notes,
        targetDate = item.targetDate?.takeIf { it > now.atZone(zone).toLocalDate() }
    )
}

private fun daysBetween(from: LocalDate, to: LocalDate): Int = maxOf(0L, ChronoUnit.DAYS.between(from, to)).toInt()

private fun requireCurrency(currency: String, vararg amounts: Money) {
    amounts.forEach {
        require(it.currency == currency) { "History amounts are in the app's currency $currency, was $it" }
    }
}
