package io.github.locngu.beforeyoubuy.feature.capture

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RoborazziRule
import io.github.locngu.beforeyoubuy.core.domain.capture.CaptureForm
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.money.MoneyParseResult
import io.github.locngu.beforeyoubuy.testing.captureContent
import io.github.locngu.beforeyoubuy.ui.theme.BeforeYouBuyTheme
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The quick-add form in each state, in English and German. The sheet itself is a separate window that the
 * capture doesn't include, so the form is drawn on the sheet's colour. Record: `./gradlew :app:recordRoborazziDebug`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class QuickAddScreenshotTest(private val german: Boolean) {
    @get:Rule(order = 0)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // Names images after the concrete subclass, not this base class.
    @get:Rule(order = 1)
    val roborazziRule = RoborazziRule()

    private fun t(en: String, de: String) = if (german) de else en

    private val opened = QuickAddUiState(visible = true, currency = "EUR", today = LocalDate.of(2026, 10, 16))

    @Test
    fun empty() = capture(opened)

    @Test
    fun filled() = capture(
        opened.copy(
            form = CaptureForm(
                name = t("Noise-cancelling headphones", "Kopfhörer mit Geräuschunterdrückung"),
                price = t("249.99", "249,99"),
                link = "https://example.com/headphones",
                category = t("Music", "Musik"),
                notes = t("The ones Sam has", "Die, die Sam hat"),
                targetDate = LocalDate.of(2026, 12, 24)
            ),
            detailsExpanded = true,
            canSave = true
        )
    )

    @Test
    fun error() = capture(
        opened.copy(
            form = CaptureForm(price = t("about 100", "etwa 100")),
            showNameMissing = true,
            priceError = MoneyParseResult.Invalid
        )
    )

    @Test
    fun need() = capture(
        opened.copy(
            form = CaptureForm(name = t("Fridge", "Kühlschrank"), price = "600", kind = ItemKind.NEED),
            canSave = true
        )
    )

    private fun capture(state: QuickAddUiState) {
        composeRule.setContent {
            BeforeYouBuyTheme {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    QuickAddForm(state = state, actions = NoActions)
                }
            }
        }
        composeRule.captureContent()
    }

    private object NoActions : QuickAddActions {
        override fun onNameChange(name: String) = Unit

        override fun onPriceChange(price: String) = Unit

        override fun onLinkChange(link: String) = Unit

        override fun onNeedChange(need: Boolean) = Unit

        override fun onDetailsExpandedChange(expanded: Boolean) = Unit

        override fun onCategoryChange(category: String) = Unit

        override fun onNotesChange(notes: String) = Unit

        override fun onTargetDateChange(date: LocalDate?) = Unit

        override fun save() = Unit
    }
}

@Config(qualifiers = "en-w411dp-h891dp-notnight-xxhdpi")
class QuickAddScreenshotEnTest : QuickAddScreenshotTest(german = false)

@Config(qualifiers = "de-w411dp-h891dp-notnight-xxhdpi")
class QuickAddScreenshotDeTest : QuickAddScreenshotTest(german = true)
