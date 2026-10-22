package io.github.locngu.beforeyoubuy.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RoborazziRule
import io.github.locngu.beforeyoubuy.core.domain.home.homeOverviewOf
import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
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
 * Home on first start (nothing added), in a typical state (items to decide, one cooling off), with everything still
 * cooling off, and while loading; en/de × light/dark. Record: `./gradlew :app:recordRoborazziDebug`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class HomeScreenshotTest(private val german: Boolean) {
    @get:Rule(order = 0)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // Names images after the concrete subclass, not this base class.
    @get:Rule(order = 1)
    val roborazziRule = RoborazziRule()

    private fun t(en: String, de: String) = if (german) de else en

    private val now = Instant.parse("2026-10-16T10:00:00Z")

    private fun item(id: Long, name: String, euros: Long, endsInDays: Long, kind: ItemKind = ItemKind.WANT) = Item(
        id = ItemId(id),
        name = name,
        price = Money(euros * 100, "EUR"),
        addedAt = now.minusSeconds(40L * 24 * 3600),
        coolingEndsAt = now.plusSeconds(endsInDays * 24 * 3600),
        kind = kind
    )

    private val reflection =
        Reflection(why = "Hikes", usageFrequency = UsageFrequency.WEEKLY, reasonsAgainst = listOf("Heavy"))

    private val ready = item(1, t("Mirrorless camera", "Systemkamera"), 1_199, endsInDays = -10)
    private val unreflected = item(2, t("Standing desk", "Stehschreibtisch"), 449, endsInDays = -2)
    private val need = item(3, t("Winter boots", "Winterstiefel"), 129, endsInDays = -40, kind = ItemKind.NEED)
    private val coolingSoon = item(4, t("Road bike", "Rennrad"), 1_890, endsInDays = 12)
    private val coolingLater = item(5, t("Tent", "Zelt"), 320, endsInDays = 60)

    private fun state(items: List<Item>) =
        HomeUiState(homeOverviewOf(items, mapOf(ready.id to reflection), now, ZoneOffset.UTC))

    @Test
    fun firstStart() = capture(state(emptyList()))

    @Test
    fun typical() = capture(state(listOf(coolingLater, unreflected, coolingSoon, need, ready)))

    @Test
    fun nothingToDecide() = capture(state(listOf(coolingLater, coolingSoon)))

    @Test
    fun loading() = capture(HomeUiState(null))

    private fun capture(state: HomeUiState) {
        composeRule.setContent {
            BeforeYouBuyTheme {
                HomeContent(
                    state = state,
                    onAdd = {},
                    onOpenDecision = {},
                    onOpenWishlist = {},
                    onOpenHistory = {},
                    snackbarHostState = SnackbarHostState()
                )
            }
        }
        composeRule.captureContent()
    }
}

@Config(qualifiers = "en-w411dp-h891dp-notnight-xxhdpi")
class HomeScreenshotEnLightTest : HomeScreenshotTest(german = false)

@Config(qualifiers = "en-w411dp-h891dp-night-xxhdpi")
class HomeScreenshotEnDarkTest : HomeScreenshotTest(german = false)

@Config(qualifiers = "de-w411dp-h891dp-notnight-xxhdpi")
class HomeScreenshotDeLightTest : HomeScreenshotTest(german = true)

@Config(qualifiers = "de-w411dp-h891dp-night-xxhdpi")
class HomeScreenshotDeDarkTest : HomeScreenshotTest(german = true)
