package io.github.locngu.beforeyoubuy.feature.wishlist

import app.cash.turbine.test
import io.github.locngu.beforeyoubuy.core.domain.decision.OpportunityCostLine
import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.item.Reflection
import io.github.locngu.beforeyoubuy.core.domain.item.UsageFrequency
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.Stage
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.feature.priorities.PriorityEvent
import io.github.locngu.beforeyoubuy.testing.asPriority
import io.github.locngu.beforeyoubuy.testing.FakeItemImageStore
import io.github.locngu.beforeyoubuy.testing.FakeItemRepository
import io.github.locngu.beforeyoubuy.testing.FakePolicyRepository
import io.github.locngu.beforeyoubuy.testing.FakePoolRepository
import io.github.locngu.beforeyoubuy.testing.MutableClock
import java.time.Duration
import java.time.Instant
import java.util.Locale
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
class ItemDetailViewModelTest {
    private val start = Instant.parse("2026-10-16T10:00:00Z")
    private val clock = MutableClock(start)
    private val items = FakeItemRepository()
    private val policies = FakePolicyRepository()
    private val pool = FakePoolRepository(items)
    private val images = FakeItemImageStore()

    private val reflection =
        Reflection(why = "Hikes", usageFrequency = UsageFrequency.WEEKLY, reasonsAgainst = listOf("Heavy"))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** A priority whose wait ends [endsInHours] from now, or a want without a slot if [parked] (ADR-0049). */
    private suspend fun add(endsInHours: Long, parked: Boolean = false): Item {
        val captured = Item(
            id = ItemId.NEW,
            name = "Camera",
            price = Money(12_000, "EUR"),
            addedAt = start.minus(Duration.ofDays(3)),
            coolingOffDays = 30
        )
        val id = items.add(if (parked) captured else captured.asPriority(start.plus(Duration.ofHours(endsInHours))))
        return items.stored.single { it.id == id }
    }

