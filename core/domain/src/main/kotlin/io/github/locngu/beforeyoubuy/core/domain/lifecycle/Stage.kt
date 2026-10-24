package io.github.locngu.beforeyoubuy.core.domain.lifecycle

import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.item.Reflection
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.core.domain.money.shownWholeUnits
import io.github.locngu.beforeyoubuy.core.domain.reflection.ReflectionStep
import io.github.locngu.beforeyoubuy.core.domain.reflection.isAnswered
import io.github.locngu.beforeyoubuy.core.domain.reflection.isRequired
import java.time.Instant

/** The displayed stage of an active item: derived, never stored (brief §5, ADR-0007). */
enum class Stage { COOLING_OFF, NEEDS_REFLECTION, SAVING, READY }

/** Brief §4.2 and §5 `cooledOff`: the cooling-off period has ended. Always true for a `NEED` (ADR-0008). */
fun isCooledOff(item: Item, now: Instant): Boolean = item.kind == ItemKind.NEED || now >= item.coolingEndsAt

/**
 * Brief §4.3 and §5 `reflected`: the required answers are there (ADR-0009). That is every [ReflectionStep] that
 * [isRequired] for the item [answered][isAnswered]: a non-blank "why", a usage frequency and at least one non-blank
 * reason against. Always true for a `NEED`, which has no required questions (ADR-0008).
 */
fun isReflected(item: Item, reflection: Reflection?): Boolean {
    val answers = reflection ?: Reflection()
    return ReflectionStep.entries.all { !isRequired(it, item.kind) || isAnswered(it, answers) }
}

/**
 * What Ready's money condition (brief §5 `funded`) looks at for one item: [allocated], the money set aside for it, and
 * whether wants need it at all. [required] is false while fun money isn't set up or its "Wants need their price set
 * aside" switch is off: then the waiting period and the answers are enough (ADR-0046). Build it with
 * [io.github.locngu.beforeyoubuy.core.domain.pool.Savings.fundingOf].
 */
data class Funding(val allocated: Money, val required: Boolean)

/**
 * Brief §5 `funded`: the money set aside for the item covers its price ([coversPrice]), or wants don't need it
 * ([Funding.required] false, ADR-0046). [Funding.allocated] must be in the item's currency. Always true for a `NEED`,
 * which never uses fun money (ADR-0044).
 */
fun isFunded(item: Item, funding: Funding): Boolean =
    item.kind == ItemKind.NEED || !funding.required || coversPrice(funding.allocated, item.price)

/**
 * Whether [allocated] covers [price] as the screen shows them, in whole units (ADR-0034): 1.599,60 € set aside for
 * 1.600 € reads "1.600 € of 1.600 €" and counts as covered, so the screen never shows the full price set aside on
 * an item that isn't funded (ADR-0046). Both must be in one currency.
 */
fun coversPrice(allocated: Money, price: Money): Boolean {
    require(allocated.currency == price.currency) { "Can't compare ${allocated.currency} with ${price.currency}" }
    return allocated.shownWholeUnits() >= price.shownWholeUnits()
}

/**
 * The stage of an active item, per the table in brief §5. The first unmet condition wins: cooled off, then
 * reflected, then funded. Purchased and rejected items have no stage, and [Funding.allocated] must be in the item's
 * currency; anything else is a programming error and throws.
 */
fun stageOf(item: Item, reflection: Reflection?, funding: Funding, now: Instant): Stage {
    require(item.status == ItemStatus.ACTIVE) { "Only active items have a stage; ${item.id} is ${item.status}" }
    requireSameCurrency(item, funding.allocated)
    return when {
        !isCooledOff(item, now) -> Stage.COOLING_OFF
        !isReflected(item, reflection) -> Stage.NEEDS_REFLECTION
        !isFunded(item, funding) -> Stage.SAVING
        else -> Stage.READY
    }
}

/**
 * Whether the item should be offered for a decision now (brief §5): it is active and Ready, and a `WAIT`
 * decision's re-check date, if any, has come. False for purchased and rejected items. Like [stageOf], throws if
 * [Funding.allocated] isn't in the item's currency.
 */
fun isDueForDecision(item: Item, reflection: Reflection?, funding: Funding, now: Instant): Boolean {
    requireSameCurrency(item, funding.allocated)
    return item.status == ItemStatus.ACTIVE &&
        stageOf(item, reflection, funding, now) == Stage.READY &&
        (item.recheckAt == null || now >= item.recheckAt)
}

/** Mixing currencies is a wiring bug; fail on every path, not only once the item reaches the funded check. */
internal fun requireSameCurrency(item: Item, allocated: Money) {
    require(allocated.currency == item.price.currency) {
        "Allocated ${allocated.currency} doesn't match the item's currency ${item.price.currency}"
    }
}
