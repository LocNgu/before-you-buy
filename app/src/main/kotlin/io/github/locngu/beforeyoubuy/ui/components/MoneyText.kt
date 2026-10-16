package io.github.locngu.beforeyoubuy.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.core.domain.money.format
import io.github.locngu.beforeyoubuy.ui.format.currentLocale

/**
 * An amount in whole units, rounded up, for the UI's locale (`1.235 €` / `€1,235`, ADR-0034), with
 * tabular digits so amounts line up in lists. Never colour amounts to signal "affordable" or "deal" (ADR-0014).
 */
@Composable
fun MoneyText(
    money: Money,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified
) {
    val locale = currentLocale()
    val text = remember(money, locale) { money.format(locale) }
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = style.merge(TextStyle(fontFeatureSettings = "tnum"))
    )
}
