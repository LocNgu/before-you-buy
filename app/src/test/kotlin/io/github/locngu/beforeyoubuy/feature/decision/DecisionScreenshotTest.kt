package io.github.locngu.beforeyoubuy.feature.decision

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RoborazziRule
import io.github.locngu.beforeyoubuy.core.domain.decision.checkPurchase
import io.github.locngu.beforeyoubuy.core.domain.decision.decisionFormOf
import io.github.locngu.beforeyoubuy.core.domain.decision.decisionOverviewOf
import io.github.locngu.beforeyoubuy.core.domain.decision.earliestWaitDate
import io.github.locngu.beforeyoubuy.core.domain.decision.purchaseDates
import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.Reflection
import io.github.locngu.beforeyoubuy.core.domain.item.UsageFrequency
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.core.domain.pool.Savings
import io.github.locngu.beforeyoubuy.testing.captureContent
import io.github.locngu.beforeyoubuy.ui.theme.BeforeYouBuyTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The decision screen for a Ready want (with other items to spend the money on), a want still cooling off (overview
 * and the early path), a need, and the buy, wait and reject steps, in English and German. Record:
 * `./gradlew :app:recordRoborazziDebug`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class DecisionScreenshotTest(private val german: Boolean) {
    @get:Rule(order = 0)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // Names images after the concrete subclass, not this base class.
    @get:Rule(order = 1)
    val roborazziRule = RoborazziRule()

    private fun t(en: String, de: String) = if (german) de else en

    private val locale = if (german) Locale.GERMAN else Locale.ENGLISH
    private val zone = ZoneOffset.UTC
    private val now = Instant.parse("2026-10-16T10:00:00Z")
    private val today = LocalDate.of(2026, 10, 16)

    private fun item(id: Long, name: String, euros: Long, endsInDays: Long, addedDaysAgo: Long = 61) = Item(
        id = ItemId(id),
        name = name,
        price = Money(euros * 100, "EUR"),
        addedAt = now.minusSeconds(addedDaysAgo * 24 * 3600),
        coolingEndsAt = now.plusSeconds(endsInDays * 24 * 3600)
    )

    private val camera = item(1, t("Mirrorless camera", "Systemkamera"), 1_200, endsInDays = -31)
    private val others = listOf(
        item(2, t("Road bike", "Rennrad"), 1_600, endsInDays = 10, addedDaysAgo = 50).copy(prioritySlot = 1),
        item(3, t("Bike upgrade", "Fahrrad-Upgrade"), 450, endsInDays = 5, addedDaysAgo = 40),
        item(4, t("Standing desk", "Stehschreibtisch"), 449, endsInDays = 2, addedDaysAgo = 30),
        item(5, t("Headphones", "Kopfhörer"), 249, endsInDays = 1, addedDaysAgo = 20)
    )

    private val reasons = Reflection(
        why = t("Better photos on hikes than with my phone", "Bessere Fotos beim Wandern als mit dem Handy"),
        usageFrequency = UsageFrequency.MONTHLY,
        reasonsAgainst = listOf(
            t("My phone takes decent photos", "Mein Handy macht ordentliche Fotos"),
            t("Heavy to carry", "Schwer zu tragen")
        ),
        replaces = t("My old compact camera", "Meine alte Kompaktkamera")
    )

    private val cooling = camera.copy(coolingEndsAt = now.plusSeconds(23L * 24 * 3600))

    private val need = camera.copy(
        name = t("Washing machine", "Waschmaschine"),
        price = Money(54_900, "EUR"),
        kind = ItemKind.NEED,
        addedAt = now.minusSeconds(2L * 24 * 3600),
        coolingEndsAt = now.minusSeconds(2L * 24 * 3600)
    )

    @Test
    fun ready() = capture(camera, reasons, others)

    @Test
    fun early() = capture(cooling, Reflection(why = reasons.why, reasonsAgainst = reasons.reasonsAgainst), others)

    @Test
    fun earlyBuy() = capture(
        cooling,
        Reflection(why = reasons.why, reasonsAgainst = reasons.reasonsAgainst),
        step = DecisionStep.BUY,
        earlyReason = t("It's on sale until Sunday", "Bis Sonntag im Angebot")
    )

    @Test
    fun need() = capture(need, null, others.take(1))

    @Test
    fun buy() = capture(camera, reasons, step = DecisionStep.BUY)

    @Test
    fun waitStep() = capture(camera, reasons, step = DecisionStep.WAIT)

    @Test
    fun reject() = capture(camera, reasons, step = DecisionStep.REJECT)

    @Test
    fun gone() = capture(DecisionUiState(gone = true))

    private fun capture(
        item: Item,
        reflection: Reflection?,
        others: List<Item> = emptyList(),
        step: DecisionStep = DecisionStep.OVERVIEW,
        earlyReason: String = ""
    ) {
        val overview = decisionOverviewOf(item, reflection, listOf(item) + others, Savings.NONE, now, zone)
        val form = decisionFormOf(item, today, locale).copy(earlyReason = earlyReason)
        capture(
            DecisionUiState(
                overview = overview,
                step = step,
                buyPath = overview.buyPath,
                form = form,
                purchase = checkPurchase(form, item, overview.buyPath, locale, today, zone),
                purchaseDates = purchaseDates(item, overview.buyPath, today, zone),
                today = today,
                earliestWait = earliestWaitDate(today)
            )
        )
    }

    private fun capture(state: DecisionUiState) {
        composeRule.setContent {
            BeforeYouBuyTheme {
                DecisionContent(state, NoActions, onReflect = {}, snackbarHostState = SnackbarHostState())
            }
        }
        composeRule.captureContent()
    }

    private object NoActions : DecisionActions {
        override fun onBuy() = Unit

        override fun onWait() = Unit

        override fun onReject() = Unit

        override fun onBackToOverview() = Unit

        override fun onPriceChange(price: String) = Unit

        override fun onBoughtOnChange(date: LocalDate) = Unit

        override fun onEarlyReasonChange(reason: String) = Unit

        override fun onWaitUntilChange(date: LocalDate) = Unit

        override fun onNoteChange(note: String) = Unit

        override fun confirm() = Unit

        override fun confirmBelowZero() = Unit

        override fun dismissBelowZero() = Unit
    }
}

@Config(qualifiers = "en-w411dp-h891dp-notnight-xxhdpi")
class DecisionScreenshotEnTest : DecisionScreenshotTest(german = false)

@Config(qualifiers = "de-w411dp-h891dp-notnight-xxhdpi")
class DecisionScreenshotDeTest : DecisionScreenshotTest(german = true)
