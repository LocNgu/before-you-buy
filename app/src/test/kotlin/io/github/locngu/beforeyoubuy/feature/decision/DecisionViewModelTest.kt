package io.github.locngu.beforeyoubuy.feature.decision

import app.cash.turbine.test
import io.github.locngu.beforeyoubuy.core.domain.decision.BuyPath
import io.github.locngu.beforeyoubuy.core.domain.decision.OpportunityCostLine
import io.github.locngu.beforeyoubuy.core.domain.decision.decisionFormOf
import io.github.locngu.beforeyoubuy.core.domain.decision.keepFor
import io.github.locngu.beforeyoubuy.core.domain.decision.rejectDecision
import io.github.locngu.beforeyoubuy.core.domain.item.DecisionType
import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.item.Reflection
import io.github.locngu.beforeyoubuy.core.domain.item.UsageFrequency
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.Stage
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.core.domain.pool.Savings
import io.github.locngu.beforeyoubuy.core.domain.wishlist.wishlistOf
import io.github.locngu.beforeyoubuy.testing.asPriority
import io.github.locngu.beforeyoubuy.testing.FakeItemRepository
import io.github.locngu.beforeyoubuy.testing.FakePoolRepository
import io.github.locngu.beforeyoubuy.testing.MutableClock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
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
class DecisionViewModelTest {
    private val start = Instant.parse("2026-11-02T10:00:00Z")
    private val today = LocalDate.of(2026, 11, 2)
    private val clock = MutableClock(start)
    private val items = FakeItemRepository()
    private val pool = FakePoolRepository(items)

    private val reflection =
        Reflection(why = "Hikes", usageFrequency = UsageFrequency.WEEKLY, reasonsAgainst = listOf("Heavy"))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** A 1.200 € item added 30 days ago; its cooling-off ends [endsInHours] from now. */
    private suspend fun add(
        endsInHours: Long = -24,
        kind: ItemKind = ItemKind.WANT,
        name: String = "Camera",
        reflected: Boolean = true
    ): Item {
        val id = items.add(
            Item(
                id = ItemId.NEW,
                name = name,
                price = Money(120_000, "EUR"),
                addedAt = start.minus(Duration.ofDays(30)),
                coolingOffDays = 30,
                kind = kind
            ).asPriority(start.plus(Duration.ofHours(endsInHours)))
        )
        if (reflected) items.reflect(id, reflection)
        return items.stored.single { it.id == id }
    }

    private fun TestScope.viewModelFor(item: Item) = DecisionViewModel(item.id.value, items, pool, clock).also {
        it.uiState.launchIn(backgroundScope)
        it.setLocale(Locale.GERMANY)
    }

    private fun stored(item: Item) = items.stored.single { it.id == item.id }

    @Test
    fun `a ready want offers buy and wait, with the exact price and today prefilled`() =
        runTest(UnconfinedTestDispatcher()) {
            val item = add()
            add(name = "Bike upgrade", reflected = false)
            val viewModel = viewModelFor(item)

            val state = viewModel.uiState.value
            assertEquals(Stage.READY, state.overview!!.stage)
            assertEquals(BuyPath.BUY, state.overview!!.buyPath)
            assertEquals("1200", state.form!!.price)
            assertEquals(today, state.form!!.boughtOn)
            assertEquals(today.plusDays(7), state.form!!.waitUntil)
        }

    @Test
    fun `buy records the price paid and the date, then the screen closes`() = runTest(UnconfinedTestDispatcher()) {
        val item = add()
        val viewModel = viewModelFor(item)

        viewModel.outcome.test {
            viewModel.onBuy()
            assertEquals(DecisionStep.BUY, viewModel.uiState.value.step)
            viewModel.onPriceChange("1100")
            viewModel.confirm()

            assertEquals(DecisionOutcome.Bought, awaitItem())
        }
        val decision = items.decisions.single()
        assertEquals(DecisionType.BUY, decision.type)
        assertEquals(Money(110_000, "EUR"), decision.actualPrice)
        assertEquals(start, decision.decidedAt)
        assertEquals(ItemStatus.PURCHASED, stored(item).status)
        // While the screen closes it keeps showing the item, not "gone".
        assertFalse(viewModel.uiState.value.gone)
    }

    @Test
    fun `a want that isn't ready offers no plain buy and no wait, only reject and buying early`() =
        runTest(UnconfinedTestDispatcher()) {
            val item = add(endsInHours = 24 * 5)
            val viewModel = viewModelFor(item)
            assertEquals(BuyPath.EARLY, viewModel.uiState.value.overview!!.buyPath)

            viewModel.onWait()
            assertEquals(DecisionStep.OVERVIEW, viewModel.uiState.value.step)

            viewModel.onBuy()
            assertEquals(DecisionStep.BUY, viewModel.uiState.value.step)
            assertEquals(BuyPath.EARLY, viewModel.uiState.value.buyPath)
        }

