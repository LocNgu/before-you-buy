package io.github.locngu.beforeyoubuy.feature.decision

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.locngu.beforeyoubuy.R
import io.github.locngu.beforeyoubuy.core.domain.decision.OpportunityCostLine
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.core.domain.money.format
import io.github.locngu.beforeyoubuy.ui.components.SectionHeader
import io.github.locngu.beforeyoubuy.ui.format.currentLocale

/**
 * "What else this money could do" (brief §7): the [lines] from `opportunityCostOf`, in their order. Nothing without
 * lines. The decision screen shows them all, item detail the first two (issue #25).
 */
@Composable
fun OpportunityCostSection(lines: List<OpportunityCostLine>, modifier: Modifier = Modifier) {
    if (lines.isEmpty()) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(stringResource(R.string.opportunity_cost_title))
        lines.forEach { line ->
            Text(
                opportunityCostText(line),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** One line as a sentence, with the counts as plurals. */
@Composable
fun opportunityCostText(line: OpportunityCostLine): String = when (line) {
    is OpportunityCostLine.DelaysPriority -> pluralStringResource(
        R.plurals.opportunity_cost_delays,
        line.months,
        line.months,
        line.priority.name,
        line.rank
    )

    is OpportunityCostLine.ShareOfGoal ->
        if (line.percent == 0L) {
            stringResource(R.string.opportunity_cost_share_less, line.priority.name, line.rank)
        } else {
            stringResource(R.string.opportunity_cost_share, line.percent, line.priority.name, line.rank)
        }

    is OpportunityCostLine.MonthsOfAllowance -> when {
        line.months == 0L -> stringResource(R.string.opportunity_cost_months_less)
        line.exact -> pluralStringResource(R.plurals.opportunity_cost_months, line.months.quantity, line.months)
        else -> pluralStringResource(R.plurals.opportunity_cost_months_about, line.months.quantity, line.months)
    }

    is OpportunityCostLine.SmallerItemsCombined -> pluralStringResource(
        if (line.all) R.plurals.opportunity_cost_all_items else R.plurals.opportunity_cost_smaller_items,
        line.items.size,
        line.items.size,
        line.total.formatted()
    )
}

@Composable
private fun Money.formatted(): String = format(currentLocale())

/** Plural quantities are `Int`; any count past that reads as "many". */
private val Long.quantity: Int get() = coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
