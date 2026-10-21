package io.github.locngu.beforeyoubuy.feature.history

import io.github.locngu.beforeyoubuy.core.domain.coolingoff.CoolingOffRules
import io.github.locngu.beforeyoubuy.core.domain.history.HistoryPeriod
import io.github.locngu.beforeyoubuy.core.domain.item.Decision
import io.github.locngu.beforeyoubuy.core.domain.item.DecisionType
import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.core.domain.settings.Policy
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    /** Two hours before November in UTC, the clock's zone. */
    private val start = Instant.parse("2026-10-31T22:00:00Z")
    private val clock = MutableClock(start)
    private val items = FakeItemRepository()
    private val policies = FakePolicyRepository()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = HistoryViewModel(items, policies, clock)

    private fun TestScope.pass(duration: Duration) {
        clock.advance(duration)
        advanceTimeBy(duration.toMillis() + 1)
    }

    private suspend fun add(name: String, euros: Long): Item {
        val id = items.add(
            Item(
                id = ItemId.NEW,
                name = name,
                price = Money(euros * 100, "EUR"),
                addedAt = Instant.parse("2026-09-01T10:00:00Z"),
                coolingEndsAt = Instant.parse("2026-09-15T10:00:00Z")
            )
        )
        return items.stored.single { it.id == id }
    }

    private suspend fun reject(name: String, euros: Long, at: Instant) {
        val item = add(name, euros)
        items.recordDecision(item, Decision(DecisionType.REJECT, at), item.copy(status = ItemStatus.REJECTED))
    }

    private suspend fun buy(name: String, euros: Long, paid: Long, at: Instant) {
        val item = add(name, euros)
        val decision = Decision(DecisionType.BUY, at, actualPrice = Money(paid * 100, "EUR"))
        items.recordDecision(item, decision, item.copy(status = ItemStatus.PURCHASED))
    }

    @Test
    fun `lists both tabs, newest first, with the decided-against total`() = runTest(UnconfinedTestDispatcher()) {
        buy("Camera", euros = 1_200, paid = 1_150, at = Instant.parse("2026-10-20T10:00:00Z"))
        reject("Bike", euros = 450, at = Instant.parse("2026-10-10T10:00:00Z"))
        reject("Lamp", euros = 80, at = Instant.parse("2026-10-30T10:00:00Z"))
        val viewModel = viewModel()
        viewModel.uiState.launchIn(backgroundScope)

        val state = viewModel.uiState.value
        assertEquals(HistoryTab.BOUGHT, state.tab)
        assertEquals(HistoryPeriod.ALL, state.period)
        assertEquals(listOf("Camera"), state.bought!!.map { it.item.name })
        assertEquals(Money(120_000, "EUR"), state.bought.single().wishlistPrice)
        assertEquals(listOf("Lamp", "Bike"), state.decidedAgainst!!.entries.map { it.item.name })
        assertEquals(Money(53_000, "EUR"), state.decidedAgainst.total)
    }

    @Test
    fun `shows nothing until loaded`() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = viewModel()

        assertNull(viewModel.uiState.value.bought)
        assertNull(viewModel.uiState.value.decidedAgainst)
    }

    @Test
    fun `the tab and period are kept, and the period filters both lists and the total`() =
        runTest(UnconfinedTestDispatcher()) {
            buy("Camera", euros = 1_200, paid = 1_200, at = Instant.parse("2026-09-20T10:00:00Z"))
            reject("Bike", euros = 450, at = Instant.parse("2026-09-10T10:00:00Z"))
            reject("Lamp", euros = 80, at = Instant.parse("2026-10-30T10:00:00Z"))
            val viewModel = viewModel()
            viewModel.uiState.launchIn(backgroundScope)

            viewModel.selectTab(HistoryTab.DECIDED_AGAINST)
            viewModel.selectPeriod(HistoryPeriod.THIS_MONTH)

            val state = viewModel.uiState.value
            assertEquals(HistoryTab.DECIDED_AGAINST, state.tab)
            assertEquals(HistoryPeriod.THIS_MONTH, state.period)
            assertEquals(emptyList<String>(), state.bought!!.map { it.item.name })
            assertEquals(listOf("Lamp"), state.decidedAgainst!!.entries.map { it.item.name })
            assertEquals(Money(8_000, "EUR"), state.decidedAgainst.total)
        }

    @Test
    fun `this month moves on at midnight`() = runTest(UnconfinedTestDispatcher()) {
        reject("Lamp", euros = 80, at = Instant.parse("2026-10-30T10:00:00Z"))
        val viewModel = viewModel()
        viewModel.uiState.launchIn(backgroundScope)
        viewModel.selectPeriod(HistoryPeriod.THIS_MONTH)
        assertEquals(1, viewModel.uiState.value.decidedAgainst!!.entries.size)

        pass(Duration.ofHours(2))

        assertEquals(0, viewModel.uiState.value.decidedAgainst!!.entries.size)
        assertEquals(Money(0, "EUR"), viewModel.uiState.value.decidedAgainst!!.total)
    }

    @Test
    fun `follows new decisions`() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = viewModel()
        viewModel.uiState.launchIn(backgroundScope)
        assertEquals(0, viewModel.uiState.value.decidedAgainst!!.entries.size)

        reject("Lamp", euros = 80, at = start)

        assertEquals(listOf("Lamp"), viewModel.uiState.value.decidedAgainst!!.entries.map { it.item.name })
    }

    @Test
    fun `waits while the history and the currency are read apart, instead of totalling two currencies`() =
        runTest(UnconfinedTestDispatcher()) {
            reject("Lamp", euros = 80, at = start)
            val viewModel = viewModel()
            viewModel.uiState.launchIn(backgroundScope)
            val before = viewModel.uiState.value

            // The policy is relabelled to USD; the items aren't yet.
            policies.policy.value = Policy("USD", CoolingOffRules.defaults("USD"))

            assertEquals(before, viewModel.uiState.value)
        }
}
