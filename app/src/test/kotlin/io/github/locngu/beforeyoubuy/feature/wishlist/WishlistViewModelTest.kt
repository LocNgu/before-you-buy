package io.github.locngu.beforeyoubuy.feature.wishlist

import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
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
class WishlistViewModelTest {
    private val start = Instant.parse("2026-10-16T10:00:00Z")
    private val clock = MutableClock(start)
    private val items = FakeItemRepository()
    private val images = FakeItemImageStore()
    private val policies = FakePolicyRepository().also { it.items = items }
    private lateinit var viewModel: WishlistViewModel

    private val reflection =
        Reflection(why = "Hikes", usageFrequency = UsageFrequency.WEEKLY, reasonsAgainst = listOf("Heavy"))

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = WishlistViewModel(items, FakePoolRepository(items), policies, images, clock)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** A priority whose wait ends [endsInHours] from now, or a want without a slot if [parked] (ADR-0049). */
    private suspend fun add(name: String, endsInHours: Long, price: Long = 12_000, parked: Boolean = false): Item {
        val captured = Item(
            id = ItemId.NEW,
            name = name,
            price = Money(price, "EUR"),
            addedAt = start,
            coolingOffDays = 30
        )
        val id = items.add(if (parked) captured else captured.asPriority(start.plus(Duration.ofHours(endsInHours))))
        return items.stored.first { it.id == id }
    }

    private val groups get() = viewModel.uiState.value.groups

    /** Moves the clock and the coroutine time together, as real time does. */
    private fun kotlinx.coroutines.test.TestScope.pass(duration: Duration) {
        clock.advance(duration)
        advanceTimeBy(duration.toMillis() + 1)
    }

    @Test
    fun `has no groups until the first load`() = runTest(UnconfinedTestDispatcher()) {
        assertNull(groups)

        viewModel.uiState.launchIn(backgroundScope)

        assertEquals(emptyList<Stage>(), groups!!.map { it.stage })
    }

    @Test
    fun `lists items by the stage stageOf gives them`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.uiState.launchIn(backgroundScope)
        val ready = add("Ready", endsInHours = -48)
        val unreflected = add("Unreflected", endsInHours = -24)
        add("Cooling", endsInHours = 24 * 5)
        items.reflect(ready.id, reflection)

