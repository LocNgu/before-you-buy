package io.github.locngu.beforeyoubuy.core.domain.lifecycle

import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.item.Reflection
import io.github.locngu.beforeyoubuy.core.domain.money.Money
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
 * Brief §5 `funded`: the money set aside for the item covers its price. [allocated] must be in the item's
 * currency. Always true for a `NEED`, which never uses fun money (ADR-0044). Until savings exist (#23), callers pass
 * the price itself.
 */
fun isFunded(item: Item, allocated: Money): Boolean = item.kind == ItemKind.NEED || allocated >= item.price

/**
 * The stage of an active item, per the table in brief §5. The first unmet condition wins: cooled off, then
 * reflected, then funded. Purchased and rejected items have no stage, and [allocated] must be in the item's
 * currency; anything else is a programming error and throws.
 */
fun stageOf(item: Item, reflection: Reflection?, allocated: Money, now: Instant): Stage {
    require(item.status == ItemStatus.ACTIVE) { "Only active items have a stage; ${item.id} is ${item.status}" }
    requireSameCurrency(item, allocated)
    return when {
        !isCooledOff(item, now) -> Stage.COOLING_OFF
        !isReflected(item, reflection) -> Stage.NEEDS_REFLECTION
        !isFunded(item, allocated) -> Stage.SAVING
        else -> Stage.READY
    }
}

/**
 * Whether the item should be offered for a decision now (brief §5): it is active and Ready, and a `WAIT`
 * decision's re-check date, if any, has come. False for purchased and rejected items. Like [stageOf], throws if
 * [allocated] isn't in the item's currency.
 */
fun isDueForDecision(item: Item, reflection: Reflection?, allocated: Money, now: Instant): Boolean {
    requireSameCurrency(item, allocated)
    return item.status == ItemStatus.ACTIVE &&
        stageOf(item, reflection, allocated, now) == Stage.READY &&
        (item.recheckAt == null || now >= item.recheckAt)
}

/** Mixing currencies is a wiring bug; fail on every path, not only once the item reaches the funded check. */
internal fun requireSameCurrency(item: Item, allocated: Money) {
    require(allocated.currency == item.price.currency) {
        "Allocated ${allocated.currency} doesn't match the item's currency ${item.price.currency}"
    }
}
