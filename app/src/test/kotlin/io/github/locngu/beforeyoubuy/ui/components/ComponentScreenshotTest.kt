package io.github.locngu.beforeyoubuy.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RoborazziRule
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.testing.captureContent
import io.github.locngu.beforeyoubuy.ui.theme.BeforeYouBuyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every shared component in light/dark × en/de: one subclass per variant below, each with fixed
 * qualifiers. Record: `./gradlew :app:recordRoborazziDebug`. Images are named
 * `…ComponentScreenshot<Language><Theme>Test.<component>.png`.
 *
 * Qualifiers are set with `@Config`, never with `RuntimeEnvironment.setQualifiers` between tests: the
 * runtime switch reaches the activity asynchronously and intermittently produced blank captures.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ComponentScreenshotTest(private val german: Boolean) {
    @get:Rule(order = 0)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // Names images after the concrete subclass, not this base class.
    @get:Rule(order = 1)
    val roborazziRule = RoborazziRule()

    // Sample label for components that take their text from the caller. Real copy comes with each feature.
    private fun t(en: String, de: String) = if (german) de else en

    @Test
    fun moneyText() = capture {
        MoneyText(Money(123_456, "EUR"), style = MaterialTheme.typography.headlineSmall)
        MoneyText(Money(120_000, "EUR"))
        MoneyText(Money(1_999, "EUR"), style = MaterialTheme.typography.bodySmall)
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
        composeRule.captureContent()
    }
}

@Config(qualifiers = "en-w360dp-h640dp-notnight-xhdpi")
class ComponentScreenshotEnLightTest : ComponentScreenshotTest(german = false)

@Config(qualifiers = "en-w360dp-h640dp-night-xhdpi")
class ComponentScreenshotEnDarkTest : ComponentScreenshotTest(german = false)

@Config(qualifiers = "de-w360dp-h640dp-notnight-xhdpi")
class ComponentScreenshotDeLightTest : ComponentScreenshotTest(german = true)

@Config(qualifiers = "de-w360dp-h640dp-night-xhdpi")
class ComponentScreenshotDeDarkTest : ComponentScreenshotTest(german = true)
