package dev.placeholder.beforeyoubuy.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import dev.placeholder.beforeyoubuy.R

/**
 * What's left of a cooling-off period: "23 days left", or "Cooling-off over" once [daysLeft] is 0
 * or less. Informational, so it uses a quiet colour and never the error or a warning colour (ADR-0026).
 *
 * [daysLeft] counts calendar days (ADR-0018); the caller recomputes it when the date changes.
 */
@Composable
fun CountdownText(daysLeft: Int, modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current) {
    val text =
        if (daysLeft > 0) {
            pluralStringResource(R.plurals.countdown_days_left, daysLeft, daysLeft)
        } else {
            stringResource(R.string.countdown_over)
        }
    Text(text = text, modifier = modifier, color = MaterialTheme.colorScheme.onSurfaceVariant, style = style)
}
