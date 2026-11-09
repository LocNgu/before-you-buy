package io.github.locngu.beforeyoubuy.feature.decision

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RoborazziRule
import io.github.locngu.beforeyoubuy.core.domain.decision.BoughtConfirmation
import io.github.locngu.beforeyoubuy.core.domain.decision.OpportunityCostLine
import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.testing.captureContent
import io.github.locngu.beforeyoubuy.ui.theme.BeforeYouBuyTheme
import java.time.Instant
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The confirmations after a decision as the snackbar shows them (ADR-0055): bought as planned, the same day, the
 * neutral line of an early buy or a need, and letting go with and without a priority named; in English and German.
 * Record: `./gradlew :app:recordRoborazziDebug`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class DecisionMessageScreenshotTest(private val german: Boolean) {
    @get:Rule(order = 0)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // Names images after the concrete subclass, not this base class.
    @get:Rule(order = 1)
    val roborazziRule = RoborazziRule()

    private fun t(en: String, de: String) = if (german) de else en

    private val locale = if (german) Locale.GERMAN else Locale.ENGLISH
    private val camera = Item(
        id = ItemId(2),
        name = t("Camera", "Kamera"),
        price = Money(120_000, "EUR"),
        addedAt = Instant.parse("2026-08-01T10:00:00Z"),
        coolingOffDays = 30
    )
    private val price = Money(120_000, "EUR")

    @Test
    fun confirmations() {
        val outcomes = listOf(
            DecisionOutcome.Bought(BoughtConfirmation.Waited(61)),
            DecisionOutcome.Bought(BoughtConfirmation.Waited(1)),
            DecisionOutcome.Bought(BoughtConfirmation.Enjoy),
            DecisionOutcome.Bought(BoughtConfirmation.Neutral),
            DecisionOutcome.Rejected(price, OpportunityCostLine.DelaysPriority(camera, rank = 2, months = 4)),
            DecisionOutcome.Rejected(price, null)
        )
        composeRule.setContent {
            BeforeYouBuyTheme {
                val resources = LocalResources.current
                Column(Modifier.padding(16.dp)) {
                    outcomes.forEach {
                        Snackbar(Modifier.padding(vertical = 4.dp)) { Text(decisionMessage(it, resources, locale)) }
                    }
                }
            }
        }
        composeRule.captureContent()
    }

    @Test
    fun theTextsAreAsDecided() {
        lateinit var texts: List<String>
        composeRule.setContent {
            val resources = LocalResources.current
            texts = listOf(
                decisionMessage(DecisionOutcome.Bought(BoughtConfirmation.Waited(61)), resources, locale),
                decisionMessage(DecisionOutcome.Bought(BoughtConfirmation.Waited(1)), resources, locale),
                decisionMessage(DecisionOutcome.Bought(BoughtConfirmation.Enjoy), resources, locale),
                decisionMessage(DecisionOutcome.Bought(BoughtConfirmation.Neutral), resources, locale),
                decisionMessage(
                    DecisionOutcome.Rejected(price, OpportunityCostLine.DelaysPriority(camera, 2, 4)),
                    resources,
                    locale
                ),
                decisionMessage(DecisionOutcome.Rejected(price, null), resources, locale)
            )
        }
        composeRule.waitForIdle()
        assertEquals(
            listOf(
                t("You waited 61 days for this. Enjoy it.", "Du hast 61 Tage darauf gewartet. Viel Freude damit."),
                t("You waited 1 day for this. Enjoy it.", "Du hast 1 Tag darauf gewartet. Viel Freude damit."),
                t("Enjoy it.", "Viel Freude damit."),
                t("Got it. It’s in “Bought”.", "Alles klar. Es ist jetzt unter „Gekauft“."),
                t(
                    "Let go. €1,200 stays for your Camera (#2).",
                    "Losgelassen. 1.200\u00A0€ bleiben für Kamera (#2)."
                ),
                t("Got it. It’s in “Decided against”.", "Alles klar. Es ist jetzt unter „Dagegen entschieden“.")
            ),
            texts
        )
    }
}

@Config(qualifiers = "en-w411dp-h891dp-notnight-xxhdpi")
class DecisionMessageScreenshotEnTest : DecisionMessageScreenshotTest(german = false)

@Config(qualifiers = "de-w411dp-h891dp-notnight-xxhdpi")
class DecisionMessageScreenshotDeTest : DecisionMessageScreenshotTest(german = true)
