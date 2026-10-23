package io.github.locngu.beforeyoubuy.feature.wishlist

import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.item.Reflection
import io.github.locngu.beforeyoubuy.core.domain.item.UsageFrequency
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.Stage
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.testing.FakeItemImageStore
import io.github.locngu.beforeyoubuy.testing.FakeItemRepository
import io.github.locngu.beforeyoubuy.testing.FakePoolRepository
import io.github.locngu.beforeyoubuy.testing.MutableClock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private lateinit var viewModel: WishlistViewModel

    private val reflection =
        Reflection(why = "Hikes", usageFrequency = UsageFrequency.WEEKLY, reasonsAgainst = listOf("Heavy"))

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = WishlistViewModel(items, FakePoolRepository(items), images, clock)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun add(name: String, endsInHours: Long, price: Long = 12_000): Item {
        val id =
            items.add(
                Item(
                    id = ItemId.NEW,
                    name = name,
                    price = Money(price, "EUR"),
                    addedAt = start,
                    coolingEndsAt = start.plus(Duration.ofHours(endsInHours))
                )
            )
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
}
