package io.github.locngu.beforeyoubuy.feature.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import io.github.locngu.beforeyoubuy.R
import io.github.locngu.beforeyoubuy.core.domain.money.format
import io.github.locngu.beforeyoubuy.core.domain.pool.SavingsProgress
import io.github.locngu.beforeyoubuy.ui.format.currentLocale

/**
 * What is set aside for an item (brief §4.6): a bar and "650 € of 1,600 € set aside · 950 € to go", or without the
 * rest once the price is covered. Calm colours, never a celebration (ADR-0012). Item detail, the wishlist, Home and
 * the decision screen show the same.
 */
@Composable
fun SavingsProgressBar(
    progress: SavingsProgress,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
        Text(
            text = savingsText(progress),
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** "650 € of 1,600 € set aside · 950 € to go"; once covered, "1,600 € of 1,600 € set aside". */
@Composable
fun savingsText(progress: SavingsProgress): String {
    val locale = currentLocale()
    val allocated = progress.allocated.format(locale)
    val price = progress.price.format(locale)
    return if (progress.complete) {
        stringResource(R.string.savings_progress_complete, allocated, price)
    } else {
        stringResource(R.string.savings_progress, allocated, price, progress.shownToGo.format(locale))
    }
}
