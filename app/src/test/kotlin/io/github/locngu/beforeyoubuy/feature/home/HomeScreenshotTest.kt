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
import io.github.locngu.beforeyoubuy.core.domain.pool.AllowanceSettings
import io.github.locngu.beforeyoubuy.core.domain.pool.FunMoney
import io.github.locngu.beforeyoubuy.core.domain.pool.PoolBalance
import io.github.locngu.beforeyoubuy.core.domain.pool.Savings
import io.github.locngu.beforeyoubuy.testing.asPriority
import io.github.locngu.beforeyoubuy.testing.captureContent
import io.github.locngu.beforeyoubuy.ui.theme.BeforeYouBuyTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Home on first start (nothing added, fun money not set up), in a typical state (items to decide, one cooling off,
 * fun money set up), with everything still cooling off, and while loading; en/de × light/dark. Record: `./gradlew :app:recordRoborazziDebug`.
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

    /** A priority in [slot] whose wait ends [endsInDays] from now, a want without a slot, or a need (ADR-0049). */
    private fun item(
        id: Long,
        name: String,
        euros: Long,
        endsInDays: Long,
        kind: ItemKind = ItemKind.WANT,
        slot: Int? = null
    ): Item {
        val captured = Item(
            id = ItemId(id),
            name = name,
            price = Money(euros * 100, "EUR"),
            addedAt = now.minusSeconds(40L * 24 * 3600),
            coolingOffDays = 30,
            kind = kind
        )
        return if (slot == null) captured else captured.asPriority(now.plusSeconds(endsInDays * 24 * 3600), slot)
    }

    private val reflection =
        Reflection(why = "Hikes", usageFrequency = UsageFrequency.WEEKLY, reasonsAgainst = listOf("Heavy"))

    // Three priorities fill the slots; the tent is parked, with money set aside before it lost its slot.
    private val ready = item(1, t("Mirrorless camera", "Systemkamera"), 1_199, endsInDays = -10, slot = 1)
    private val unreflected = item(2, t("Standing desk", "Stehschreibtisch"), 449, endsInDays = -2, slot = 2)
    private val need = item(3, t("Winter boots", "Winterstiefel"), 129, endsInDays = -40, kind = ItemKind.NEED)
    private val coolingSoon = item(4, t("Road bike", "Rennrad"), 1_890, endsInDays = 12, slot = 3)
    private val coolingLater = item(5, t("Tent", "Zelt"), 320, endsInDays = 60)

    private val funMoney = FunMoney.SetUp(
        allowance = AllowanceSettings(Money(30_000, "EUR"), 1, LocalDate.parse("2026-09-20"), wantsNeedFunding = false),
        balance = PoolBalance(pool = Money(104_550, "EUR"), committed = Money(70_000, "EUR")),
        nextAccrual = LocalDate.parse("2026-11-01"),
        startingBalance = Money(12_000, "EUR")
    )

    // The switch is off here, so the camera is to decide without its price set aside (ADR-0046).
    private val savings = Savings(
        setUp = true,
        required = false,
        byItem = mapOf(coolingSoon.id to Money(60_000, "EUR"), coolingLater.id to Money(10_000, "EUR"))
    )

    private fun state(items: List<Item>, money: FunMoney? = FunMoney.NotSetUp) = HomeUiState(
        homeOverviewOf(
            items,
            mapOf(ready.id to reflection),
            if (money is FunMoney.SetUp) savings else Savings.NONE,
            now,
            ZoneOffset.UTC
        ),
        money
    )

    @Test
    fun firstStart() = capture(state(emptyList()))

    @Test
    fun typical() = capture(state(listOf(coolingLater, unreflected, coolingSoon, need, ready), funMoney))

    @Test
    fun nothingToDecide() = capture(state(listOf(coolingLater, coolingSoon), funMoney))

    /** Without fun money, each priority shows its price instead of what is set aside (ADR-0047). */
    @Test
    fun priorities() = capture(state(listOf(coolingLater, unreflected, coolingSoon, need, ready)))

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
                    onOpenFunMoney = {},
                    onOpenItem = {},
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
