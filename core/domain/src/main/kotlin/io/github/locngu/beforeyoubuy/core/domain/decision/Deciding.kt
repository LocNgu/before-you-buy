package io.github.locngu.beforeyoubuy.core.domain.decision

import io.github.locngu.beforeyoubuy.core.domain.item.Decision
import io.github.locngu.beforeyoubuy.core.domain.item.DecisionType
import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.item.Reflection
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.Funding
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.Stage
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.isReflected
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.stageOf
import io.github.locngu.beforeyoubuy.core.domain.money.MoneyParseResult
import io.github.locngu.beforeyoubuy.core.domain.money.formatForInput
import io.github.locngu.beforeyoubuy.core.domain.money.parseMoney
import io.github.locngu.beforeyoubuy.core.domain.pool.Savings
import io.github.locngu.beforeyoubuy.core.domain.pool.SavingsProgress
import io.github.locngu.beforeyoubuy.core.domain.pool.progressOf
import io.github.locngu.beforeyoubuy.core.domain.wishlist.daysLeft
import io.github.locngu.beforeyoubuy.core.domain.wishlist.recheckOnOf
import io.github.locngu.beforeyoubuy.core.domain.wishlist.wantedForDays
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * How the decision screen offers buying an item (brief §5 "Decisions", §6.5). Reject is always offered.
 *
 * @property type the decision a purchase on this path records.
 */
enum class BuyPath(val type: DecisionType) {
    /** A Ready want: Buy, Wait and Reject. */
    BUY(DecisionType.BUY),

    /** A need, in any stage (ADR-0008): Buy, Wait and Reject. */
    BUY_NEED(DecisionType.BUY_NEED),

    /**
     * A want that isn't Ready: no Buy and no Wait (it's waiting already), only Reject and a quieter "Decide early…"
     * behind friction: a typed reason, the days left and the reasons against again, a confirm (ADR-0006).
     */
    EARLY(DecisionType.BUY_EARLY)
    ;

    /** Whether the screen offers Wait: only where it offers a plain Buy. */
    val canWait: Boolean get() = this != EARLY
}

/**
 * What the decision screen shows about an active [item] (brief §6.5, issue #18), top to bottom: the item, how long
 * it has been wanted and its cooling-off, the user's reasons, what else the money could do, then the actions.
 *
 * @property daysLeft as in [io.github.locngu.beforeyoubuy.core.domain.wishlist.WishlistEntry.daysLeft].
 * @property wantedForDays calendar days since the item was added (ADR-0018).
 * @property reflected the required reflection answers are there; if not, the screen offers the questions.
 * @property others what else this money could do (basic opportunity cost, brief §7): the user's other active items,
 *   at most [OTHER_ITEMS_SHOWN], priorities first, then the longest wanted.
 * @property moreOthers how many other active items didn't fit into [others].
 * @property recheckOn the re-check date of an earlier "Wait", while it's still ahead.
 * @property savings what is set aside for a want while fun money is set up (brief §6.5 "amount saved"); null for a
 *   need and without fun money.
 * @property funding Ready's money condition, to check the decision against with `applyDecision`.
 */
data class DecisionOverview(
    val item: Item,
    val reflection: Reflection?,
    val stage: Stage,
    val buyPath: BuyPath,
    val daysLeft: Int,
    val wantedForDays: Int,
    val reflected: Boolean,
    val others: List<Item>,
    val moreOthers: Int,
    val recheckOn: LocalDate?,
    val savings: SavingsProgress? = null,
    val funding: Funding = Funding(item.price, required = false)
)

/** How many other items "What else this money could do" lists before "and N more". */
const val OTHER_ITEMS_SHOWN = 3

/**
 * The decision screen for an active [item] at [now] in [zone], with its reflection answers, the user's [active]
 * items (the item itself may be among them) and the [savings] (as in the wishlist). Purchased and rejected items have
 * nothing to decide and throw.
 */
fun decisionOverviewOf(
    item: Item,
    reflection: Reflection?,
    active: List<Item>,
    savings: Savings,
    now: Instant,
    zone: ZoneId
): DecisionOverview {
    val funding = savings.fundingOf(item)
    val stage = stageOf(item, reflection, funding, now)
    val others = active
        .filter { it.status == ItemStatus.ACTIVE && it.id != item.id }
        .sortedWith(compareBy(nullsLast()) { it: Item -> it.prioritySlot }.thenBy { it.addedAt }.thenBy { it.id.value })
    return DecisionOverview(
        item = item,
        reflection = reflection,
        stage = stage,
        buyPath = buyPathOf(item, stage),
        daysLeft = daysLeft(item, now, zone),
        wantedForDays = wantedForDays(item, now, zone),
        reflected = isReflected(item, reflection),
        others = others.take(OTHER_ITEMS_SHOWN),
        moreOthers = (others.size - OTHER_ITEMS_SHOWN).coerceAtLeast(0),
        recheckOn = recheckOnOf(item, stage, now, zone),
        savings = savings.progressOf(item).takeIf { savings.setUp },
        funding = funding
    )
}

