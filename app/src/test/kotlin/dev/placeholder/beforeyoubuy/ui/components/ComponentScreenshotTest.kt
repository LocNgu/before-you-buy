package dev.placeholder.beforeyoubuy.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.captureRoboImage
import dev.placeholder.beforeyoubuy.core.domain.money.Money
import dev.placeholder.beforeyoubuy.ui.theme.BeforeYouBuyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every shared component in light/dark × en/de. Record: `./gradlew :app:recordRoborazziDebug`.
 * Images are named `…ComponentScreenshotTest.<component>[<language>-<theme>].png`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class ComponentScreenshotTest(private val variant: Variant) {
    data class Variant(val language: String, val dark: Boolean) {
        override fun toString() = "$language-${if (dark) "dark" else "light"}"
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants() = listOf("en", "de").flatMap { language -> listOf(false, true).map { Variant(language, it) } }
    }

    // Qualifiers must be set before the compose rule launches its activity, hence the rule order.
    @get:Rule(order = 0)
    val variantRule =
        object : ExternalResource() {
            override fun before() {
                RuntimeEnvironment.setQualifiers("+${variant.language}-${if (variant.dark) "night" else "notnight"}")
            }
        }

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    // Gives captureRoboImage() the parameterized test name.
    @get:Rule(order = 2)
    val roborazziRule = RoborazziRule()

    // Sample label for components that take their text from the caller. Real copy comes with each feature.
    private fun t(en: String, de: String) = if (variant.language == "de") de else en

    @Test
    fun moneyText() = capture {
        MoneyText(Money(123_456, "EUR"), style = MaterialTheme.typography.headlineSmall)
        MoneyText(Money(120_000, "EUR"))
        MoneyText(Money(5, "EUR"), style = MaterialTheme.typography.bodySmall)
    }

    @Test
    fun countdownText() = capture {
        CountdownText(daysLeft = 23)
        CountdownText(daysLeft = 1)
        CountdownText(daysLeft = 0)
    }

    @Test
    fun sectionHeader() = capture {
        SectionHeader(t("Priorities", "Prioritäten"))
    }

    @Test
    fun emptyState() = capture {
        EmptyState(
            title = t("Nothing decided yet", "Noch nichts entschieden"),
            body = t("Things you’ve decided on appear here.", "Hier erscheinen Dinge, über die du entschieden hast."),
            action = { SecondaryButton(t("Add something", "Etwas hinzufügen"), onClick = {}) }
        )
    }

    @Test
    fun buttonPair() = capture {
        ButtonPair(
            start = { SecondaryButton(t("Let it go", "Loslassen"), onClick = {}, modifier = it) },
            end = { PrimaryButton(t("Buy", "Kaufen"), onClick = {}, modifier = it) }
        )
        ButtonPair(
            start = {
                SecondaryButton(
                    t("Keep 1,200 € for your Camera (#2)", "1.200 € für deine Kamera (#2) behalten"),
                    onClick = {},
                    modifier = it
                )
            },
            end = { PrimaryButton(t("Buy", "Kaufen"), onClick = {}, modifier = it) }
        )
        ButtonPair(
            start = { SecondaryButton(t("Wait", "Warten"), onClick = {}, modifier = it, enabled = false) },
            end = { PrimaryButton(t("Buy", "Kaufen"), onClick = {}, modifier = it, enabled = false) }
        )
    }

    // Captures through the compose rule, which waits until the content is drawn.
    private fun capture(content: @Composable () -> Unit) {
        composeRule.setContent {
            BeforeYouBuyTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) { content() }
                }
            }
        }
        composeRule.onRoot().captureRoboImage()
    }
}