    @Test
    fun `buying early needs a reason, and is recorded as early with it`() = runTest(UnconfinedTestDispatcher()) {
        val item = add(endsInHours = 24 * 5)
        val viewModel = viewModelFor(item)
        viewModel.onBuy()

        viewModel.confirm()
        assertTrue(viewModel.uiState.value.purchase!!.reasonMissing)
        assertTrue(items.decisions.isEmpty())

        viewModel.outcome.test {
            viewModel.onEarlyReasonChange("Old one broke")
            assertTrue(viewModel.uiState.value.purchase!!.isValid)
            viewModel.confirm()
            assertEquals(DecisionOutcome.Bought, awaitItem())
        }
        val decision = items.decisions.single()
        assertEquals(DecisionType.BUY_EARLY, decision.type)
        assertEquals("Old one broke", decision.earlyReason)
    }

    @Test
    fun `an early purchase stays early when cooling off ends while typing`() = runTest(UnconfinedTestDispatcher()) {
        val item = add(endsInHours = 1)
        val viewModel = viewModelFor(item)
        viewModel.onBuy()

        clock.advance(Duration.ofHours(2))
        advanceTimeBy(Duration.ofHours(2).toMillis())
        assertEquals(BuyPath.BUY, viewModel.uiState.value.overview!!.buyPath)
        assertEquals(BuyPath.EARLY, viewModel.uiState.value.buyPath)

        viewModel.onEarlyReasonChange("Gift")
        viewModel.confirm()

        assertEquals(DecisionType.BUY_EARLY, items.decisions.single().type)
    }

    @Test
    fun `a need is bought as a need`() = runTest(UnconfinedTestDispatcher()) {
        val item = add(endsInHours = 24 * 5, kind = ItemKind.NEED, reflected = false)
        val viewModel = viewModelFor(item)
        assertEquals(BuyPath.BUY_NEED, viewModel.uiState.value.overview!!.buyPath)

        viewModel.onBuy()
        viewModel.confirm()

        assertEquals(DecisionType.BUY_NEED, items.decisions.single().type)
        assertEquals(ItemStatus.PURCHASED, stored(item).status)
    }

    @Test
    fun `reject records the optional note and says so`() = runTest(UnconfinedTestDispatcher()) {
        val item = add(endsInHours = 24 * 5)
        val viewModel = viewModelFor(item)

        viewModel.outcome.test {
            viewModel.onReject()
            viewModel.onNoteChange(" Phone is enough ")
            viewModel.confirm()
            assertEquals(DecisionOutcome.Rejected, awaitItem())
        }
        val decision = items.decisions.single()
        assertEquals(DecisionType.REJECT, decision.type)
        assertEquals("Phone is enough", decision.note)
        assertEquals(ItemStatus.REJECTED, stored(item).status)
    }

    @Test
    fun `wait sets the re-check date and hides the item from ready until then`() = runTest(UnconfinedTestDispatcher()) {
        val item = add()
        val viewModel = viewModelFor(item)

        viewModel.outcome.test {
            viewModel.onWait()
            viewModel.onWaitUntilChange(today.plusDays(14))
            viewModel.confirm()
            assertEquals(DecisionOutcome.Waiting(today.plusDays(14)), awaitItem())
        }
        val waited = stored(item)
        assertEquals(ItemStatus.ACTIVE, waited.status)
        assertEquals(Instant.parse("2026-11-16T00:00:00Z"), waited.recheckAt)
        val reflections = items.observeReflections().first()
        val groups = wishlistOf(listOf(waited), reflections, Savings.NONE, clock.instant(), ZoneOffset.UTC)
        assertTrue(groups.single().waiting)
        val due = wishlistOf(listOf(waited), reflections, Savings.NONE, waited.recheckAt!!, ZoneOffset.UTC)
        assertFalse(due.single().waiting)
    }

    @Test
    fun `a decision that raced with another change writes nothing and says so`() = runTest(UnconfinedTestDispatcher()) {
        val item = add()
        val viewModel = viewModelFor(item)
        viewModel.onReject()

        viewModel.message.test {
            items.conflictOnNextDecision = true
            viewModel.confirm()
            assertEquals(DecisionMessage.CHANGED_MEANWHILE, awaitItem())
        }
        assertTrue(items.decisions.isEmpty())
        assertEquals(DecisionStep.OVERVIEW, viewModel.uiState.value.step)
    }

    @Test
    fun `an item ended elsewhere shows as gone`() = runTest(UnconfinedTestDispatcher()) {
        val item = add()
        val viewModel = viewModelFor(item)

        items.replace(item.copy(status = ItemStatus.REJECTED))

        assertTrue(viewModel.uiState.value.gone)
        assertNull(viewModel.uiState.value.overview)
    }