/** A need is bought as a need; a want only plainly once it's Ready, otherwise early (ADR-0006, ADR-0008). */
fun buyPathOf(item: Item, stage: Stage): BuyPath = when {
    item.kind == ItemKind.NEED -> BuyPath.BUY_NEED
    stage == Stage.READY -> BuyPath.BUY
    else -> BuyPath.EARLY
}

/** "Wait" offers to look again in this many days, or on a picked date (issue #18). */
val WAIT_DAYS: List<Int> = listOf(7, 14, 30)

/**
 * The decision screen's inputs as typed: the price actually paid and the purchase date (Buy, Decide early), the
 * reason for buying early, the date to look again (Wait) and the optional "Why not?" (Reject).
 */
data class DecisionForm(
    val price: String,
    val boughtOn: LocalDate,
    val earlyReason: String = "",
    val waitUntil: LocalDate,
    val note: String = ""
)

/**
 * A fresh form for [item] on [today]: the item's exact price as typed input (ADR-0034, so an unchanged price is
 * stored as it is), bought today, and the first [WAIT_DAYS] option.
 */
fun decisionFormOf(item: Item, today: LocalDate, locale: Locale): DecisionForm = DecisionForm(
    price = item.price.formatForInput(locale),
    boughtOn = today,
    waitUntil = today.plusDays(WAIT_DAYS.first().toLong())
)

/**
 * The dates a purchase on [path] can be dated to, up to [today] in [zone]: a plain Buy from the day the item's
 * cooling-off ended, a need or an early purchase from the day the item was added.
 */
fun purchaseDates(item: Item, path: BuyPath, today: LocalDate, zone: ZoneId): ClosedRange<LocalDate> {
    val added = item.addedAt.atZone(zone).toLocalDate()
    val earliest = if (path == BuyPath.BUY) maxOf(added, item.coolingEndsAt.atZone(zone).toLocalDate()) else added
    return minOf(earliest, today)..today
}

/** The dates "Wait" can be set to: from tomorrow on (the decision needs a re-check date after it, ADR-0018). */
fun earliestWaitDate(today: LocalDate): LocalDate = today.plusDays(1)

/** Outcome of [checkPurchase]: how the price reads, and whether an early purchase still lacks its reason. */
data class PurchaseCheck(val price: MoneyParseResult, val reasonMissing: Boolean, val dateOutOfRange: Boolean) {
    val isValid: Boolean get() = price is MoneyParseResult.Success && !reasonMissing && !dateOutOfRange
}

/**
 * Checks a purchase on [path]: a price that [parseMoney] reads in the item's currency, a non-blank reason for an
 * early purchase (ADR-0006), and a date in [purchaseDates].
 */
fun checkPurchase(
    form: DecisionForm,
    item: Item,
    path: BuyPath,
    locale: Locale,
    today: LocalDate,
    zone: ZoneId
): PurchaseCheck = PurchaseCheck(
    price = parseMoney(form.price, item.price.currency, locale),
    reasonMissing = path == BuyPath.EARLY && form.earlyReason.isBlank(),
    dateOutOfRange = form.boughtOn !in purchaseDates(item, path, today, zone)
)

/**
 * The purchase decision for [form] on [path] at [now] in [zone]. A purchase dated today is decided now; one dated
 * back is decided at the last moment of that day, so a plain Buy on the day cooling off ended still counts as Ready.
 * The early reason is trimmed. Only call it for a form [checkPurchase] accepts; anything else throws.
 */
fun purchaseDecision(
    form: DecisionForm,
    item: Item,
    path: BuyPath,
    locale: Locale,
    now: Instant,
    zone: ZoneId
): Decision {
    val today = now.atZone(zone).toLocalDate()
    val check = checkPurchase(form, item, path, locale, today, zone)
    val price = (check.price as? MoneyParseResult.Success)?.money
    require(check.isValid && price != null) { "Only a valid purchase can be decided: $check" }
    return Decision(
        type = path.type,
        decidedAt = decidedAtOn(form.boughtOn, now, zone),
        actualPrice = price,
        earlyReason = form.earlyReason.trim().takeIf { path == BuyPath.EARLY }
    )
}

/** Today: [now]. An earlier day: its last millisecond (instants are stored to the millisecond). */
private fun decidedAtOn(date: LocalDate, now: Instant, zone: ZoneId): Instant =
    if (date >= now.atZone(zone).toLocalDate()) {
        now
    } else {
        date.plusDays(1).atStartOfDay(zone).toInstant().minusMillis(1)
    }

/**
 * "Wait" until [DecisionForm.waitUntil]: the item is back to decide at the start of that day in
 * [zone], calendar days (ADR-0018). Throws for a date before [earliestWaitDate].
 */
fun waitDecision(form: DecisionForm, now: Instant, zone: ZoneId): Decision {
    require(form.waitUntil >= earliestWaitDate(now.atZone(zone).toLocalDate())) {
        "Wait needs a date after today, was ${form.waitUntil}"
    }
    return Decision(DecisionType.WAIT, now, waitUntil = form.waitUntil.atStartOfDay(zone).toInstant())
}

/** "Don't buy" at [now], with the optional "Why not?" trimmed; a blank note is none. */
fun rejectDecision(form: DecisionForm, now: Instant): Decision =
    Decision(DecisionType.REJECT, now, note = form.note.trim().ifEmpty { null })
