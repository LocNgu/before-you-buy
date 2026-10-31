package io.github.locngu.beforeyoubuy.feature.reflection

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.cash.turbine.test
import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.item.Reflection
import io.github.locngu.beforeyoubuy.core.domain.item.UpgradeOrNew
import io.github.locngu.beforeyoubuy.core.domain.item.UsageFrequency
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.Stage
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.core.domain.reflection.Onward
import io.github.locngu.beforeyoubuy.core.domain.reflection.ReflectionStep
import io.github.locngu.beforeyoubuy.feature.wishlist.ItemDetailViewModel
import io.github.locngu.beforeyoubuy.testing.asPriority
import io.github.locngu.beforeyoubuy.testing.FakeItemImageStore
import io.github.locngu.beforeyoubuy.testing.FakeItemRepository
import io.github.locngu.beforeyoubuy.testing.FakePolicyRepository
import io.github.locngu.beforeyoubuy.testing.FakePoolRepository
import io.github.locngu.beforeyoubuy.testing.MutableClock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
class ReflectionViewModelTest {
    private val start = Instant.parse("2026-10-16T10:00:00Z")
    private val clock = MutableClock(start)
    private val items = FakeItemRepository()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** A 120 € want added 10 days ago whose wait ends [endsInHours] from now (negative: already over). */
    private suspend fun add(endsInHours: Long): Item {
        val id = items.add(
            Item(
                id = ItemId.NEW,
                name = "Camera",
                price = Money(12_000, "EUR"),
                addedAt = start.minus(Duration.ofDays(10)),
                coolingOffDays = 30
            ).asPriority(start.plus(Duration.ofHours(endsInHours)))
        )
        return items.stored.single { it.id == id }
    }

    private fun reflectionFor(item: Item) = ReflectionViewModel(item.id.value, items)

    private fun detailFor(item: Item) = ItemDetailViewModel(
        item.id.value,
        items,
        FakePoolRepository(items),
        FakePolicyRepository(),
        FakeItemImageStore(),
        clock
    )

    private suspend fun storedAnswers(item: Item): Reflection? = items.observeReflection(item.id).first()

    private fun TestScope.pass(duration: Duration) {
        clock.advance(duration)
        advanceTimeBy(duration.toMillis() + 1)
    }

    @Test
    fun `the item moves from needs reflection to ready as soon as the required answers exist`() =
        runTest(UnconfinedTestDispatcher()) {
            val item = add(endsInHours = -1)
            val detail = detailFor(item)
            detail.uiState.launchIn(backgroundScope)
            val flow = reflectionFor(item)
            assertEquals(Stage.NEEDS_REFLECTION, detail.uiState.value.detail!!.stage)

            flow.onWhyChange("Better photos on hikes")
            flow.next()
            flow.onUsageChange(UsageFrequency.WEEKLY)
            flow.next()
            assertEquals(Stage.NEEDS_REFLECTION, detail.uiState.value.detail!!.stage)

            // The first reason against completes the required answers, before "Next".
            flow.onReasonChange(0, "Heavy")

            assertEquals(Stage.READY, detail.uiState.value.detail!!.stage)
            assertEquals(ReflectionStep.REASONS_AGAINST, flow.uiState.value.page!!.step)
        }

    @Test
    fun `answers given while cooling off make it ready the moment the wait ends`() =
        runTest(UnconfinedTestDispatcher()) {
            val item = add(endsInHours = 2)
            val detail = detailFor(item)
            detail.uiState.launchIn(backgroundScope)
            val flow = reflectionFor(item)

            flow.onWhyChange("Hikes")
            flow.next()
            flow.onUsageChange(UsageFrequency.MONTHLY)
            flow.next()
            flow.onReasonChange(0, "Price")
            assertEquals(Stage.COOLING_OFF, detail.uiState.value.detail!!.stage)

            pass(Duration.ofHours(2))

            assertEquals(Stage.READY, detail.uiState.value.detail!!.stage)
        }

    @Test
    fun `leaving mid-flow keeps the answers given, and coming back continues there`() =
        runTest(UnconfinedTestDispatcher()) {
            val item = add(endsInHours = -1)
            val first = reflectionFor(item)
            first.onWhyChange("Hikes")
            first.next()
            // Typed but never confirmed with "Next": kept too.
            first.onUsageChange(UsageFrequency.DAILY)

            assertEquals(Reflection(why = "Hikes", usageFrequency = UsageFrequency.DAILY), storedAnswers(item))

            val again = reflectionFor(item)
            val state = again.uiState.value
            assertEquals(ReflectionStep.REASONS_AGAINST, state.page!!.step)
            assertEquals("Hikes", state.form!!.why)
            assertEquals(UsageFrequency.DAILY, state.form.usageFrequency)
        }

