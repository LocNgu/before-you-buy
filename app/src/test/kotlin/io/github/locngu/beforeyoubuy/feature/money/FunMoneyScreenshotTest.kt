package io.github.locngu.beforeyoubuy.feature.money

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RoborazziRule
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.core.domain.money.MoneyParseResult
import io.github.locngu.beforeyoubuy.core.domain.pool.AllowanceSettings
import io.github.locngu.beforeyoubuy.core.domain.pool.FunMoney
import io.github.locngu.beforeyoubuy.core.domain.pool.PoolBalance
import io.github.locngu.beforeyoubuy.testing.captureContent
import io.github.locngu.beforeyoubuy.ui.theme.BeforeYouBuyTheme
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The fun money screen: setting up, set up and an input error; en/de × light/dark. The below-zero dialog is its own
 * window, which the capture misses; `FunMoneyFlowTest` and `FunMoneyViewModelTest` cover it. Record:
 * `./gradlew :app:recordRoborazziDebug`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class FunMoneyScreenshotTest(private val german: Boolean) {
    @get:Rule(order = 0)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule(order = 1)
    val roborazziRule = RoborazziRule()

    private fun t(en: String, de: String) = if (german) de else en

    private fun eur(minor: Long) = Money(minor, "EUR")

    private val setUp = FunMoney.SetUp(
        allowance = AllowanceSettings(eur(30_000), 1, LocalDate.parse("2026-09-20")),
        balance = PoolBalance(pool = eur(64_550), committed = eur(0)),
        nextAccrual = LocalDate.parse("2026-11-01"),
        startingBalance = Money(12_000, "EUR")
    )

    @Test
    fun settingUp() = capture(
        FunMoneyUiState(
            money = FunMoney.NotSetUp,
            currency = "EUR",
            form = FunMoneyForm(monthly = "300", startingBalance = "120"),
            firstAccrual = LocalDate.parse("2026-11-01"),
            canSave = true
        )
    )

    @Test
    fun setUp() = capture(
        FunMoneyUiState(
            money = setUp,
            currency = "EUR",
            form = FunMoneyForm(
                monthly = "300",
                accrualDay = 1,
                startingBalance = "120",
                adjustment = "20",
                note = t("Birthday", "Geburtstag")
            ),
            canSave = true,
            canAdjust = true
        )
    )

    @Test
    fun invalidAmount() = capture(
        FunMoneyUiState(
            money = FunMoney.NotSetUp,
            currency = "EUR",
            form = FunMoneyForm(monthly = ""),
            monthlyError = MoneyParseResult.Empty,
            firstAccrual = LocalDate.parse("2026-11-01")
        )
    )

    private fun capture(state: FunMoneyUiState) {
        composeRule.setContent { BeforeYouBuyTheme { FunMoneyContent(state = state, actions = NoActions) } }
        composeRule.captureContent()
    }

    private object NoActions : FunMoneyActions {
        override fun onMonthlyChange(text: String) = Unit

        override fun onAccrualDayChange(day: Int) = Unit

        override fun onStartingBalanceChange(text: String) = Unit

        override fun save() = Unit

        override fun onAdjustmentChange(text: String) = Unit

        override fun onNoteChange(text: String) = Unit

        override fun add() = Unit

        override fun takeOut() = Unit

        override fun confirmTakeOut() = Unit

        override fun dismissTakeOut() = Unit
    }
}

@Config(qualifiers = "en-w411dp-h891dp-notnight-xxhdpi")
class FunMoneyScreenshotEnLightTest : FunMoneyScreenshotTest(german = false)

@Config(qualifiers = "en-w411dp-h891dp-night-xxhdpi")
class FunMoneyScreenshotEnDarkTest : FunMoneyScreenshotTest(german = false)

@Config(qualifiers = "de-w411dp-h891dp-notnight-xxhdpi")
class FunMoneyScreenshotDeLightTest : FunMoneyScreenshotTest(german = true)

@Config(qualifiers = "de-w411dp-h891dp-night-xxhdpi")
class FunMoneyScreenshotDeDarkTest : FunMoneyScreenshotTest(german = true)
