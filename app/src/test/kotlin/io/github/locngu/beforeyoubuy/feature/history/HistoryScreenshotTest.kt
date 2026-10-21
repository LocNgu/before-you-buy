package io.github.locngu.beforeyoubuy.feature.history

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RoborazziRule
import io.github.locngu.beforeyoubuy.core.domain.history.HistoryDetail
import io.github.locngu.beforeyoubuy.core.domain.history.HistoryPeriod
import io.github.locngu.beforeyoubuy.core.domain.history.boughtOf
import io.github.locngu.beforeyoubuy.core.domain.history.decidedAgainstOf
import io.github.locngu.beforeyoubuy.core.domain.item.Decision
import io.github.locngu.beforeyoubuy.core.domain.item.DecisionType
import io.github.locngu.beforeyoubuy.core.domain.item.HistoryEntry
import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.item.Reflection
import io.github.locngu.beforeyoubuy.core.domain.item.UsageFrequency
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.testing.captureContent
import io.github.locngu.beforeyoubuy.ui.theme.BeforeYouBuyTheme
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * History: both tabs with entries, a period with nothing in it, both empty states and loading; a bought and a
 * decided-against entry, and a deleted one; in English and German.
 * Record: `./gradlew :app:recordRoborazziDebug`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class HistoryScreenshotTest(private val german: Boolean) {
    @get:Rule(order = 0)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // Names images after the concrete subclass, not this base class.
    @get:Rule(order = 1)
    val roborazziRule = RoborazziRule()

    private fun t(en: String, de: String) = if (german) de else en

    private val now = Instant.parse("2026-11-17T10:00:00Z")
    private val zone = ZoneOffset.UTC

    private fun item(id: Long, name: String, euros: Long, addedDaysAgo: Long, status: ItemStatus) = Item(
        id = ItemId(id),
        name = name,
        price = Money(euros * 100, "EUR"),
        addedAt = now.minusSeconds(addedDaysAgo * 24 * 3600),
        coolingEndsAt = now.minusSeconds((addedDaysAgo - 14) * 24 * 3600),
        status = status
    )

    private fun daysAgo(days: Long) = now.minusSeconds(days * 24 * 3600)

    private fun bought(
        id: Long,
        name: String,
        euros: Long,
        paid: Long,
        addedDaysAgo: Long,
        boughtDaysAgo: Long,
        type: DecisionType = DecisionType.BUY
    ) = HistoryEntry(
        item(id, name, euros, addedDaysAgo, ItemStatus.PURCHASED),
        Decision(
            type,
            daysAgo(boughtDaysAgo),
            actualPrice = Money(paid * 100, "EUR"),
            earlyReason = t("Trip next week", "Reise nächste Woche").takeIf { type == DecisionType.BUY_EARLY }
        )
    )

    private fun rejected(id: Long, name: String, euros: Long, decidedDaysAgo: Long, note: String? = null) =
        HistoryEntry(
            item(id, name, euros, decidedDaysAgo + 30, ItemStatus.REJECTED),
            Decision(DecisionType.REJECT, daysAgo(decidedDaysAgo), note = note)
        )

    private val boughtEntries = listOf(
        bought(1, t("Mirrorless camera", "Systemkamera"), 1_299, paid = 1_199, addedDaysAgo = 49, boughtDaysAgo = 2),
        bought(2, t("Winter boots", "Winterstiefel"), 140, paid = 140, 3, 3, type = DecisionType.BUY_NEED),
        bought(3, t("Espresso machine", "Siebträgermaschine"), 680, 680, 45, 40, type = DecisionType.BUY_EARLY)
    )

    private val rejectedEntries = listOf(
        rejected(4, t("Road bike", "Rennrad"), 1_890, 5, note = t("I hardly ride", "Ich fahre kaum")),
        rejected(5, t("Standing desk", "Stehschreibtisch"), 449, 20),
        rejected(6, t("Smartwatch", "Smartwatch"), 1_081, 300, note = t("My watch still works", "Meine Uhr geht noch"))
    )

    private fun state(tab: HistoryTab, period: HistoryPeriod = HistoryPeriod.ALL) = HistoryUiState(
        tab = tab,
        period = period,
        bought = boughtOf(boughtEntries, "EUR", period, now, zone),
        decidedAgainst = decidedAgainstOf(rejectedEntries, "EUR", period, now, zone)
    )

    private val empty = HistoryUiState(
        bought = emptyList(),
        decidedAgainst = decidedAgainstOf(emptyList(), "EUR", HistoryPeriod.ALL, now, zone)
    )

    @Test
    fun bought() = capture(state(HistoryTab.BOUGHT))

    @Test
    fun decidedAgainst() = capture(state(HistoryTab.DECIDED_AGAINST))

    @Test
    fun decidedAgainstThisMonth() = capture(state(HistoryTab.DECIDED_AGAINST, HistoryPeriod.THIS_MONTH))

    @Test
    fun boughtEmpty() = capture(empty)

    @Test
    fun decidedAgainstEmpty() = capture(empty.copy(tab = HistoryTab.DECIDED_AGAINST))

    @Test
    fun boughtNothingThisMonth() = capture(
        HistoryUiState(
            period = HistoryPeriod.THIS_MONTH,
            bought = emptyList(),
            decidedAgainst = decidedAgainstOf(emptyList(), "EUR", HistoryPeriod.THIS_MONTH, now, zone)
        )
    )

    @Test
    fun loading() = capture(HistoryUiState())

    private val reflection = Reflection(
        why = t("Better photos on hikes", "Bessere Fotos beim Wandern"),
        usageFrequency = UsageFrequency.WEEKLY,
        reasonsAgainst = listOf(t("My phone takes decent photos", "Mein Handy macht gute Fotos"))
    )

    @Test
    fun boughtDetail() {
        val entry = boughtOf(listOf(boughtEntries[2]), "EUR", HistoryPeriod.ALL, now, zone).single()
        captureDetail(HistoryItemUiState(HistoryDetail.Bought(entry, reflection)))
    }

    @Test
    fun decidedAgainstDetail() {
        val (item, decision) = rejectedEntries[0]
        val withNotes = HistoryEntry(item.copy(category = t("Sport", "Sport"), notes = t("Carbon", "Carbon")), decision)
        val entry = decidedAgainstOf(listOf(withNotes), "EUR", HistoryPeriod.ALL, now, zone).entries.single()
        captureDetail(HistoryItemUiState(HistoryDetail.DecidedAgainst(entry, reflection)))
    }

    @Test
    fun detailGone() = captureDetail(HistoryItemUiState(gone = true))

    private fun capture(state: HistoryUiState) {
        composeRule.setContent {
            BeforeYouBuyTheme {
                HistoryContent(state, onSelectTab = {}, onSelectPeriod = {}, onOpenItem = {})
            }
        }
        composeRule.captureContent()
    }

    private fun captureDetail(state: HistoryItemUiState) {
        composeRule.setContent {
            BeforeYouBuyTheme {
                HistoryItemContent(state, onWantAgain = {}, onDelete = {}, snackbarHostState = SnackbarHostState())
            }
        }
        composeRule.captureContent()
    }
}

@Config(qualifiers = "en-w411dp-h891dp-notnight-xxhdpi")
class HistoryScreenshotEnTest : HistoryScreenshotTest(german = false)

@Config(qualifiers = "de-w411dp-h891dp-notnight-xxhdpi")
class HistoryScreenshotDeTest : HistoryScreenshotTest(german = true)