        assertEquals(listOf(Stage.READY, Stage.NEEDS_REFLECTION, Stage.COOLING_OFF), groups!!.map { it.stage })
        assertEquals(unreflected.id, groups!![1].entries.single().item.id)
        assertEquals(5, groups!![2].entries.single().daysLeft)
    }

    @Test
    fun `an item moves up when its reflection is completed`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.uiState.launchIn(backgroundScope)
        val item = add("Camera", endsInHours = -1)
        assertEquals(Stage.NEEDS_REFLECTION, groups!!.single().stage)

        items.reflect(item.id, reflection)

        assertEquals(Stage.READY, groups!!.single().stage)
    }

    @Test
    fun `an item regroups when its cooling off ends while the screen is open`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.uiState.launchIn(backgroundScope)
        add("Camera", endsInHours = 3)
        assertEquals(Stage.COOLING_OFF, groups!!.single().stage)

        pass(Duration.ofHours(2))
        assertEquals(Stage.COOLING_OFF, groups!!.single().stage)

        pass(Duration.ofHours(1))
        assertEquals(Stage.NEEDS_REFLECTION, groups!!.single().stage)
    }

    @Test
    fun `the days left drop at midnight with no change in the database`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.uiState.launchIn(backgroundScope)
        add("Camera", endsInHours = 24 * 3)
        assertEquals(3, groups!!.single().entries.single().daysLeft)

        pass(Duration.ofHours(14)) // midnight

        assertEquals(2, groups!!.single().entries.single().daysLeft)
    }

    @Test
    fun `items with a photo get a thumbnail`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.uiState.launchIn(backgroundScope)
        val withPhoto = add("Camera", endsInHours = 24)
        add("Book", endsInHours = 24)

        items.replace(withPhoto.copy(imageRef = "camera.jpg"))

        assertEquals(mapOf(withPhoto.id to images.fileFor("camera.jpg")), viewModel.uiState.value.photos)
    }

    @Test
    fun `items that are no longer active disappear`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.uiState.launchIn(backgroundScope)
        val item = add("Camera", endsInHours = 24)

        items.replace(item.copy(status = ItemStatus.REJECTED))

        assertEquals(emptyList<Stage>(), groups!!.map { it.stage })
    }

    @Test
    fun `priorities come first by rank, out of their stage group, with the slot count`() =
        runTest(UnconfinedTestDispatcher()) {
            val camera = add("Camera", endsInHours = 10, parked = true)
            val bike = add("Bike", endsInHours = 20, parked = true)
            add("Lamp", endsInHours = 30, parked = true)
            items.setPriorities(emptyList(), listOf(bike.id, camera.id), clock.instant(), clock.zone)
            viewModel.uiState.launchIn(backgroundScope)

            val state = viewModel.uiState.value
            assertEquals(listOf(1 to "Bike", 2 to "Camera"), state.priorities.map { it.rank to it.entry.item.name })
            assertEquals(listOf(listOf("Lamp")), state.groups!!.map { group -> group.entries.map { it.item.name } })
            assertEquals(3, state.prioritySlots)
        }

    @Test
    fun `make priority with a free slot stores it at once and says so`() = runTest(UnconfinedTestDispatcher()) {
        val camera = add("Camera", endsInHours = 10, parked = true)

        viewModel.makePriority(camera.id)

        assertEquals(PriorityEvent.Promoted("Camera", 1), viewModel.priorityEvents.first())
        assertEquals(1, items.stored.single().prioritySlot)
    }

    @Test
    fun `make priority with full slots asks for the head-to-head and writes nothing`() =
        runTest(UnconfinedTestDispatcher()) {
            val camera = add("Camera", endsInHours = 10, parked = true)
            val bike = add("Bike", endsInHours = 20, parked = true)
            policies.changePrioritySlots(1, emptyList(), clock.instant(), clock.zone)
            items.setPriorities(emptyList(), listOf(camera.id), clock.instant(), clock.zone)

            viewModel.makePriority(bike.id)

            assertEquals(PriorityEvent.Compare(bike.id), viewModel.priorityEvents.first())
            assertEquals(listOf(listOf(camera.id)), items.priorityWrites)
        }

    @Test
    fun `fewer slots move the lowest-ranked back to the wishlist`() = runTest(UnconfinedTestDispatcher()) {
        val camera = add("Camera", endsInHours = 10, parked = true)
        val bike = add("Bike", endsInHours = 20, parked = true)
        items.setPriorities(emptyList(), listOf(camera.id, bike.id), clock.instant(), clock.zone)
        viewModel.uiState.launchIn(backgroundScope)

        viewModel.changePrioritySlots(1)

        assertEquals(listOf(1), policies.slotChanges)
        assertEquals(listOf("Camera"), viewModel.uiState.value.priorities.map { it.entry.item.name })
        assertEquals(1, viewModel.uiState.value.prioritySlots)
    }

    @Test
    fun `fewer slots based on priorities that changed meanwhile write nothing and say so`() =
        runTest(UnconfinedTestDispatcher()) {
            val camera = add("Camera", endsInHours = 10, parked = true)
            val bike = add("Bike", endsInHours = 20, parked = true)
            items.setPriorities(emptyList(), listOf(camera.id, bike.id), clock.instant(), clock.zone)
            // The state isn't collected, so what the dialog listed from (no priorities) is behind the stored ones.

            viewModel.changePrioritySlots(1)

            assertEquals(PriorityEvent.ChangedMeanwhile, viewModel.priorityEvents.first())
            assertEquals(emptyList<Int>(), policies.slotChanges)
        }
}
