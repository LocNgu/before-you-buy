package io.github.locngu.beforeyoubuy.feature.wishlist

import app.cash.turbine.test
import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.item.Reflection
import io.github.locngu.beforeyoubuy.core.domain.item.UsageFrequency
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.Stage
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.testing.FakeItemRepository
import io.github.locngu.beforeyoubuy.testing.FakePolicyRepository
import io.github.locngu.beforeyoubuy.testing.MutableClock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class ItemDetailViewModelTest {
    private val start = Instant.parse("2026-10-16T10:00:00Z")
    private val clock = MutableClock(start)
    private val items = FakeItemRepository()
    private val policies = FakePolicyRepository()

    private val reflection =
        Reflection(why = "Hikes", usageFrequency = UsageFrequency.WEEKLY, reasonsAgainst = listOf("Heavy"))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun add(endsInHours: Long): Item {
        val id = items.add(
            Item(
                id = ItemId.NEW,
                name = "Camera",
                price = Money(12_000, "EUR"),
                addedAt = start.minus(Duration.ofDays(3)),
                coolingEndsAt = start.plus(Duration.ofHours(endsInHours))
            )
        )
        return items.stored.single { it.id == id }
    }

    private fun viewModelFor(item: Item) = ItemDetailViewModel(item.id.value, items, policies, clock)

    private fun TestScope.pass(duration: Duration) {
        clock.advance(duration)
        advanceTimeBy(duration.toMillis() + 1)
    }

    @Test
    fun `shows the stage stageOf gives, and moves on when cooling off ends`() = runTest(UnconfinedTestDispatcher()) {
        val item = add(endsInHours = 2)
        items.reflect(item.id, reflection)
        val viewModel = viewModelFor(item)
        viewModel.uiState.launchIn(backgroundScope)

        val cooling = viewModel.uiState.value.detail!!
        assertEquals(Stage.COOLING_OFF, cooling.stage)
        assertEquals(1, cooling.daysLeft)
        assertEquals(3, cooling.wantedForDays)
        assertFalse(cooling.linkProminent)

        pass(Duration.ofHours(2))

        val ready = viewModel.uiState.value.detail!!
        assertEquals(Stage.READY, ready.stage)
        assertTrue(ready.linkProminent)
    }

    @Test
    fun `the wanted-for count grows at midnight`() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = viewModelFor(add(endsInHours = 24 * 10))
        viewModel.uiState.launchIn(backgroundScope)
        assertEquals(3, viewModel.uiState.value.detail!!.wantedForDays)

        pass(Duration.ofHours(14))

        assertEquals(4, viewModel.uiState.value.detail!!.wantedForDays)
    }

    @Test
    fun `answers given later show up`() = runTest(UnconfinedTestDispatcher()) {
        val item = add(endsInHours = -1)
        val viewModel = viewModelFor(item)
        viewModel.uiState.launchIn(backgroundScope)
        assertFalse(viewModel.uiState.value.detail!!.reflected)

        items.reflect(item.id, reflection)

        assertEquals(reflection, viewModel.uiState.value.detail!!.reflection)
        assertEquals(Stage.READY, viewModel.uiState.value.detail!!.stage)
    }

    @Test
    fun `an item ended meanwhile is gone`() = runTest(UnconfinedTestDispatcher()) {
        val item = add(endsInHours = 24)
        val viewModel = viewModelFor(item)
        viewModel.uiState.launchIn(backgroundScope)

        items.replace(item.copy(status = ItemStatus.REJECTED))

        assertTrue(viewModel.uiState.value.gone)
        assertNull(viewModel.uiState.value.detail)
    }

    @Test
    fun `mark as need makes it a need that is ready at once`() = runTest(UnconfinedTestDispatcher()) {
        val item = add(endsInHours = 24 * 10)
        val viewModel = viewModelFor(item)
        viewModel.uiState.launchIn(backgroundScope)

        viewModel.markAsNeed()

        val stored = items.stored.single()
        assertEquals(ItemKind.NEED, stored.kind)
        assertEquals(start, stored.markedAsNeedAt)
        assertEquals(item.coolingEndsAt, stored.coolingEndsAt)
        assertEquals(Stage.READY, viewModel.uiState.value.detail!!.stage)
    }

    @Test
    fun `mark as need on an item changed meanwhile writes nothing and says so`() = runTest(UnconfinedTestDispatcher()) {
        val item = add(endsInHours = 24 * 10)
        val viewModel = viewModelFor(item)
        viewModel.uiState.launchIn(backgroundScope)
        viewModel.message.test {
            items.conflictOnNextUpdate = true

            viewModel.markAsNeed()

            assertEquals(ItemDetailMessage.CHANGED_MEANWHILE, awaitItem())
            assertEquals(ItemKind.WANT, items.stored.single().kind)
        }
    }
}
