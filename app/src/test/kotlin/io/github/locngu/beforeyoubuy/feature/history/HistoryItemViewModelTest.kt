package io.github.locngu.beforeyoubuy.feature.history

import app.cash.turbine.test
import io.github.locngu.beforeyoubuy.core.domain.history.HistoryDetail
import io.github.locngu.beforeyoubuy.core.domain.item.Decision
import io.github.locngu.beforeyoubuy.core.domain.item.DecisionType
import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.item.Reflection
import io.github.locngu.beforeyoubuy.core.domain.item.UsageFrequency
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.testing.FakeItemRepository
import io.github.locngu.beforeyoubuy.testing.FakePolicyRepository
import io.github.locngu.beforeyoubuy.testing.MutableClock
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
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
class HistoryItemViewModelTest {
    private val now = Instant.parse("2026-11-17T10:00:00Z")
    private val clock = MutableClock(now)
    private val items = FakeItemRepository()
    private val policies = FakePolicyRepository()

    private val reflection =
        Reflection(why = "Hikes", usageFrequency = UsageFrequency.WEEKLY, reasonsAgainst = listOf("Heavy"))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModelFor(id: ItemId) = HistoryItemViewModel(id.value, items, policies, clock)

    private suspend fun add(kind: ItemKind = ItemKind.WANT): Item {
        val id = items.add(
            Item(
                id = ItemId.NEW,
                name = "Camera",
                price = Money(120_000, "EUR"),
                addedAt = Instant.parse("2026-10-01T10:00:00Z"),
                coolingEndsAt = Instant.parse("2026-10-31T10:00:00Z"),
                kind = kind,
                link = "https://shop.example/camera",
                imageRef = "camera.jpg",
                notes = "The silver one"
            )
        )
        items.reflect(id, reflection)
        return items.stored.single { it.id == id }
    }

    private suspend fun rejected(kind: ItemKind = ItemKind.WANT): Item {
        val item = add(kind)
        val ended = item.copy(status = ItemStatus.REJECTED)
        items.recordDecision(item, Decision(DecisionType.REJECT, now, note = "Too heavy"), ended)
        return ended
    }

    private suspend fun bought(): Item {
        val item = add()
        val ended = item.copy(status = ItemStatus.PURCHASED)
        val decision = Decision(DecisionType.BUY_EARLY, now, actualPrice = Money(115_000, "EUR"), earlyReason = "Trip")
        items.recordDecision(item, decision, ended)
        return ended
    }

    @Test
    fun `shows a purchase with what was paid and the original reasons`() = runTest(UnconfinedTestDispatcher()) {
        val item = bought()
        val viewModel = viewModelFor(item.id)
        viewModel.uiState.launchIn(backgroundScope)

        val detail = viewModel.uiState.value.detail as HistoryDetail.Bought
        assertEquals(Money(115_000, "EUR"), detail.entry.paid)
        assertEquals(Money(120_000, "EUR"), detail.entry.wishlistPrice)
        assertTrue(detail.entry.early)
        assertEquals(47, detail.entry.consideredForDays)
        assertEquals(reflection, detail.reflection)
    }

    @Test
    fun `shows a decided-against item with its note`() = runTest(UnconfinedTestDispatcher()) {
        val item = rejected()
        val viewModel = viewModelFor(item.id)
        viewModel.uiState.launchIn(backgroundScope)

        val detail = viewModel.uiState.value.detail as HistoryDetail.DecidedAgainst
        assertEquals("Too heavy", detail.entry.note)
        assertEquals(reflection, detail.reflection)
    }

    @Test
    fun `an active or missing item is gone`() = runTest(UnconfinedTestDispatcher()) {
        val active = add()
        val activeViewModel = viewModelFor(active.id)
        val missingViewModel = viewModelFor(ItemId(99))
        activeViewModel.uiState.launchIn(backgroundScope)
        missingViewModel.uiState.launchIn(backgroundScope)

        assertTrue(activeViewModel.uiState.value.gone)
        assertTrue(missingViewModel.uiState.value.gone)
    }

    @Test
    fun `want it again adds a new want that cools off from now, and the entry stays`() =
        runTest(UnconfinedTestDispatcher()) {
            val old = rejected(ItemKind.NEED)
            val viewModel = viewModelFor(old.id)
            viewModel.uiState.launchIn(backgroundScope)

            viewModel.outcome.test {
                viewModel.wantAgain()

                val added = (awaitItem() as HistoryItemOutcome.WantedAgain).id
                val again = items.stored.single { it.id == added }
                assertEquals(ItemStatus.ACTIVE, again.status)
                assertEquals(ItemKind.WANT, again.kind)
                assertEquals(now, again.addedAt)
                assertTrue(again.coolingEndsAt > now)
                assertEquals(
                    old.copy(id = added, status = ItemStatus.ACTIVE),
                    again.copy(
                        kind = old.kind,
                        addedAt = old.addedAt,
                        coolingEndsAt = old.coolingEndsAt
                    )
                )
            }
            assertEquals(old, items.stored.single { it.id == old.id })
            assertTrue(viewModel.uiState.value.detail is HistoryDetail.DecidedAgainst)
            assertFalse(viewModel.uiState.value.busy)
        }

    @Test
    fun `want it again does nothing for a purchase`() = runTest(UnconfinedTestDispatcher()) {
        val item = bought()
        val viewModel = viewModelFor(item.id)
        viewModel.uiState.launchIn(backgroundScope)

        viewModel.wantAgain()

        assertEquals(1, items.stored.size)
    }

    @Test
    fun `delete removes the entry, closes without showing it gone`() = runTest(UnconfinedTestDispatcher()) {
        val item = rejected()
        val viewModel = viewModelFor(item.id)
        viewModel.uiState.launchIn(backgroundScope)

        viewModel.outcome.test {
            viewModel.delete()

            assertEquals(HistoryItemOutcome.Deleted, awaitItem())
        }
        assertEquals(listOf(item), items.deleted)
        assertNull(items.stored.find { it.id == item.id })
        assertFalse(viewModel.uiState.value.gone)
        assertTrue(viewModel.uiState.value.detail is HistoryDetail.DecidedAgainst)
    }

    @Test
    fun `a delete that finds the entry changed deletes nothing and says so`() = runTest(UnconfinedTestDispatcher()) {
        val item = bought()
        val viewModel = viewModelFor(item.id)
        viewModel.uiState.launchIn(backgroundScope)
        items.conflictOnNextDelete = true

        viewModel.message.test {
            viewModel.delete()

            assertEquals(HistoryItemMessage.CHANGED_MEANWHILE, awaitItem())
        }
        assertEquals(emptyList<Item>(), items.deleted)
        assertTrue(viewModel.uiState.value.detail is HistoryDetail.Bought)
        assertFalse(viewModel.uiState.value.busy)
    }
}
