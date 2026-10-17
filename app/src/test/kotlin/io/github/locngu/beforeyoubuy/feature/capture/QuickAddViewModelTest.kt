package io.github.locngu.beforeyoubuy.feature.capture

import app.cash.turbine.test
import io.github.locngu.beforeyoubuy.core.domain.capture.CaptureForm
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.core.domain.money.MoneyParseResult
import io.github.locngu.beforeyoubuy.testing.FakeItemRepository
import io.github.locngu.beforeyoubuy.testing.FakePolicyRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
class QuickAddViewModelTest {
    private val berlin: ZoneId = ZoneId.of("Europe/Berlin")
    private val now = Instant.parse("2026-10-16T10:00:00Z")
    private val clock = Clock.fixed(now, berlin)

    private val items = FakeItemRepository()
    private lateinit var viewModel: QuickAddViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = QuickAddViewModel(items, FakePolicyRepository(), clock)
        viewModel.setLocale(Locale.GERMANY)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val state get() = viewModel.uiState.value

    private fun fill(name: String, price: String) {
        viewModel.onNameChange(name)
        viewModel.onPriceChange(price)
    }

    @Test
    fun `opens with an empty form that shows no errors and can't be saved`() {
        assertFalse(state.visible)

        viewModel.open()

        assertTrue(state.visible)
        assertEquals(CaptureForm(), state.form)
        assertFalse(state.showNameMissing)
        assertNull(state.priceError)
        assertFalse(state.canSave)
        assertEquals("EUR", state.currency)
        assertEquals(LocalDate.of(2026, 10, 16), state.today)
    }

    @Test
    fun `name and price are enough to save`() = runTest {
        viewModel.open()
        fill("Camera", "120")
        assertTrue(state.canSave)

        viewModel.saved.test {
            viewModel.save()
            assertEquals(QuickAddSaved(ItemKind.WANT, 30), awaitItem())
        }

        val item = items.stored.single()
        assertEquals("Camera", item.name)
        assertEquals(Money(12_000, "EUR"), item.price)
        assertEquals(now, item.addedAt)
        // 30 calendar days at the same wall-clock time in Berlin: noon CEST to noon CET, across the clock change.
        assertEquals(Instant.parse("2026-11-15T11:00:00Z"), item.coolingEndsAt)
        assertEquals(ItemKind.WANT, item.kind)
        assertFalse(state.visible)
    }

    @Test
    fun `prices read the German and the English way`() {
        viewModel.open()
        fill("TV", "1.234,56")
        viewModel.save()

        viewModel.setLocale(Locale.US)
        viewModel.open()
        fill("TV", "1,234.56")
        viewModel.save()

        assertEquals(listOf(Money(123_456, "EUR"), Money(123_456, "EUR")), items.stored.map { it.price })
    }

    @Test
    fun `the snackbar says each default tier's waiting period`() = runTest {
        viewModel.saved.test {
            for ((price, days) in listOf("50" to 7, "100" to 30, "500" to 60, "1000" to 90)) {
                viewModel.open()
                fill("Thing", price)
                viewModel.save()
                assertEquals("price $price", QuickAddSaved(ItemKind.WANT, days), awaitItem())
            }
        }
    }

    @Test
    fun `a need is added without waiting`() = runTest {
        viewModel.open()
        fill("Fridge", "600")
        viewModel.onNeedChange(true)

        viewModel.saved.test {
            viewModel.save()
            assertEquals(QuickAddSaved(ItemKind.NEED, 0), awaitItem())
        }
        assertEquals(ItemKind.NEED, items.stored.single().kind)
        assertEquals(now, items.stored.single().coolingEndsAt)
    }

    @Test
    fun `errors show once a field was typed in`() {
        viewModel.open()

        viewModel.onNameChange("C")
        viewModel.onNameChange("")
        assertTrue(state.showNameMissing)
        assertNull(state.priceError) // untouched

        viewModel.onPriceChange("abc")
        assertEquals(MoneyParseResult.Invalid, state.priceError)
        viewModel.onPriceChange("12,345")
        assertEquals(MoneyParseResult.TooManyDecimals(2), state.priceError)
        viewModel.onPriceChange("")
        assertEquals(MoneyParseResult.Empty, state.priceError)
        assertFalse(state.canSave)

        fill("Camera", "12,34")
        assertFalse(state.showNameMissing)
        assertNull(state.priceError)
        assertTrue(state.canSave)
    }

    @Test
    fun `an invalid form isn't saved`() {
        viewModel.open()
        fill("Camera", "abc")

        viewModel.save()

        assertTrue(items.stored.isEmpty())
        assertTrue(state.visible)
    }

    @Test
    fun `optional details are stored`() {
        viewModel.open()
        fill("Camera", "120")
        viewModel.onLinkChange("https://example.com/camera")
        viewModel.onDetailsExpandedChange(true)
        viewModel.onCategoryChange("Photo")
        viewModel.onNotesChange("The small one")
        viewModel.onTargetDateChange(LocalDate.of(2026, 12, 24))
        assertTrue(state.detailsExpanded)

        viewModel.save()

        val item = items.stored.single()
        assertEquals("https://example.com/camera", item.link)
        assertEquals("Photo", item.category)
        assertEquals("The small one", item.notes)
        assertEquals(LocalDate.of(2026, 12, 24), item.targetDate)
    }

    @Test
    fun `opens prefilled, showing what's wrong with a prefilled price`() {
        viewModel.open(QuickAddPrefill(name = "Camera", price = "about 120", link = "https://example.com/camera"))

        assertEquals(CaptureForm(name = "Camera", price = "about 120", link = "https://example.com/camera"), state.form)
        assertEquals(MoneyParseResult.Invalid, state.priceError)
        assertFalse(state.showNameMissing)
    }

    @Test
    fun `closing discards the form and reopening starts empty`() {
        viewModel.open()
        fill("Camera", "120")
        viewModel.onDetailsExpandedChange(true)

        viewModel.dismiss()
        assertFalse(state.visible)
        assertTrue(items.stored.isEmpty())

        viewModel.open()
        assertEquals(CaptureForm(), state.form)
        assertFalse(state.detailsExpanded)
        assertFalse(state.canSave)
    }

    @Test
    fun `saving twice stores one item`() {
        viewModel.open()
        fill("Camera", "120")

        viewModel.save()
        viewModel.save()

        assertEquals(1, items.stored.size)
    }

    @Test
    fun `a failed save leaves the form open and usable`() {
        viewModel.open()
        fill("Camera", "120")
        items.failNextAdd = true

        viewModel.save() // The failure itself still surfaces, as the coroutine's uncaught exception.

        assertTrue(state.visible)
        assertTrue(state.canSave)
        viewModel.onNameChange("Camera 2")
        assertEquals("Camera 2", state.form.name)
        assertTrue(items.stored.isEmpty())
    }

    @Test
    fun `nothing can be saved before the UI's locale is known`() {
        val fresh = QuickAddViewModel(items, FakePolicyRepository(), clock)
        fresh.open()
        fresh.onNameChange("Camera")
        fresh.onPriceChange("120")

        assertFalse(fresh.uiState.value.canSave)
        fresh.save()
        assertTrue(items.stored.isEmpty())
    }
}
