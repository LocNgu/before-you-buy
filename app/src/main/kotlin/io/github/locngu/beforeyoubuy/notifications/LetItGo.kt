package io.github.locngu.beforeyoubuy.notifications

import io.github.locngu.beforeyoubuy.core.domain.item.Decision
import io.github.locngu.beforeyoubuy.core.domain.item.DecisionSource
import io.github.locngu.beforeyoubuy.core.domain.item.DecisionType
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemRepository
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.DecisionResult
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.applyDecision
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * "Let it go" on a notification (brief §8, ADR-0014): records a `REJECT` from [DecisionSource.NOTIFICATION] without
 * opening the app. Goes through `applyDecision` and the compare-and-set `recordDecision` like every decision.
 */
class LetItGo @Inject constructor(private val items: ItemRepository, private val clock: Clock) {
    /**
     * Returns true if the item is now decided against. False if it was gone or had already ended (e.g. bought in the
     * app meanwhile): nothing is written then. An edit that lands in between is read again and the reject still
     * applies, since the user asked for it.
     */
    suspend fun run(id: ItemId): Boolean {
        repeat(ATTEMPTS) {
            val item = items.observeById(id).first() ?: return false
            if (item.status != ItemStatus.ACTIVE) return false
            val decision = Decision(DecisionType.REJECT, clock.instant(), DecisionSource.NOTIFICATION)
            // Every item counts as funded until savings exist (M2).
            val result = applyDecision(item, items.observeReflection(id).first(), item.price, decision)
            if (result !is DecisionResult.Applied) return false
            if (items.recordDecision(item, decision, result.item)) return true
        }
        return false
    }

    private companion object {
        const val ATTEMPTS = 3
    }
}
