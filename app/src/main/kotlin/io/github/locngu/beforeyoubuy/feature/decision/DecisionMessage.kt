package io.github.locngu.beforeyoubuy.feature.decision

import android.content.res.Resources
import io.github.locngu.beforeyoubuy.R
import io.github.locngu.beforeyoubuy.core.domain.decision.BoughtConfirmation
import io.github.locngu.beforeyoubuy.core.domain.money.format
import java.util.Locale

/**
 * The plain-text confirmation after a decision (ADR-0040, amended by ADR-0055). A plain Buy says how long the user
 * waited; an early buy and a need keep the neutral line, so nothing marks the difference; letting go names the priority
 * the money stays for, as "Don't buy" did, and otherwise says where the item went. No animation, badge or sound.
 * [Waiting][DecisionOutcome.Waiting] has none: the screen just closes.
 */
fun decisionMessage(outcome: DecisionOutcome, resources: Resources, locale: Locale): String = when (outcome) {
    is DecisionOutcome.Bought -> when (val confirmation = outcome.confirmation) {
        BoughtConfirmation.Neutral -> resources.getString(R.string.decision_bought)

        BoughtConfirmation.Enjoy -> resources.getString(R.string.decision_bought_enjoy)

        is BoughtConfirmation.Waited ->
            resources.getQuantityString(R.plurals.decision_bought_waited, confirmation.days, confirmation.days)
    }

    is DecisionOutcome.Rejected -> outcome.keepFor?.let {
        resources.getString(R.string.decision_rejected_for, outcome.price.format(locale), it.priority.name, it.rank)
    } ?: resources.getString(R.string.decision_rejected)

    is DecisionOutcome.Waiting -> ""
}
