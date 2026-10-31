package io.github.locngu.beforeyoubuy.feature.home

import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.item.Reflection
import io.github.locngu.beforeyoubuy.core.domain.item.UsageFrequency
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.Stage
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.core.domain.pool.FunMoney
import io.github.locngu.beforeyoubuy.core.domain.pool.PoolBalance
import io.github.locngu.beforeyoubuy.testing.asPriority
import io.github.locngu.beforeyoubuy.testing.FakeItemRepository
import io.github.locngu.beforeyoubuy.testing.FakePoolRepository
import io.github.locngu.beforeyoubuy.testing.MutableClock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
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
class HomeViewModelTest {
    private val start = Instant.parse("2026-10-16T10:00:00Z")
    private val clock = MutableClock(start)
    private val items = FakeItemRepository()
    private val pool = FakePoolRepository(items)
    private lateinit var viewModel: HomeViewModel

    private val reflection =
        Reflection(why = "Hikes", usageFrequency = UsageFrequency.WEEKLY, reasonsAgainst = listOf("Heavy"))

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = HomeViewModel(items, pool, clock)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun add(name: String, endsInHours: Long, kind: ItemKind = ItemKind.WANT): Item {
        val id =
            items.add(
                Item(
                    id = ItemId.NEW,
                    name = name,
                    price = Money(12_000, "EUR"),
                    addedAt = start,
                    coolingOffDays = 30,
                    kind = kind
                ).asPriority(start.plus(Duration.ofHours(endsInHours)))
            )
        return items.stored.first { it.id == id }
    }

    private val overview get() = viewModel.uiState.value.overview

    private val toDecide get() = overview!!.toDecide.map { it.item.name to it.stage }

    /** Moves the clock and the coroutine time together, as real time does. */
    private fun TestScope.pass(duration: Duration) {
        clock.advance(duration)
        advanceTimeBy(duration.toMillis() + 1)
    }

    @Test
    fun `has no overview until the first load, then an empty one`() = runTest(UnconfinedTestDispatcher()) {
        assertNull(overview)

        viewModel.uiState.launchIn(backgroundScope)

        assertEquals(emptyList<Pair<String, Stage>>(), toDecide)
        assertEquals(0, overview!!.activeCount)
        assertNull(overview!!.nextCoolingEnd)
    }

    @Test
    fun `sections reflect the derived stages`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.uiState.launchIn(backgroundScope)
        val ready = add("Camera", endsInHours = -48)
        add("Desk", endsInHours = -24)
        add("Glasses", endsInHours = 24 * 30, kind = ItemKind.NEED)
        add("Bike", endsInHours = 24 * 12)
        add("Tent", endsInHours = 24 * 40)
        items.reflect(ready.id, reflection)

        assertEquals(
            listOf("Camera" to Stage.READY, "Glasses" to Stage.READY, "Desk" to Stage.NEEDS_REFLECTION),
            toDecide
        )
        assertEquals(5, overview!!.activeCount)
        assertEquals("Bike", overview!!.nextCoolingEnd!!.item.name)
        assertEquals(12, overview!!.nextCoolingEnd!!.daysLeft)
    }

    @Test
    fun `an item moves to the decide section when its cooling off ends while Home is open`() =
        runTest(UnconfinedTestDispatcher()) {
            viewModel.uiState.launchIn(backgroundScope)
            add("Camera", endsInHours = 3)
            assertEquals(emptyList<Pair<String, Stage>>(), toDecide)
            assertEquals("Camera", overview!!.nextCoolingEnd!!.item.name)

            pass(Duration.ofHours(3))

            assertEquals(listOf("Camera" to Stage.NEEDS_REFLECTION), toDecide)
            assertNull(overview!!.nextCoolingEnd)
        }

    @Test
    fun `the days left drop at midnight with no change in the database`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.uiState.launchIn(backgroundScope)
        add("Camera", endsInHours = 24 * 3)
        assertEquals(3, overview!!.nextCoolingEnd!!.daysLeft)

        // 10:00 → midnight is 14 hours.
        pass(Duration.ofHours(14))

        assertEquals(2, overview!!.nextCoolingEnd!!.daysLeft)
    }

    @Test
    fun `a waited item leaves the decide section until its re-check date`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.uiState.launchIn(backgroundScope)
        val camera = add("Camera", endsInHours = -48)
        items.reflect(camera.id, reflection)
        items.replace(camera.copy(recheckAt = start.plus(Duration.ofHours(5))))

        assertEquals(emptyList<Pair<String, Stage>>(), toDecide)
        assertEquals(1, overview!!.activeCount)

        pass(Duration.ofHours(5))

        assertEquals(listOf("Camera" to Stage.READY), toDecide)
    }

    @Test
    fun `fun money is offered for setup until it exists`() = runTest(UnconfinedTestDispatcher()) {
        assertNull(viewModel.uiState.value.money)

        viewModel.uiState.launchIn(backgroundScope)

        assertEquals(FunMoney.NotSetUp, viewModel.uiState.value.money)
    }

    @Test
    fun `fun money shows the balance with nothing set aside yet`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.uiState.launchIn(backgroundScope)

        pool.setUp(Money(30_000, "EUR"), 1, Money(5_000, "EUR"), true, start, clock.zone)
        pool.adjust(Money(-1_000, "EUR"), null, start)

        val money = viewModel.uiState.value.money as FunMoney.SetUp
        assertEquals(PoolBalance(Money(4_000, "EUR"), Money(0, "EUR")), money.balance)
        assertEquals(Money(4_000, "EUR"), money.balance.unallocated)
        assertEquals(LocalDate.parse("2026-11-01"), money.nextAccrual)

        // The monthly amount arrives (app start or the daily worker) while Home is open.
        clock.advance(Duration.ofDays(16))
        pool.accrue(clock.instant(), clock.zone)
        assertEquals(Money(34_000, "EUR"), (viewModel.uiState.value.money as FunMoney.SetUp).balance.pool)
    }

    @Test
    fun `an ended item leaves Home`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.uiState.launchIn(backgroundScope)
        val camera = add("Camera", endsInHours = -48, kind = ItemKind.NEED)
        assertEquals(listOf("Camera" to Stage.READY), toDecide)

        items.replace(camera.copy(status = ItemStatus.REJECTED))

        assertEquals(emptyList<Pair<String, Stage>>(), toDecide)
        assertEquals(0, overview!!.activeCount)
    }

    @Test
    fun `saving for lists wants by progress and fun money counts what is set aside`() =
        runTest(UnconfinedTestDispatcher()) {
            viewModel.uiState.launchIn(backgroundScope)
            pool.setUp(Money(30_000, "EUR"), 1, Money(20_000, "EUR"), true, start, clock.zone)
            val camera = add("Camera", endsInHours = 24)
            val bike = add("Bike", endsInHours = 48)
            add("Fridge", endsInHours = 0, kind = ItemKind.NEED)
            pool.setAside(bike.id, Money(9_000, "EUR"), start)
            pool.setAside(camera.id, Money(3_000, "EUR"), start)

            assertEquals(listOf("Bike", "Camera"), overview!!.savingFor.map { it.item.name })
            val money = viewModel.uiState.value.money as FunMoney.SetUp
            assertEquals(PoolBalance(Money(20_000, "EUR"), Money(12_000, "EUR")), money.balance)
        }

    @Test
    fun `without fun money nothing is listed as saved for`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.uiState.launchIn(backgroundScope)
        add("Camera", endsInHours = 24)
        assertEquals(emptyList<Any>(), overview!!.savingFor)
    }
}
