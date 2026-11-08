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
import io.github.locngu.beforeyoubuy.core.domain.wishlist.Wishlist
import io.github.locngu.beforeyoubuy.core.domain.wishlist.wishlistOf
import io.github.locngu.beforeyoubuy.testing.asPriority
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
 * The wishlist (ADR-0049): priorities in each stage, a need and wants without a slot, with photo thumbnails, with a
 * Ready priority waiting after "Wait", without priorities, empty, and while loading, in English and German.
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

    /** A priority in [slot] whose wait ends [endsInDays] from now. */
    private fun item(id: Long, name: String, euros: Long, endsInDays: Long, slot: Int = 1) = Item(
        id = ItemId(id),
        name = name,
        price = Money(euros * 100, "EUR"),
        addedAt = now.minusSeconds(40L * 24 * 3600),
        coolingOffDays = 30
    ).asPriority(now.plusSeconds(endsInDays * 24 * 3600), slot)

    /** A want without a slot that has waited [waitedDays] of its 30 (ADR-0049). */
    private fun parkedItem(id: Long, name: String, euros: Long, waitedDays: Int = 0) = Item(
        id = ItemId(id),
        name = name,
        price = Money(euros * 100, "EUR"),
        addedAt = now.minusSeconds(id * 24 * 3600),
        coolingOffDays = 30,
        waitedDays = waitedDays
    )

    private val reflection =
        Reflection(why = "Hikes", usageFrequency = UsageFrequency.WEEKLY, reasonsAgainst = listOf("Heavy"))

    // Three slots, all used (the default); everything else is a need or parked.
    private val ready = item(1, t("Mirrorless camera", "Systemkamera"), 1_199, endsInDays = -10, slot = 1)
    private val unreflected = item(2, t("Standing desk", "Stehschreibtisch"), 449, endsInDays = -2, slot = 2)
    private val coolingSoon =
        item(3, t("Noise-cancelling headphones", "Kopfhörer mit Geräuschunterdrückung"), 249, endsInDays = 1, slot = 3)
    private val need = Item(
        id = ItemId(4),
        name = t("Winter boots", "Winterstiefel"),
        price = Money(140_00, "EUR"),
        addedAt = now.minusSeconds(3L * 24 * 3600),
        coolingOffDays = 30,
        kind = ItemKind.NEED
    )
    private val bike = parkedItem(5, t("Road bike", "Rennrad"), 1_890, waitedDays = 12)
    private val lamp = parkedItem(6, t("Floor lamp", "Stehlampe"), 89)
    private val watch = parkedItem(7, t("Smartwatch", "Smartwatch"), 329, waitedDays = 30)

    private val all = listOf(bike, unreflected, lamp, coolingSoon, need, ready, watch)

    /** Priorities in every stage by rank, then a need, then the wishlist: not started, partly and fully waited. */
    @Test
    fun allStages() = capture(
        WishlistUiState(
            wishlistOf(all, mapOf(ready.id to reflection), Savings.NONE, now, ZoneOffset.UTC),
            active = all
        )
    )

    /** Fun money set up and wants need their price: the camera saves; the bike still holds some from a slot. */
    @Test
    fun withSavings() = capture(
        WishlistUiState(
            wishlistOf(
                all,
                mapOf(ready.id to reflection),
                Savings(
                    setUp = true,
                    required = true,
                    byItem = mapOf(ready.id to Money(65_000, "EUR"), bike.id to Money(40_000, "EUR"))
                ),
                now,
                ZoneOffset.UTC
            ),
            active = all
        )
    )

    @Test
    fun withPhotos() = capture(
        WishlistUiState(
            wishlistOf(
                listOf(ready, unreflected, bike),
                mapOf(ready.id to reflection),
                Savings.NONE,
                now,
                ZoneOffset.UTC
            ),
            photos = mapOf(
                ready.id to testPhoto(folder.root, "camera.jpg"),
                bike.id to testPhoto(folder.root, "bike.jpg", width = 600, height = 900)
            )
        )
    )

    /** A Ready priority after "Wait": "Looking again later" and the date. */
    @Test
    fun waiting() {
        val waited = item(8, t("Espresso machine", "Siebträgermaschine"), 680, endsInDays = -12, slot = 1)
            .copy(recheckAt = Instant.parse("2026-10-30T00:00:00Z"))
        val active = listOf(waited, coolingSoon.copy(prioritySlot = 2), ready.copy(prioritySlot = 3))
        capture(
            WishlistUiState(
                wishlistOf(
                    active,
                    mapOf(ready.id to reflection, waited.id to reflection),
                    Savings.NONE,
                    now,
                    ZoneOffset.UTC
                ),
                active = active
            )
        )
    }

    /** No priorities yet, as after the first captures: the empty slots with a hint, then the wishlist (ADR-0049). */
    @Test
    fun noPriorities() {
        val active = listOf(bike, lamp, watch)
        capture(WishlistUiState(wishlistOf(active, emptyMap(), Savings.NONE, now, ZoneOffset.UTC), active = active))
    }

    @Test
    fun empty() = capture(WishlistUiState(Wishlist.EMPTY))

    @Test
    fun loading() = capture(WishlistUiState(null))

    /** Two categories in use: the chips show, and "Photo" keeps only its items in every section (#83). */
    @Test
    fun filteredByCategory() {
        val categorized = all.map {
            when (it.id) {
                bike.id, lamp.id -> it.copy(category = t("Home", "Zuhause"))
                else -> it.copy(category = t("Photo", "Foto"))
            }
        }
        val chosen = t("Photo", "Foto")
        capture(
            WishlistUiState(
                wishlistOf(
                    categorized,
                    mapOf(ready.id to reflection),
                    Savings.NONE,
                    now,
                    ZoneOffset.UTC,
                    category = chosen
                ),
                active = categorized,
                category = chosen
            )
        )
    }

    private fun capture(state: WishlistUiState) {
        composeRule.setContent {
            BeforeYouBuyTheme {
                WishlistContent(
                    state,
                    onAdd = {},
                    onOpenItem = {},
                    snackbarHostState = SnackbarHostState()
                )
            }
        }
        composeRule.captureContent()
    }
}

@Config(qualifiers = "en-w411dp-h891dp-notnight-xxhdpi")
class WishlistScreenshotEnTest : WishlistScreenshotTest(german = false)

@Config(qualifiers = "de-w411dp-h891dp-notnight-xxhdpi")
class WishlistScreenshotDeTest : WishlistScreenshotTest(german = true)