    private fun viewModelFor(item: Item) = ItemDetailViewModel(item.id.value, items, pool, policies, images, clock)

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
    fun `shows the item's photo, once it has one`() = runTest(UnconfinedTestDispatcher()) {
        val item = add(endsInHours = 24)
        val viewModel = viewModelFor(item)
        viewModel.uiState.launchIn(backgroundScope)
        assertNull(viewModel.uiState.value.photo)

        items.replace(item.copy(imageRef = "camera.jpg"))

        assertEquals(images.fileFor("camera.jpg"), viewModel.uiState.value.photo)
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

    private suspend fun setUpFunMoney(starting: Long, wantsNeedFunding: Boolean = true) {
        pool.setUp(Money(30_000, "EUR"), 1, Money(starting, "EUR"), wantsNeedFunding, start, clock.zone)
    }

    @Test
    fun `shows the first two lines of what else this money could do`() = runTest(UnconfinedTestDispatcher()) {
        // 300 € a month, nothing yet; the camera costs 120 €, the 290 € lamp is priority #1 and two small items fit:
        // four lines, detail shows the first two. Buying the camera pushes the lamp from one month to two.
        setUpFunMoney(starting = 0)
        val camera = add(endsInHours = 24)
        val lampId =
            items.add(camera.copy(id = ItemId.NEW, name = "Lamp", price = Money(29_000, "EUR"), prioritySlot = 1))
        items.add(camera.copy(id = ItemId.NEW, name = "Socks", price = Money(1_000, "EUR")))
        items.add(camera.copy(id = ItemId.NEW, name = "Book", price = Money(2_000, "EUR")))
        val lamp = items.stored.single { it.id == lampId }
        val viewModel = viewModelFor(camera)
        viewModel.uiState.launchIn(backgroundScope)

        assertEquals(
            listOf(OpportunityCostLine.DelaysPriority(lamp, 1, 1), OpportunityCostLine.ShareOfGoal(lamp, 1, 41)),
            viewModel.uiState.value.opportunityCost
        )
    }

    /** A want whose wait is over and whose answers are given: only money can hold it back. */
    private suspend fun readyItem(): Item = add(endsInHours = -1).also { items.reflect(it.id, reflection) }

    @Test
    fun `setting aside moves money to the item, and it's Ready once its price is set aside`() =
        runTest(UnconfinedTestDispatcher()) {
            setUpFunMoney(starting = 20_000)
            val item = readyItem()
            val viewModel = viewModelFor(item).apply { setLocale(Locale.GERMANY) }
            viewModel.uiState.launchIn(backgroundScope)
            assertEquals(Stage.SAVING, viewModel.uiState.value.detail!!.stage)

            viewModel.openSetAside()
            // Limited by what isn't set aside, not by the camera's 120 € (ADR-0046).
            assertEquals(Money(20_000, "EUR"), viewModel.uiState.value.sheet!!.limit)
            viewModel.onSavingsAmountChange("60")
            viewModel.confirmSavings()

            assertNull(viewModel.uiState.value.sheet)
            assertEquals(Money(6_000, "EUR"), viewModel.uiState.value.detail!!.savings!!.allocated)
            assertEquals(Stage.SAVING, viewModel.uiState.value.detail!!.stage)

            viewModel.openSetAside()
            // "The rest": what's still to go, here less than what isn't set aside.
            assertEquals(Money(6_000, "EUR"), viewModel.uiState.value.sheet!!.rest)
            viewModel.onSavingsChip(viewModel.uiState.value.sheet!!.rest!!)
            viewModel.confirmSavings()

            assertEquals(Stage.READY, viewModel.uiState.value.detail!!.stage)
            assertTrue(viewModel.uiState.value.detail!!.savings!!.complete)
        }

    @Test
    fun `more than isn't set aside yet shows why and writes nothing`() = runTest(UnconfinedTestDispatcher()) {
        setUpFunMoney(starting = 5_000)
        val item = readyItem()
        val viewModel = viewModelFor(item).apply { setLocale(Locale.GERMANY) }
        viewModel.uiState.launchIn(backgroundScope)

        viewModel.openSetAside()
        viewModel.onSavingsAmountChange("51")
        val sheet = viewModel.uiState.value.sheet!!
        assertEquals(SavingsError.MoreThanLimit, sheet.error)
        assertFalse(sheet.canConfirm)
        // The rest is capped by what isn't set aside.
        assertEquals(Money(5_000, "EUR"), sheet.rest)
        viewModel.confirmSavings()

        assertEquals(emptyList<Any>(), pool.allocations.value)
        assertTrue(viewModel.uiState.value.sheet != null)
    }

    @Test
    fun `taking back is limited by what is set aside`() = runTest(UnconfinedTestDispatcher()) {
        setUpFunMoney(starting = 20_000)
        val item = readyItem()
        pool.setAside(item.id, Money(6_000, "EUR"), start)
        val viewModel = viewModelFor(item).apply { setLocale(Locale.GERMANY) }
        viewModel.uiState.launchIn(backgroundScope)

        viewModel.openTakeBack()
        assertEquals(Money(6_000, "EUR"), viewModel.uiState.value.sheet!!.limit)
        assertEquals(Money(6_000, "EUR"), viewModel.uiState.value.sheet!!.rest)
        viewModel.onSavingsAmountChange("61")
        assertEquals(SavingsError.MoreThanLimit, viewModel.uiState.value.sheet!!.error)
        viewModel.onSavingsAmountChange("10")
        viewModel.confirmSavings()

        assertEquals(Money(5_000, "EUR"), viewModel.uiState.value.detail!!.savings!!.allocated)
    }

    @Test
    fun `an empty or zero amount can't be confirmed, and an empty one shows no error`() =
        runTest(UnconfinedTestDispatcher()) {
            setUpFunMoney(starting = 20_000)
            val viewModel = viewModelFor(readyItem()).apply { setLocale(Locale.GERMANY) }
            viewModel.uiState.launchIn(backgroundScope)

            viewModel.openSetAside()
            assertNull(viewModel.uiState.value.sheet!!.error)
            assertFalse(viewModel.uiState.value.sheet!!.canConfirm)
            viewModel.onSavingsAmountChange("0")
            assertEquals(SavingsError.Zero, viewModel.uiState.value.sheet!!.error)
            viewModel.onSavingsAmountChange("")
            assertNull(viewModel.uiState.value.sheet!!.error)

            viewModel.dismissSavings()
            assertNull(viewModel.uiState.value.sheet)
        }

    @Test
    fun `without fun money nothing can be set aside, and a want is Ready without savings`() =
        runTest(UnconfinedTestDispatcher()) {
            val viewModel = viewModelFor(readyItem()).apply { setLocale(Locale.GERMANY) }
            viewModel.uiState.launchIn(backgroundScope)

            val detail = viewModel.uiState.value.detail!!
            assertFalse(detail.funMoneySetUp)
            assertEquals(Stage.READY, detail.stage)
            viewModel.openSetAside()
            assertNull(viewModel.uiState.value.sheet)
        }

    @Test
    fun `with the switch off a want is Ready without savings, which still show`() =
        runTest(UnconfinedTestDispatcher()) {
            setUpFunMoney(starting = 20_000, wantsNeedFunding = false)
            val item = readyItem()
            pool.setAside(item.id, Money(3_000, "EUR"), start)
            val viewModel = viewModelFor(item).apply { setLocale(Locale.GERMANY) }
            viewModel.uiState.launchIn(backgroundScope)

            val detail = viewModel.uiState.value.detail!!
            assertEquals(Stage.READY, detail.stage)
            assertEquals(Money(3_000, "EUR"), detail.savings!!.allocated)
        }

    @Test
    fun `a need has no savings`() = runTest(UnconfinedTestDispatcher()) {
        setUpFunMoney(starting = 20_000)
        val item = add(endsInHours = 24)
        items.update(item, item.copy(kind = ItemKind.NEED, markedAsNeedAt = start))
        val viewModel = viewModelFor(item)
        viewModel.uiState.launchIn(backgroundScope)

        assertNull(viewModel.uiState.value.detail!!.savings)
        viewModel.openSetAside()
        assertNull(viewModel.uiState.value.sheet)
    }

    @Test
    fun `with nothing left to set aside, setting aside isn't offered`() = runTest(UnconfinedTestDispatcher()) {
        setUpFunMoney(starting = 6_000)
        val camera = readyItem()
        val bike = add(endsInHours = 24)
        pool.setAside(bike.id, Money(6_000, "EUR"), start)
        val viewModel = viewModelFor(camera).apply { setLocale(Locale.GERMANY) }
        viewModel.uiState.launchIn(backgroundScope)

        assertFalse(viewModel.uiState.value.canSetAside)
        viewModel.openSetAside()
        assertNull(viewModel.uiState.value.sheet)
    }

    @Test
    fun `once the price is set aside, more can still be set aside, without a rest`() =
        runTest(UnconfinedTestDispatcher()) {
            setUpFunMoney(starting = 20_000)
            val camera = readyItem()
            pool.setAside(camera.id, Money(12_000, "EUR"), start)
            val viewModel = viewModelFor(camera).apply { setLocale(Locale.GERMANY) }
            viewModel.uiState.launchIn(backgroundScope)

            assertEquals(Stage.READY, viewModel.uiState.value.detail!!.stage)
            assertTrue(viewModel.uiState.value.canSetAside)
            viewModel.openSetAside()
            assertNull(viewModel.uiState.value.sheet!!.rest)
            viewModel.onSavingsAmountChange("50")
            viewModel.confirmSavings()

            assertEquals(Money(17_000, "EUR"), viewModel.uiState.value.detail!!.savings!!.allocated)
        }

    @Test
    fun `make priority takes a free slot, remove from priorities gives it up`() = runTest(UnconfinedTestDispatcher()) {
        val item = add(endsInHours = 2, parked = true)
        val viewModel = viewModelFor(item)

        viewModel.makePriority()
        assertEquals(PriorityEvent.Promoted("Camera", 1), viewModel.priorityEvents.first())
        assertEquals(1, items.stored.single().prioritySlot)

        viewModel.removePriority()
        assertEquals(PriorityEvent.Removed("Camera"), viewModel.priorityEvents.first())
        assertNull(items.stored.single().prioritySlot)
    }

    @Test
    fun `make priority with full slots opens the head-to-head instead`() = runTest(UnconfinedTestDispatcher()) {
        val first = add(endsInHours = 2, parked = true)
        val second = add(endsInHours = 2, parked = true)
        items.prioritySlots = 1
        policies.changePrioritySlots(1, emptyList(), clock.instant(), clock.zone)
        items.setPriorities(emptyList(), listOf(first.id), clock.instant(), clock.zone)
        val viewModel = viewModelFor(second)

        viewModel.makePriority()

        assertEquals(PriorityEvent.Compare(second.id), viewModel.priorityEvents.first())
        assertNull(items.stored.single { it.id == second.id }.prioritySlot)
    }

    @Test
    fun `a double tap on make priority writes once and says so once`() = runTest(UnconfinedTestDispatcher()) {
        val item = add(endsInHours = 2, parked = true)
        val viewModel = viewModelFor(item)
        val gate = CompletableDeferred<Unit>()
        items.priorityGate = gate

        viewModel.priorityEvents.test {
            viewModel.makePriority()
            viewModel.makePriority()
            gate.complete(Unit)

            assertEquals(PriorityEvent.Promoted("Camera", 1), awaitItem())
            // Not a second "changed meanwhile" about its own write.
            expectNoEvents()
        }
        assertEquals(1, items.priorityWrites.size)
    }

    @Test
    fun `priorities that changed meanwhile write nothing and say so`() = runTest(UnconfinedTestDispatcher()) {
        val item = add(endsInHours = 2, parked = true)
        val viewModel = viewModelFor(item)
        items.conflictOnNextPriorities = true

        viewModel.makePriority()

        assertEquals(PriorityEvent.ChangedMeanwhile, viewModel.priorityEvents.first())
        assertNull(items.stored.single().prioritySlot)
    }

    @Test
    fun `marking a priority as a need takes it out of the priorities`() = runTest(UnconfinedTestDispatcher()) {
        val first = add(endsInHours = 2, parked = true)
        val second = add(endsInHours = 2, parked = true)
        items.setPriorities(emptyList(), listOf(first.id, second.id), clock.instant(), clock.zone)
        val viewModel = viewModelFor(items.stored.first())
        viewModel.uiState.launchIn(backgroundScope)

        viewModel.markAsNeed()

        assertEquals(listOf(null, 1), items.stored.map { it.prioritySlot })
    }
}