    @Test
    fun `once the required answers are there, it opens at the first question to edit them`() =
        runTest(UnconfinedTestDispatcher()) {
            val item = add(endsInHours = -1)
            items.reflect(
                item.id,
                Reflection(why = "Hikes", usageFrequency = UsageFrequency.WEEKLY, reasonsAgainst = listOf("Heavy"))
            )

            val state = reflectionFor(item).uiState.value

            assertEquals(ReflectionStep.WHY, state.page!!.step)
            assertEquals(listOf("Heavy"), state.form!!.reasonsAgainst)
            assertEquals("Camera", state.itemName)
        }

    @Test
    fun `required questions wait for an answer, optional ones can be skipped`() = runTest(UnconfinedTestDispatcher()) {
        val flow = reflectionFor(add(endsInHours = -1))
        assertFalse(flow.uiState.value.page!!.canGoOn)

        flow.next()
        assertEquals(ReflectionStep.WHY, flow.uiState.value.page!!.step)

        flow.onWhyChange("   ")
        assertFalse(flow.uiState.value.page!!.canGoOn)
        flow.onWhyChange("Hikes")
        assertTrue(flow.uiState.value.page!!.canGoOn)
        flow.next()
        flow.onUsageChange(UsageFrequency.RARELY)
        flow.next()
        flow.onReasonChange(0, "Space")
        flow.next()

        val optional = flow.uiState.value.page!!
        assertEquals(ReflectionStep.PROBLEM_SOLVED, optional.step)
        assertEquals(Onward.SKIP, optional.onward)
        assertTrue(optional.canGoOn)
        flow.next()
        assertEquals(ReflectionStep.CURRENT_ALTERNATIVE, flow.uiState.value.page!!.step)
    }

    @Test
    fun `back goes to the previous question and stops at the first`() = runTest(UnconfinedTestDispatcher()) {
        val flow = reflectionFor(add(endsInHours = -1))
        flow.onWhyChange("Hikes")
        flow.next()

        flow.back()
        assertEquals(ReflectionStep.WHY, flow.uiState.value.page!!.step)
        flow.back()
        assertEquals(ReflectionStep.WHY, flow.uiState.value.page!!.step)
    }

    @Test
    fun `done on the last question stores everything and finishes`() = runTest(UnconfinedTestDispatcher()) {
        val item = add(endsInHours = -1)
        val flow = reflectionFor(item)
        flow.onWhyChange("Hikes")
        flow.next()
        flow.onUsageChange(UsageFrequency.WEEKLY)
        flow.next()
        flow.onReasonChange(0, "Heavy")
        flow.onAddReason()
        flow.onReasonChange(1, "My phone is fine")
        flow.next()
        flow.onProblemSolvedChange("Blurry photos")
        flow.next()
        flow.onCurrentAlternativeChange("My phone")
        flow.next()
        flow.onReplacesChange("Nothing")
        flow.next()
        flow.onWhyNowChange("Trip in spring")
        flow.next()
        flow.onUpgradeOrNewTap(UpgradeOrNew.NEW)
        assertEquals(Onward.DONE, flow.uiState.value.page!!.onward)

        flow.finished.test {
            flow.next()
            awaitItem()
        }

        assertEquals(
            Reflection(
                why = "Hikes",
                usageFrequency = UsageFrequency.WEEKLY,
                reasonsAgainst = listOf("Heavy", "My phone is fine"),
                problemSolved = "Blurry photos",
                currentAlternative = "My phone",
                replaces = "Nothing",
                whyNow = "Trip in spring",
                upgradeOrNew = UpgradeOrNew.NEW
            ),
            storedAnswers(item)
        )
    }

    @Test
    fun `back right after typing still stores the newest answers`() = runTest(UnconfinedTestDispatcher()) {
        val item = add(endsInHours = -1)
        val store = ViewModelStore()
        val flow = ViewModelProvider.create(store, viewModelFactory { initializer { reflectionFor(item) } })
            .get(ReflectionViewModel::class)
        val gate = CompletableDeferred<Unit>()
        items.reflectionGate = gate
        flow.onWhyChange("Hik")
        flow.onWhyChange("Hikes")

        // Back pops the screen: its view model is cleared while the first write still runs.
        store.clear()
        gate.complete(Unit)

        assertEquals(Reflection(why = "Hikes"), storedAnswers(item))
    }

    @Test
    fun `close finishes at once, keeping the answers`() = runTest(UnconfinedTestDispatcher()) {
        val item = add(endsInHours = -1)
        val flow = reflectionFor(item)
        flow.onWhyChange("Hikes")

        flow.finished.test {
            flow.close()
            awaitItem()
        }

        assertEquals(Reflection(why = "Hikes"), storedAnswers(item))
    }

    @Test
    fun `answers never change the item or its wait`() = runTest(UnconfinedTestDispatcher()) {
        val item = add(endsInHours = 24 * 20)
        items.reflect(
            item.id,
            Reflection(why = "Hikes", usageFrequency = UsageFrequency.WEEKLY, reasonsAgainst = listOf("Heavy"))
        )
        val flow = reflectionFor(item)

        flow.onWhyChange("Hikes and city trips")
        flow.next()
        flow.onUsageChange(UsageFrequency.DAILY)

        assertEquals(listOf(item), items.stored)
        assertTrue(items.updates.isEmpty())
    }

