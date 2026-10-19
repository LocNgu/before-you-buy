package io.github.locngu.beforeyoubuy.feature.wishlist

import app.cash.turbine.test
import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.core.domain.money.MoneyParseResult
import io.github.locngu.beforeyoubuy.testing.FakeItemImageStore
import io.github.locngu.beforeyoubuy.testing.FakeItemRepository
import io.github.locngu.beforeyoubuy.testing.FakePolicyRepository
import io.github.locngu.beforeyoubuy.testing.MutableClock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ItemEditViewModelTest {
    // 120 € added on 1 Oct: the 30-day tier, so it cools off until 31 Oct.
    private val added = Instant.parse("2026-10-01T10:00:00Z")
    private val coolingEnds = Instant.parse("2026-10-31T10:00:00Z")
    private val clock = MutableClock(Instant.parse("2026-10-16T10:00:00Z"))
    private val items = FakeItemRepository()
    private val policies = FakePolicyRepository()
    private val images = FakeItemImageStore()
    private lateinit var item: Item
    private lateinit var viewModel: ItemEditViewModel

    @Before
    fun setUp() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val id = items.add(
            Item(
                id = ItemId.NEW,
                name = "Camera",
                price = Money(11_999, "EUR"),
                addedAt = added,
                coolingEndsAt = coolingEnds,
                link = "https://shop.example/camera"
            )
        )
        item = items.stored.single { it.id == id }
        viewModel = ItemEditViewModel(id.value, items, policies, images, clock)
        viewModel.setLocale(Locale.GERMANY)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val state get() = viewModel.uiState.value

    @Test
    fun `the form starts with what is stored, the price exact`() {
        val form = state.form!!
        assertEquals("Camera", form.name)
        assertEquals("119,99", form.price)
        assertEquals("https://shop.example/camera", form.link)
        assertTrue(state.canSave)
        assertNull(state.waitNote)
    }

    @Test
    fun `saving an unchanged form keeps the item and closes`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.saved.test {
            viewModel.save()

            awaitItem()
            assertEquals(item, items.stored.single())
        }
    }

    @Test
    fun `a lower price in a shorter tier keeps the countdown`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.onPriceChange("50")
        assertNull(state.waitNote)

        viewModel.saved.test {
            viewModel.save()
            awaitItem()
        }

        val stored = items.stored.single()
        assertEquals(Money(5_000, "EUR"), stored.price)
        assertEquals(coolingEnds, stored.coolingEndsAt)
    }

    @Test
    fun `a higher price in a longer tier says how long it now waits, and extends it`() =
        runTest(UnconfinedTestDispatcher()) {
            viewModel.onPriceChange("600")

            // 60 days from 1 Oct.
            assertEquals(WaitNote.Extended(LocalDate.of(2026, 11, 30)), state.waitNote)

            viewModel.saved.test {
                viewModel.save()
                awaitItem()
            }
            assertEquals(Instant.parse("2026-11-30T10:00:00Z"), items.stored.single().coolingEndsAt)
        }

    @Test
    fun `a need turned back into a want says when its wait ends`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.onNeedChange(true)
        assertNull(state.waitNote)

        items.replace(item.copy(kind = ItemKind.NEED, coolingEndsAt = added))
        val needEditor = ItemEditViewModel(item.id.value, items, policies, images, clock).apply {
            setLocale(Locale.GERMANY)
        }
        needEditor.onNeedChange(false)

        assertEquals(WaitNote.Starts(LocalDate.of(2026, 10, 31)), needEditor.uiState.value.waitNote)
    }

    @Test
    fun `an invalid form can't be saved and says why`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.onNameChange(" ")
        viewModel.onPriceChange("abc")

        assertTrue(state.showNameMissing)
        assertEquals(MoneyParseResult.Invalid, state.priceError)
        assertFalse(state.canSave)
        viewModel.save()
        assertTrue(items.updates.isEmpty())
    }

    @Test
    fun `fields are saved trimmed, blank ones cleared`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.onNameChange(" Camera body ")
        viewModel.onLinkChange("")
        viewModel.onCategoryChange("Photo")
        viewModel.onNotesChange("Silver")
        viewModel.onTargetDateChange(LocalDate.of(2026, 12, 24))

        viewModel.saved.test {
            viewModel.save()
            awaitItem()
        }

        val stored = items.stored.single()
        assertEquals("Camera body", stored.name)
        assertNull(stored.link)
        assertEquals("Photo", stored.category)
        assertEquals("Silver", stored.notes)
        assertEquals(LocalDate.of(2026, 12, 24), stored.targetDate)
    }

    @Test
    fun `an item changed meanwhile isn't overwritten, and saving again uses the newer one`() =
        runTest(UnconfinedTestDispatcher()) {
            viewModel.onPriceChange("600")
            items.conflictOnNextUpdate = true

            viewModel.save()

            assertTrue(state.conflict)
            assertEquals(item, items.stored.single())

            viewModel.saved.test {
                viewModel.save()
                awaitItem()
            }
            assertFalse(state.conflict)
            assertEquals(Money(60_000, "EUR"), items.stored.single().price)
        }

    @Test
    fun `typing after the item changed elsewhere keeps the typed text`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.onNameChange("Camera body")

        items.replace(item.copy(coolingEndsAt = coolingEnds.plus(Duration.ofDays(1))))

        assertEquals("Camera body", state.form!!.name)
    }

    @Test
    fun `a change made elsewhere while editing isn't undone by saving`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.onNameChange("Camera body")
        val need = item.copy(kind = ItemKind.NEED, markedAsNeedAt = clock.instant())
        items.replace(need)

        viewModel.save()

        assertTrue(state.conflict)
        assertEquals(need, items.stored.single())

        // Saving again, after the message, applies the form (still a want) to the newer item.
        viewModel.saved.test {
            viewModel.save()
            awaitItem()
        }
        assertEquals("Camera body", items.stored.single().name)
    }

    @Test
    fun `a picked photo is copied, shown and saved with the item`() = runTest(UnconfinedTestDispatcher()) {
        assertNull(state.photo)

        viewModel.onPhotoPicked("content://picker/42")

        assertEquals(listOf("content://picker/42"), images.imported)
        assertEquals(images.fileFor("42.jpg"), state.photo)
        assertNull(items.stored.single().imageRef)
        viewModel.saved.test {
            viewModel.save()
            awaitItem()
        }
        assertEquals("42.jpg", items.stored.single().imageRef)
    }

    @Test
    fun `an unreadable pick keeps the photo there was and says so`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.onPhotoPicked("content://picker/42")

        viewModel.onPhotoPicked(FakeItemImageStore.UNREADABLE)

        assertTrue(state.photoFailed)
        assertEquals(images.fileFor("42.jpg"), state.photo)
        assertTrue(state.canSave)
    }

    @Test
    fun `a removed photo is gone once saved, and leaving without saving keeps it`() =
        runTest(UnconfinedTestDispatcher()) {
            items.replace(item.copy(imageRef = "old.jpg"))
            val editor = ItemEditViewModel(item.id.value, items, policies, images, clock).apply {
                setLocale(Locale.GERMANY)
            }
            assertEquals(images.fileFor("old.jpg"), editor.uiState.value.photo)

            editor.onRemovePhoto()

            assertNull(editor.uiState.value.photo)
            assertEquals("old.jpg", items.stored.single().imageRef)
            editor.saved.test {
                editor.save()
                awaitItem()
            }
            assertNull(items.stored.single().imageRef)
        }

    @Test
    fun `an item ended meanwhile is gone`() {
        items.replace(item.copy(status = ItemStatus.REJECTED))

        assertTrue(state.gone)
        assertFalse(state.canSave)
    }
}
