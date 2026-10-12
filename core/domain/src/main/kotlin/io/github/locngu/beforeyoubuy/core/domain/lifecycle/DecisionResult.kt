package io.github.locngu.beforeyoubuy.core.domain.lifecycle

import io.github.locngu.beforeyoubuy.core.domain.item.Item

/** Outcome of [applyDecision]. A refusal says why, so the screen can explain it or offer the right path. */
sealed interface DecisionResult {
    /** The decision is allowed; [item] is the item's new state. Store it together with the decision. */
    data class Applied(val item: Item) : DecisionResult

    sealed interface Refused : DecisionResult

    /** The item was already purchased or rejected; it can't be decided again. */
    data object NotActive : Refused

    /** `BUY` on a want that isn't Ready. Buying it now is `BUY_EARLY` (ADR-0006). */
    data object NotReady : Refused

    /** `BUY` or `BUY_EARLY` on a `NEED`. A need is bought with `BUY_NEED` (ADR-0008). */
    data object IsNeed : Refused

    /** `BUY_NEED` on a want. */
    data object NotNeed : Refused

    /** `BUY_EARLY` without a (non-blank) reason (ADR-0006). */
    data object MissingEarlyReason : Refused

    /** `WAIT` without a re-check date after the decision time. */
    data object WaitNotInFuture : Refused
}
