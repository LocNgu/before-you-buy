package io.github.locngu.beforeyoubuy.feature.wishlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.locngu.beforeyoubuy.R
import io.github.locngu.beforeyoubuy.core.domain.coolingoff.WaitProgress

/**
 * What item detail and the decision screen say about a want without a priority slot (ADR-0049): "Not one of your
 * priorities", how much of its wait is done ([waitProgress]), and "Make it a priority" ([onMakePriority]; a free slot
 * at once, otherwise the head-to-head). Without [onMakePriority] the button is left out.
 */
@Composable
internal fun NotAPriority(waitProgress: WaitProgress?, onMakePriority: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.stage_not_priority),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        waitProgress?.let {
            Text(
                text = waitProgressText(it),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (onMakePriority != null) {
            OutlinedButton(onClick = onMakePriority) { Text(stringResource(R.string.priority_make)) }
        }
    }
}
