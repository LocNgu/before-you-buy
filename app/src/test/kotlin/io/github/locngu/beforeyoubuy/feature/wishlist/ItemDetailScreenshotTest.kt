package io.github.locngu.beforeyoubuy.feature.wishlist

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RoborazziRule
import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.Reflection
import io.github.locngu.beforeyoubuy.core.domain.item.UsageFrequency
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.core.domain.pool.Savings
import io.github.locngu.beforeyoubuy.core.domain.wishlist.itemDetailOf
import io.github.locngu.beforeyoubuy.testing.captureContent
import io.github.locngu.beforeyoubuy.testing.testPhoto
import io.github.locngu.beforeyoubuy.ui.theme.BeforeYouBuyTheme
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Item detail while cooling off (no reasons yet, and with some), when Ready, waiting after "Wait", for a need, with a
 * photo (small while cooling off, larger when Ready) and once gone, in English and German. Record:
 * `./gradlew :app:recordRoborazziDebug`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ItemDetailScreenshotTest(private val german: Boolean) {
    @get:Rule(order = 0)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // Names images after the concrete subclass, not this base class.
    @get:Rule(order = 1)
    val roborazziRule = RoborazziRule()

    @get:Rule(order = 2)
    val folder = TemporaryFolder()

    private fun t(en: String, de: String) = if (german) de else en

    private val now = Instant.parse("2026-10-16T10:00:00Z")

    private val camera = Item(
        id = ItemId(1),
        name = t("Mirrorless camera", "Systemkamera"),
        price = Money(119_900, "EUR"),
        addedAt = now.minusSeconds(40L * 24 * 3600),
        coolingEndsAt = now.plusSeconds(23L * 24 * 3600),
        link = "https://shop.example/camera"
    )

    private val reasons = Reflection(
        why = t("Better photos on hikes than with my phone", "Bessere Fotos beim Wandern als mit dem Handy"),
        usageFrequency = UsageFrequency.MONTHLY,
        reasonsAgainst = listOf(
            t("My phone takes decent photos", "Mein Handy macht ordentliche Fotos"),
            t("Heavy to carry", "Schwer zu tragen")
        ),
        replaces = t("Nothing, it's new", "Nichts, es ist neu")
    )

    @Test
    fun coolingOffWithoutReasons() = capture(camera, null)

    @Test
    fun coolingOffWithSomeReasons() = capture(
        camera.copy(category = t("Photo", "Foto"), targetDate = LocalDate.of(2026, 12, 24)),
        Reflection(why = reasons.why)
    )

    @Test
    fun ready() = capture(camera.copy(coolingEndsAt = now.minusSeconds(5L * 24 * 3600)), reasons)

    @Test
    fun waiting() = capture(
        camera.copy(
            coolingEndsAt = now.minusSeconds(5L * 24 * 3600),
            recheckAt = Instant.parse("2026-10-30T00:00:00Z")
        ),
        reasons
    )

    @Test
    fun need() = capture(
        camera.copy(
            name = t("Washing machine", "Waschmaschine"),
            price = Money(54_900, "EUR"),
            kind = ItemKind.NEED,
            addedAt = now,
            coolingEndsAt = now,
            link = null,
            notes = t("The old one leaks", "Die alte ist undicht")
        ),
        null
    )

    @Test
    fun coolingOffWithPhoto() = capture(camera, Reflection(why = reasons.why), testPhoto(folder.root))

    @Test
    fun readyWithPhoto() =
        capture(camera.copy(coolingEndsAt = now.minusSeconds(5L * 24 * 3600)), reasons, testPhoto(folder.root))

    @Test
    fun gone() = capture(ItemDetailUiState(gone = true))

    private fun capture(item: Item, reflection: Reflection?, photo: File? = null) =
        capture(ItemDetailUiState(itemDetailOf(item, reflection, Savings.NONE, now, ZoneOffset.UTC), photo = photo))

    private fun capture(state: ItemDetailUiState) {
        composeRule.setContent {
            BeforeYouBuyTheme {
                ItemDetailContent(
                    state = state,
                    onEdit = {},
                    onReflect = {},
                    onDecide = {},
                    onMarkAsNeed = {},
                    onCopyLink = {},
                    onOpenLink = {},
                    snackbarHostState = SnackbarHostState()
                )
            }
        }
        composeRule.captureContent()
    }
}

@Config(qualifiers = "en-w411dp-h891dp-notnight-xxhdpi")
class ItemDetailScreenshotEnTest : ItemDetailScreenshotTest(german = false)

@Config(qualifiers = "de-w411dp-h891dp-notnight-xxhdpi")
class ItemDetailScreenshotDeTest : ItemDetailScreenshotTest(german = true)
