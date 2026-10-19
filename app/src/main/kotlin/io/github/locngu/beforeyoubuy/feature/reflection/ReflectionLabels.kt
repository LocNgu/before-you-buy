package io.github.locngu.beforeyoubuy.feature.reflection

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.locngu.beforeyoubuy.R
import io.github.locngu.beforeyoubuy.core.domain.item.UpgradeOrNew
import io.github.locngu.beforeyoubuy.core.domain.item.UsageFrequency

// The answers to the choice questions, worded the same in the flow and wherever the answers are shown again.

@Composable
internal fun usageLabel(usage: UsageFrequency): String = stringResource(
    when (usage) {
        UsageFrequency.DAILY -> R.string.usage_daily
        UsageFrequency.WEEKLY -> R.string.usage_weekly
        UsageFrequency.MONTHLY -> R.string.usage_monthly
        UsageFrequency.RARELY -> R.string.usage_rarely
        UsageFrequency.ONCE -> R.string.usage_once
    }
)

@Composable
internal fun upgradeOrNewLabel(value: UpgradeOrNew): String = stringResource(
    when (value) {
        UpgradeOrNew.UPGRADE -> R.string.upgrade_or_new_upgrade
        UpgradeOrNew.NEW -> R.string.upgrade_or_new_new
    }
)
