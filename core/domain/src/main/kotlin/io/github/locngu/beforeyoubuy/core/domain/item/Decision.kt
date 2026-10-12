package io.github.locngu.beforeyoubuy.core.domain.item

import io.github.locngu.beforeyoubuy.core.domain.money.Money
import java.time.Instant

/** What the user decided about an item (brief §5 "Decisions"). */
enum class DecisionType {
    /** A want that is Ready. */
    BUY,

    /** A want that isn't Ready yet: allowed with friction and always logged (ADR-0006). */
    BUY_EARLY,

    /** A `NEED` (ADR-0008); never paid from fun money (ADR-0031). */
    BUY_NEED,

    /** Stay active and look again at [Decision.waitUntil]. */
    WAIT,

    /** "Decided against" (ADR-0012). */
    REJECT
    ;

    /** Ends the item as `PURCHASED` and carries an actual price. */
    val isPurchase: Boolean get() = this == BUY || this == BUY_EARLY || this == BUY_NEED
}

/** Where the decision was made. "Let it go" in a notification is a `REJECT` from [NOTIFICATION] (ADR-0014). */
enum class DecisionSource { APP, NOTIFICATION }

/**
 * One decision about an item, kept as history. Validate and apply it with
 * [io.github.locngu.beforeyoubuy.core.domain.lifecycle.applyDecision].
 *
 * A field that doesn't belong to [type] is a programming error and throws. Whether the values are acceptable
 * (a non-blank early reason, a future wait date) is checked by `applyDecision`, which reports it as a result.
 *
 * @property actualPrice what was really paid; required for purchases, absent otherwise.
 * @property earlyReason why the user buys before the item is Ready; [DecisionType.BUY_EARLY] only.
 * @property waitUntil when to look again; [DecisionType.WAIT] only.
 */
data class Decision(
    val type: DecisionType,
    val decidedAt: Instant,
    val source: DecisionSource = DecisionSource.APP,
    val actualPrice: Money? = null,
    val earlyReason: String? = null,
    val waitUntil: Instant? = null
) {
    init {
        require((actualPrice != null) == type.isPurchase) { "Purchases, and only purchases, have an actual price" }
        require(actualPrice == null || actualPrice.minorUnits >= 0) { "Actual price can't be negative" }
        require(earlyReason == null || type == DecisionType.BUY_EARLY) { "Only BUY_EARLY has an early reason" }
        require(waitUntil == null || type == DecisionType.WAIT) { "Only WAIT has a wait date" }
    }
}
