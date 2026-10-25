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
import io.github.locngu.beforeyoubuy.core.domain.wishlist.wishlistOf
import io.github.locngu.beforeyoubuy.core.domain.wishlist.wishlistSectionsOf
import io.github.locngu.beforeyoubuy.testing.captureContent
import io.github.locngu.beforeyoubuy.testing.testPhoto
import io.github.locngu.beforeyoubuy.ui.theme.BeforeYouBuyTheme
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The wishlist with an item in each stage, with photo thumbnails, with a Ready item waiting after "Wait", empty, and
 * while loading, in English and German.
 * Record: `./gradlew :app:recordRoborazziDebug`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class WishlistScreenshotTest(private val german: Boolean) {
    @get:Rule(order = 0)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // Names images after the concrete subclass, not this base class.
    @get:Rule(order = 1)
    val roborazziRule = RoborazziRule()

    @get:Rule(order = 2)
    val folder = TemporaryFolder()

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
    private val coolingSoon =
        item(3, t("Noise-cancelling headphones", "Kopfhörer mit Geräuschunterdrückung"), 249, endsInDays = 1)
    private val coolingLater = item(4, t("Road bike", "Rennrad"), 1_890, endsInDays = 23)

    @Test
    fun allStages() = capture(
        WishlistUiState(
            wishlistOf(
                listOf(coolingLater, unreflected, coolingSoon, ready),
                mapOf(ready.id to reflection),
                Savings.NONE,
                now,
                ZoneOffset.UTC
            )
        )
    )

    /** Fun money set up and wants need their price: the camera saves, the bike has some set aside. */
    @Test
    fun withSavings() = capture(
        WishlistUiState(
            wishlistOf(
                listOf(coolingLater, unreflected, coolingSoon, ready),
                mapOf(ready.id to reflection),
                Savings(
                    setUp = true,
                    required = true,
                    byItem = mapOf(ready.id to Money(65_000, "EUR"), coolingLater.id to Money(40_000, "EUR"))
                ),
                now,
                ZoneOffset.UTC
            )
        )
    )

    @Test
    fun withPhotos() = capture(
        WishlistUiState(
            wishlistOf(
                listOf(coolingLater, unreflected, ready),
                mapOf(ready.id to reflection),
                Savings.NONE,
                now,
                ZoneOffset.UTC
            ),
            photos = mapOf(
                ready.id to testPhoto(folder.root, "camera.jpg"),
                coolingLater.id to testPhoto(folder.root, "bike.jpg", width = 600, height = 900)
            )
        )
    )

    @Test
    fun waiting() {
        val waited = item(5, t("Espresso machine", "Siebträgermaschine"), 680, endsInDays = -12)
            .copy(recheckAt = Instant.parse("2026-10-30T00:00:00Z"))
        capture(
            WishlistUiState(
                wishlistOf(
                    listOf(waited, coolingSoon, ready),
                    mapOf(ready.id to reflection, waited.id to reflection),
                    Savings.NONE,
                    now,
                    ZoneOffset.UTC
                )
            )
        )
    }

    /** Two of three slots used: the priorities by rank at the top, out of their stage groups (ADR-0047). */
    @Test
    fun priorities() {
        val active = listOf(coolingLater.copy(prioritySlot = 1), unreflected, coolingSoon, ready.copy(prioritySlot = 2))
        val sections = wishlistSectionsOf(
            wishlistOf(active, mapOf(ready.id to reflection), Savings.NONE, now, ZoneOffset.UTC)
        )
        capture(WishlistUiState(sections.groups, priorities = sections.priorities, active = active))
    }

    @Test
    fun empty() = capture(WishlistUiState(emptyList()))

    @Test
    fun loading() = capture(WishlistUiState(null))

    private fun capture(state: WishlistUiState) {
        composeRule.setContent {
            BeforeYouBuyTheme {
                WishlistContent(state, onAdd = {}, onOpenItem = {}, snackbarHostState = SnackbarHostState())
            }
        }
        composeRule.captureContent()
    }
}

@Config(qualifiers = "en-w411dp-h891dp-notnight-xxhdpi")
class WishlistScreenshotEnTest : WishlistScreenshotTest(german = false)

@Config(qualifiers = "de-w411dp-h891dp-notnight-xxhdpi")
class WishlistScreenshotDeTest : WishlistScreenshotTest(german = true)
