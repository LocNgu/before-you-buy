package io.github.locngu.beforeyoubuy.feature.priorities

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RoborazziRule
import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.Reflection
import io.github.locngu.beforeyoubuy.core.domain.item.UsageFrequency
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.core.domain.pool.SavingsProgress
import io.github.locngu.beforeyoubuy.core.domain.priorities.HeadToHead
import io.github.locngu.beforeyoubuy.testing.captureContent
import io.github.locngu.beforeyoubuy.ui.theme.BeforeYouBuyTheme
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The head-to-head (brief §4.7): a comparison with savings, one without fun money and with a missing answer, the
 * summary after winning some, the summary after losing, the slots dialog listing what goes back; en/de × light/dark.
 * Record: `./gradlew :app:recordRoborazziDebug`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class HeadToHeadScreenshotTest(private val german: Boolean) {
    @get:Rule(order = 0)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // Names images after the concrete subclass, not this base class.
    @get:Rule(order = 1)
    val roborazziRule = RoborazziRule()

    private fun t(en: String, de: String) = if (german) de else en

    private val now = Instant.parse("2026-10-24T10:00:00Z")

    private fun item(id: Long, name: String, euros: Long, slot: Int? = null) =
        Item(ItemId(id), name, Money(euros * 100, "EUR"), now, now, prioritySlot = slot)

    private val camera = item(1, t("Mirrorless camera", "Systemkamera"), 1_199, slot = 1)
    private val laptop = item(2, t("Laptop", "Laptop"), 1_400, slot = 2)
    private val bike = item(3, t("Road bike", "Rennrad"), 1_600, slot = 3)
    private val printer = item(4, t("3D printer", "3D-Drucker"), 450)

    private val printerReasons = Reflection(
        why = t("Print spare parts for the bike and the kitchen", "Ersatzteile für Rad und Küche drucken"),
        usageFrequency = UsageFrequency.MONTHLY,
        reasonsAgainst = listOf(t("Takes up the desk", "Braucht den Schreibtisch"), t("Noisy", "Laut"))
    )
    private val bikeReasons = Reflection(
        why = t("Ride to work in summer", "Im Sommer zur Arbeit fahren"),
        usageFrequency = UsageFrequency.WEEKLY,
        reasonsAgainst = listOf(t("The old one still works", "Das alte fährt noch"))
    )

    private fun eur(euros: Long) = Money(euros * 100, "EUR")

    @Test
    fun comparison() = capture(
        HeadToHeadUiState(
            comparison = Comparison(
                ComparisonCard(printer, null, printerReasons, SavingsProgress(eur(150), printer.price)),
                ComparisonCard(bike, 3, bikeReasons, SavingsProgress(eur(650), bike.price))
            )
        )
    )

    @Test
    fun comparisonWithoutFunMoney() = capture(
        HeadToHeadUiState(
            comparison = Comparison(
                ComparisonCard(printer, null, Reflection(why = printerReasons.why), null),
                ComparisonCard(camera, 1, null, null)
            )
        )
    )

    @Test
    fun summaryWonSome() {
        val result = HeadToHead.start(printer, listOf(camera, laptop, bike), slots = 3)
            .choose(printer.id)
            .choose(printer.id)
            .choose(camera.id)
        capture(HeadToHeadUiState(summary = result.outcome!!.lines))
    }

    @Test
    fun summaryLost() {
        val result = HeadToHead.start(printer, listOf(camera, laptop, bike), slots = 3).choose(bike.id)
        capture(HeadToHeadUiState(summary = result.outcome!!.lines))
    }

    @Test
    fun changedMeanwhile() = capture(HeadToHeadUiState(changedMeanwhile = true))

    @Test
    fun slotsDialog() {
        // A dialog is its own window, which screenshots miss: draw its content.
        composeRule.setContent {
            BeforeYouBuyTheme {
                Surface {
                    PrioritySlotsChoice(
                        slots = 1,
                        active = listOf(camera, laptop, bike, printer),
                        onSelect = {},
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }
        }
        composeRule.captureContent()
    }

    private fun capture(state: HeadToHeadUiState) {
        composeRule.setContent {
            BeforeYouBuyTheme { HeadToHeadContent(state = state, onChoose = { _, _ -> }, onDone = {}) }
        }
        composeRule.captureContent()
    }
}

@Config(qualifiers = "en-w411dp-h891dp-notnight-xxhdpi")
class HeadToHeadScreenshotEnLightTest : HeadToHeadScreenshotTest(german = false)

@Config(qualifiers = "en-w411dp-h891dp-night-xxhdpi")
class HeadToHeadScreenshotEnDarkTest : HeadToHeadScreenshotTest(german = false)

@Config(qualifiers = "de-w411dp-h891dp-notnight-xxhdpi")
class HeadToHeadScreenshotDeLightTest : HeadToHeadScreenshotTest(german = true)

@Config(qualifiers = "de-w411dp-h891dp-night-xxhdpi")
class HeadToHeadScreenshotDeDarkTest : HeadToHeadScreenshotTest(german = true)