    private suspend fun setUpFunMoney(starting: Long) {
        pool.setUp(Money(30_000, "EUR"), 1, Money(starting, "EUR"), true, start, clock.zone)
    }

    @Test
    fun `what else this money could do comes from the fun money and the priorities`() =
        runTest(UnconfinedTestDispatcher()) {
            // 300 € a month, nothing yet; a 1.600 € priority. Buying the 1.200 € camera delays it by 4 months.
            setUpFunMoney(starting = 0)
            val camera = add()
            val bikeId = items.add(
                Item(
                    id = ItemId.NEW,
                    name = "Bike",
                    price = Money(160_000, "EUR"),
                    addedAt = start.minus(Duration.ofDays(10)),
                    coolingOffDays = 30,
                    prioritySlot = 1
                ).asPriority(start.plus(Duration.ofDays(20)))
            )
            val bike = items.stored.single { it.id == bikeId }
            val viewModel = viewModelFor(camera)

            val lines = viewModel.uiState.value.overview!!.opportunityCost
            assertEquals(
                listOf(
                    OpportunityCostLine.DelaysPriority(bike, 1, 4),
                    OpportunityCostLine.ShareOfGoal(bike, 1, 75),
                    OpportunityCostLine.MonthsOfAllowance(4, exact = true)
                ),
                lines
            )
            assertEquals(bike, lines.keepFor()?.priority)

            // Rejecting the bike leaves nothing to compare with but the fun money.
            val reject = rejectDecision(decisionFormOf(bike, today, Locale.GERMANY), start)
            items.recordDecision(bike, reject, bike.copy(status = ItemStatus.REJECTED, prioritySlot = null))
            assertEquals(
                listOf(OpportunityCostLine.MonthsOfAllowance(4, exact = true)),
                viewModel.uiState.value.overview!!.opportunityCost
            )
        }

    @Test
    fun `once fun money is set up, a want is Ready only when its price is set aside`() =
        runTest(UnconfinedTestDispatcher()) {
            setUpFunMoney(starting = 200_000)
            val item = add()
            val viewModel = viewModelFor(item)

            assertEquals(Stage.SAVING, viewModel.uiState.value.overview!!.stage)
            assertEquals(BuyPath.EARLY, viewModel.uiState.value.overview!!.buyPath)
            assertEquals(Money(120_000, "EUR"), viewModel.uiState.value.overview!!.savings!!.toGo)

            pool.setAside(item.id, Money(120_000, "EUR"), start)

            assertEquals(Stage.READY, viewModel.uiState.value.overview!!.stage)
            assertEquals(BuyPath.BUY, viewModel.uiState.value.overview!!.buyPath)
        }

    @Test
    fun `buying for more than is set aside and not set aside asks first`() = runTest(UnconfinedTestDispatcher()) {
        // 1.500 € in the fun money, the camera's 1.200 € set aside: 300 € not set aside.
        setUpFunMoney(starting = 150_000)
        val item = add()
        pool.setAside(item.id, Money(120_000, "EUR"), start)
        val viewModel = viewModelFor(item)

        viewModel.onBuy()
        viewModel.onPriceChange("1600")
        viewModel.confirm()

        assertEquals(
            BelowZero(paid = Money(160_000, "EUR"), leaves = Money(-10_000, "EUR")),
            viewModel.uiState.value.confirmBelowZero
        )
        assertEquals(emptyList<Any>(), items.decisions)

        viewModel.dismissBelowZero()
        assertNull(viewModel.uiState.value.confirmBelowZero)
        assertEquals(emptyList<Any>(), items.decisions)

        viewModel.outcome.test {
            viewModel.confirm()
            viewModel.confirmBelowZero()
            assertEquals(DecisionOutcome.Bought, awaitItem())
        }
        assertEquals(Money(160_000, "EUR"), items.decisions.single().actualPrice)
    }

    @Test
    fun `buying within what is set aside and not set aside doesn't ask`() = runTest(UnconfinedTestDispatcher()) {
        setUpFunMoney(starting = 150_000)
        val item = add()
        pool.setAside(item.id, Money(120_000, "EUR"), start)
        val viewModel = viewModelFor(item)

        viewModel.outcome.test {
            viewModel.onBuy()
            viewModel.onPriceChange("1500")
            viewModel.confirm()
            assertEquals(DecisionOutcome.Bought, awaitItem())
        }
        assertNull(viewModel.uiState.value.confirmBelowZero)
    }

    @Test
    fun `buying a need never asks about the fun money`() = runTest(UnconfinedTestDispatcher()) {
        setUpFunMoney(starting = 0)
        val item = add(kind = ItemKind.NEED, reflected = false)
        val viewModel = viewModelFor(item)

        viewModel.outcome.test {
            viewModel.onBuy()
            viewModel.confirm()
            assertEquals(DecisionOutcome.Bought, awaitItem())
        }
        assertEquals(DecisionType.BUY_NEED, items.decisions.single().type)
    }
}
