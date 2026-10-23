package io.github.locngu.beforeyoubuy.feature.wishlist

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RoborazziRule
import io.github.locngu.beforeyoubuy.core.domain.capture.CaptureForm
import io.github.locngu.beforeyoubuy.core.domain.money.MoneyParseResult
import io.github.locngu.beforeyoubuy.testing.captureContent
import io.github.locngu.beforeyoubuy.testing.testPhoto
import io.github.locngu.beforeyoubuy.ui.theme.BeforeYouBuyTheme
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The edit screen: a price that extends the wait, an invalid form, a save that met a change, photos. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ItemEditScreenshotTest(private val german: Boolean) {
    @get:Rule(order = 0)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule(order = 1)
    val roborazziRule = RoborazziRule()

    @get:Rule(order = 2)
    val folder = TemporaryFolder()

    private fun t(en: String, de: String) = if (german) de else en

    private val form = CaptureForm(
        name = t("Mirrorless camera", "Systemkamera"),
        price = t("1,199.00", "1.199,00"),
        link = "https://shop.example/camera",
        category = t("Photo", "Foto"),
        targetDate = LocalDate.of(2026, 12, 24)
    )

    private val state =
        ItemEditUiState(form = form, canSave = true, currency = "EUR", today = LocalDate.of(2026, 10, 16))

    @Test
    fun waitExtended() = capture(state.copy(waitNote = WaitNote.Extended(LocalDate.of(2027, 1, 14))))

    @Test
    fun invalid() = capture(
        state.copy(
            form = form.copy(name = "", price = "12,3,4"),
            showNameMissing = true,
            priceError = MoneyParseResult.Invalid,
            canSave = false
        )
    )

    @Test
    fun conflict() = capture(state.copy(conflict = true))

    @Test
    fun withPhoto() = capture(state.copy(photo = testPhoto(folder.root)))

    @Test
    fun photoFailed() = capture(state.copy(photoFailed = true))

    private fun capture(state: ItemEditUiState) {
        composeRule.setContent {
            BeforeYouBuyTheme { ItemEditContent(state = state, actions = NoActions, onPickPhoto = {}) }
        }
        composeRule.captureContent()
    }

    private object NoActions : ItemEditActions {
        override fun onNameChange(name: String) = Unit

        override fun confirmRelease() = Unit

        override fun dismissRelease() = Unit

        override fun onPriceChange(price: String) = Unit

        override fun onLinkChange(link: String) = Unit

        override fun onNeedChange(need: Boolean) = Unit

        override fun onCategoryChange(category: String) = Unit

        override fun onNotesChange(notes: String) = Unit

        override fun onTargetDateChange(date: LocalDate?) = Unit

        override fun onPhotoPicked(uri: String) = Unit

        override fun onRemovePhoto() = Unit

        override fun save() = Unit
    }
}

@Config(qualifiers = "en-w411dp-h891dp-notnight-xxhdpi")
class ItemEditScreenshotEnTest : ItemEditScreenshotTest(german = false)

@Config(qualifiers = "de-w411dp-h891dp-notnight-xxhdpi")
class ItemEditScreenshotDeTest : ItemEditScreenshotTest(german = true)
