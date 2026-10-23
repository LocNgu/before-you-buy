package io.github.locngu.beforeyoubuy.feature.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.locngu.beforeyoubuy.R
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.core.domain.pool.PoolBalance
import io.github.locngu.beforeyoubuy.ui.components.MoneyText

/**
 * The three fun money amounts, in this order and with these labels on Home and the fun money screen (ADR-0045):
 * plain amounts for wants, never "available to spend" (ADR-0014).
 */
@Composable
internal fun FunMoneyAmounts(balance: PoolBalance, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AmountRow(stringResource(R.string.home_money_pool), balance.pool)
        AmountRow(stringResource(R.string.home_money_committed), balance.committed)
        AmountRow(stringResource(R.string.home_money_unallocated), balance.unallocated)
    }
}

@Composable
private fun AmountRow(label: String, amount: Money) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            modifier = Modifier.weight(1f).padding(end = 16.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        MoneyText(amount, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}
