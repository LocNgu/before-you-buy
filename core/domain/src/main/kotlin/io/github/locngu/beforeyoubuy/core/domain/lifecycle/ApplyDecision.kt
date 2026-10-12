package io.github.locngu.beforeyoubuy.core.domain.lifecycle

import io.github.locngu.beforeyoubuy.core.domain.item.Decision
import io.github.locngu.beforeyoubuy.core.domain.item.DecisionType
import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.item.Reflection
import io.github.locngu.beforeyoubuy.core.domain.money.Money

/**
 * Checks [decision] against the item's kind and stage at [Decision.decidedAt] and, if it's allowed, returns the
 * item's new state (brief §5 "Decisions"):
 *
 * - `BUY`: a want that is Ready.
 * - `BUY_EARLY`: a want that isn't Ready, with a non-blank reason (ADR-0006).
 * - `BUY_NEED`: a `NEED`, in any stage (ADR-0008).
 * - `WAIT`: any active item, with a re-check date in the future. The item stays active.
 * - `REJECT`: any active item.
 *
 * Purchased and rejected items refuse every decision. Never throws for a refused decision. [allocated] and the
 * decision's actual price must be in the item's currency; anything else is a programming error and throws.
 */
fun applyDecision(item: Item, reflection: Reflection?, allocated: Money, decision: Decision): DecisionResult {
    require(decision.actualPrice == null || decision.actualPrice.currency == item.price.currency) {
        "Actual price must be in the item's currency ${item.price.currency}"
    }
    if (item.status != ItemStatus.ACTIVE) return DecisionResult.NotActive
    val now = decision.decidedAt
    val isNeed = item.kind == ItemKind.NEED
    val refusal = when (decision.type) {
        DecisionType.BUY -> when {
            isNeed -> DecisionResult.IsNeed
            stageOf(item, reflection, allocated, now) != Stage.READY -> DecisionResult.NotReady
            else -> null
        }

        DecisionType.BUY_EARLY -> when {
            isNeed -> DecisionResult.IsNeed
            stageOf(item, reflection, allocated, now) == Stage.READY -> DecisionResult.AlreadyReady
            decision.earlyReason.isNullOrBlank() -> DecisionResult.MissingEarlyReason
            else -> null
        }

        DecisionType.BUY_NEED -> if (isNeed) null else DecisionResult.NotNeed

        DecisionType.WAIT -> {
            val waitUntil = decision.waitUntil
            if (waitUntil == null || waitUntil <= now) DecisionResult.WaitNotInFuture else null
        }

        DecisionType.REJECT -> null
    }
    return refusal ?: DecisionResult.Applied(transition(item, decision))
}

private fun transition(item: Item, decision: Decision): Item = when (decision.type) {
    DecisionType.BUY, DecisionType.BUY_EARLY, DecisionType.BUY_NEED -> item.ended(ItemStatus.PURCHASED)
    DecisionType.REJECT -> item.ended(ItemStatus.REJECTED)
    DecisionType.WAIT -> item.copy(recheckAt = decision.waitUntil)
}

/** A purchased or rejected item has nothing to re-check and no longer holds a priority slot. */
private fun Item.ended(status: ItemStatus) = copy(status = status, recheckAt = null, prioritySlot = null)
