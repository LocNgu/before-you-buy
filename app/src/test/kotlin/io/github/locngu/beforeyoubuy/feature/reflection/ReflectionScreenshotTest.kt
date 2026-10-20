package io.github.locngu.beforeyoubuy.feature.reflection

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RoborazziRule
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.UpgradeOrNew
import io.github.locngu.beforeyoubuy.core.domain.item.UsageFrequency
import io.github.locngu.beforeyoubuy.core.domain.reflection.ReflectionForm
import io.github.locngu.beforeyoubuy.core.domain.reflection.ReflectionStep
import io.github.locngu.beforeyoubuy.core.domain.reflection.reflectionPageOf
import io.github.locngu.beforeyoubuy.testing.captureContent
import io.github.locngu.beforeyoubuy.ui.theme.BeforeYouBuyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Each kind of question in the reflection flow, in English and German: a required text question (empty), a required
 * choice, the reasons-against list, an optional text question (to skip), the optional choice on the last step, and
 * gone. Record: `./gradlew :app:recordRoborazziDebug`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ReflectionScreenshotTest(private val german: Boolean) {
    @get:Rule(order = 0)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // Names images after the concrete subclass, not this base class.
    @get:Rule(order = 1)
    val roborazziRule = RoborazziRule()

    private fun t(en: String, de: String) = if (german) de else en

    private val name = t("Mirrorless camera", "Systemkamera")

    @Test
    fun why() = capture(ReflectionStep.WHY, ReflectionForm())

    @Test
    fun usage() = capture(
        ReflectionStep.USAGE,
        ReflectionForm(
            why = t("Better photos on hikes", "Bessere Fotos beim Wandern"),
            usageFrequency = UsageFrequency.MONTHLY
        )
    )

    @Test
    fun reasonsAgainst() = capture(
        ReflectionStep.REASONS_AGAINST,
        ReflectionForm(
            reasonsAgainst = listOf(
                t("My phone takes decent photos", "Mein Handy macht ordentliche Fotos"),
                t("Heavy to carry", "Schwer zu tragen")
            )
        )
    )

    @Test
    fun optionalText() = capture(ReflectionStep.PROBLEM_SOLVED, ReflectionForm())

    @Test
    fun optionalChoice() = capture(ReflectionStep.UPGRADE_OR_NEW, ReflectionForm(upgradeOrNew = UpgradeOrNew.UPGRADE))

    @Test
    fun gone() = capture(ReflectionUiState(gone = true))

    private fun capture(step: ReflectionStep, form: ReflectionForm) = capture(
        ReflectionUiState(itemName = name, form = form, page = reflectionPageOf(step, form, ItemKind.WANT))
    )

    private fun capture(state: ReflectionUiState) {
        composeRule.setContent { BeforeYouBuyTheme { ReflectionContent(state = state, actions = NoActions) } }
        composeRule.captureContent()
    }

    private object NoActions : ReflectionActions {
        override fun onWhyChange(why: String) = Unit

        override fun onUsageChange(usage: UsageFrequency) = Unit

        override fun onReasonChange(index: Int, reason: String) = Unit

        override fun onAddReason() = Unit

        override fun onRemoveReason(index: Int) = Unit

        override fun onProblemSolvedChange(problemSolved: String) = Unit

        override fun onCurrentAlternativeChange(currentAlternative: String) = Unit

        override fun onReplacesChange(replaces: String) = Unit

        override fun onWhyNowChange(whyNow: String) = Unit

        override fun onUpgradeOrNewTap(choice: UpgradeOrNew) = Unit

        override fun next() = Unit

        override fun back() = Unit

        override fun close() = Unit
    }
}

@Config(qualifiers = "en-w411dp-h891dp-notnight-xxhdpi")
class ReflectionScreenshotEnTest : ReflectionScreenshotTest(german = false)

@Config(qualifiers = "de-w411dp-h891dp-notnight-xxhdpi")
class ReflectionScreenshotDeTest : ReflectionScreenshotTest(german = true)
