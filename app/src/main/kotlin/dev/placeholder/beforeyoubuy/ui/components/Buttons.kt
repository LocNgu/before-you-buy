package dev.placeholder.beforeyoubuy.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// Primary and secondary buttons look the same on purpose (ADR-0012, ADR-0026): same shape, size, type
// and fill. Only the label tells them apart, so not buying is never the "lesser" button. The two names
// say which action a screen is about (e.g. Buy) and which is its equal alternative (e.g. Let it go).

/** The action a screen is about, e.g. Buy. Looks exactly like [SecondaryButton]. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    EqualWeightButton(text = text, onClick = onClick, modifier = modifier, enabled = enabled)
}

/** The equal alternative, e.g. "Keep 1,200 € for your Camera (#2)". Looks exactly like [PrimaryButton]. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    EqualWeightButton(text = text, onClick = onClick, modifier = modifier, enabled = enabled)
}

/**
 * Lays out two buttons side by side with equal width and height, however long either label is.
 * Each slot gets the modifier its button must use.
 */
@Composable
fun ButtonPair(
    start: @Composable (Modifier) -> Unit,
    end: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        start(Modifier.weight(1f).fillMaxHeight())
        end(Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun EqualWeightButton(text: String, onClick: () -> Unit, modifier: Modifier, enabled: Boolean) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}