    @Test
    fun `answers are stored trimmed, without blank reasons`() = runTest(UnconfinedTestDispatcher()) {
        val item = add(endsInHours = -1)
        val flow = reflectionFor(item)

        flow.onWhyChange("  Hikes ")
        flow.next()
        flow.onUsageChange(UsageFrequency.ONCE)
        flow.next()
        flow.onReasonChange(0, " Heavy ")
        flow.onAddReason()
        flow.onReasonChange(1, "   ")

        assertEquals(
            Reflection(why = "Hikes", usageFrequency = UsageFrequency.ONCE, reasonsAgainst = listOf("Heavy")),
            storedAnswers(item)
        )
        // The form keeps what was typed.
        assertEquals(listOf(" Heavy ", "   "), flow.uiState.value.form!!.reasonsAgainst)
    }

    @Test
    fun `reasons can be added and removed`() = runTest(UnconfinedTestDispatcher()) {
        val item = add(endsInHours = -1)
        val flow = reflectionFor(item)

        flow.onAddReason()
        assertEquals(listOf(""), flow.uiState.value.form!!.reasonsAgainst)

        flow.onReasonChange(0, "Heavy")
        flow.onAddReason()
        flow.onReasonChange(1, "Price")
        flow.onRemoveReason(0)

        assertEquals(listOf("Price"), flow.uiState.value.form!!.reasonsAgainst)
        assertEquals(listOf("Price"), storedAnswers(item)!!.reasonsAgainst)
    }

    @Test
    fun `the optional choice can be cleared again`() = runTest(UnconfinedTestDispatcher()) {
        val item = add(endsInHours = -1)
        val flow = reflectionFor(item)

        flow.onUpgradeOrNewTap(UpgradeOrNew.UPGRADE)
        assertEquals(UpgradeOrNew.UPGRADE, storedAnswers(item)!!.upgradeOrNew)

        flow.onUpgradeOrNewTap(UpgradeOrNew.UPGRADE)
        assertNull(storedAnswers(item)!!.upgradeOrNew)
    }

    @Test
    fun `closing twice finishes once`() = runTest(UnconfinedTestDispatcher()) {
        val flow = reflectionFor(add(endsInHours = -1))

        flow.finished.test {
            flow.close()
            flow.close()
            awaitItem()
            expectNoEvents()
        }
    }

    @Test
    fun `a need's answers are all optional`() = runTest(UnconfinedTestDispatcher()) {
        val need = add(endsInHours = -1).copy(kind = ItemKind.NEED)
        items.replace(need)
        items.reflect(need.id, Reflection(upgradeOrNew = UpgradeOrNew.UPGRADE))
        val flow = reflectionFor(need)

        val first = flow.uiState.value.page!!
        assertEquals(ReflectionStep.WHY, first.step)
        assertFalse(first.required)
        assertEquals(Onward.SKIP, first.onward)
        repeat(7) { flow.next() }
        flow.onUpgradeOrNewTap(UpgradeOrNew.NEW)

        assertEquals(Reflection(upgradeOrNew = UpgradeOrNew.NEW), storedAnswers(need))
    }

    @Test
    fun `opening and leaving without a change writes nothing`() = runTest(UnconfinedTestDispatcher()) {
        val item = add(endsInHours = -1)
        items.reflect(item.id, Reflection(why = "Hikes"))
        val flow = reflectionFor(item)

        flow.next()
        flow.back()
        flow.finished.test {
            flow.close()
            awaitItem()
        }

        assertTrue(items.reflectionWrites.isEmpty())
    }

    @Test
    fun `an item ended meanwhile is gone and keeps the answers it was decided with`() =
        runTest(UnconfinedTestDispatcher()) {
            val item = add(endsInHours = -1)
            val flow = reflectionFor(item)
            flow.onWhyChange("Hikes")

            items.replace(item.copy(status = ItemStatus.REJECTED))
            // Typed as the item ended, then closed.
            flow.onWhyChange("Hikes and city trips")
            flow.close()

            assertTrue(flow.uiState.value.gone)
            assertNull(flow.uiState.value.form)
            assertEquals(listOf(Reflection(why = "Hikes")), items.reflectionWrites)
        }

    @Test
    fun `a deleted item is gone`() = runTest(UnconfinedTestDispatcher()) {
        val item = add(endsInHours = -1)
        val flow = reflectionFor(item)

        items.delete(item.id)

        assertTrue(flow.uiState.value.gone)
    }

    @Test
    fun `an item ended before opening shows it's gone and takes no answers`() = runTest(UnconfinedTestDispatcher()) {
        val item = add(endsInHours = -1).copy(status = ItemStatus.PURCHASED)
        items.replace(item)
        val flow = reflectionFor(item)

        flow.onWhyChange("Hikes")

        assertTrue(flow.uiState.value.gone)
        assertTrue(items.reflectionWrites.isEmpty())
    }
}
