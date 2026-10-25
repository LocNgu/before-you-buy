package io.github.locngu.beforeyoubuy.feature.priorities

import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.Reflection
import io.github.locngu.beforeyoubuy.core.domain.item.UsageFrequency
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.core.domain.pool.Allocation
import io.github.locngu.beforeyoubuy.core.domain.pool.AllowanceSettings
import io.github.locngu.beforeyoubuy.testing.FakeItemRepository
import io.github.locngu.beforeyoubuy.testing.FakePolicyRepository
import io.github.locngu.beforeyoubuy.testing.FakePoolRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HeadToHeadViewModelTest {
    private val now = Instant.parse("2026-10-24T10:00:00Z")
    private val items = FakeItemRepository()
    private val pool = FakePoolRepository(items)
    private val policies = FakePolicyRepository().also { it.items = items }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun add(name: String, kind: ItemKind = ItemKind.WANT): Item {
        val id = items.add(Item(ItemId.NEW, name, Money(50_000, "EUR"), now, now, kind))
        return items.stored.single { it.id == id }
    }

    /** Camera #1, Laptop #2, Bike #3, and the challenger 3D printer. */
    private suspend fun full(): List<Item> {
        val all = listOf(add("Camera"), add("Laptop"), add("Bike"), add("3D printer"))
        items.setPriorities(emptyList(), all.take(3).map { it.id })
        items.priorityWrites.clear()
        return all
    }

    private fun viewModelFor(item: Item) = HeadToHeadViewModel(item.id.value, items, pool, policies)

    private fun ranking() = items.stored.filter { it.prioritySlot != null }.sortedBy { it.prioritySlot }.map { it.name }

    /** Answers the comparison on screen: the challenger wins if [challengerWins]. */
    private fun HeadToHeadViewModel.answer(challengerWins: Boolean) {
        val comparison = uiState.value.comparison!!
        val winner = if (challengerWins) comparison.challenger.item.id else comparison.opponent.item.id
        choose(winner, comparison.opponent.item.id)
    }

    @Test
    fun `starts against the lowest-ranked priority, with reasons and savings on both cards`() = runTest {
        val (_, _, bike, printer) = full()
        items.reflect(printer.id, Reflection(why = "Spare parts", usageFrequency = UsageFrequency.WEEKLY))
        pool.allowance.value = AllowanceSettings(Money(30_000, "EUR"), 1, LocalDate.of(2026, 10, 1))
        pool.allocations.value = listOf(Allocation(bike.id, Money(20_000, "EUR"), now))

        val comparison = viewModelFor(printer).uiState.value.comparison!!

        assertEquals(printer.id, comparison.challenger.item.id)
        assertNull(comparison.challenger.rank)
        assertEquals("Spare parts", comparison.challenger.reflection?.why)
        assertEquals(bike.id, comparison.opponent.item.id)
        assertEquals(3, comparison.opponent.rank)
        assertEquals(Money(20_000, "EUR"), comparison.opponent.savings?.allocated)
    }

    @Test
    fun `winning some stores the new ranking once, at the end, and sums it up`() = runTest {
        val printer = full()[3]
        val viewModel = viewModelFor(printer)

        viewModel.answer(challengerWins = true)
        assertEquals(emptyList<List<ItemId>>(), items.priorityWrites)
        viewModel.answer(challengerWins = true)
        viewModel.answer(challengerWins = false)

        assertEquals(listOf("Camera", "3D printer", "Laptop"), ranking())
        assertEquals(1, items.priorityWrites.size)
        val summary = viewModel.uiState.value.summary!!
        assertEquals(
            listOf("Camera" to 1, "3D printer" to 2, "Laptop" to 3, "Bike" to null),
            summary.map { it.item.name to it.after }
        )
    }

    @Test
    fun `losing the first comparison stores nothing`() = runTest {
        val printer = full()[3]
        val viewModel = viewModelFor(printer)

        viewModel.answer(challengerWins = false)

        assertEquals(listOf("Camera", "Laptop", "Bike"), ranking())
        assertEquals(emptyList<List<ItemId>>(), items.priorityWrites)
        assertEquals(null, viewModel.uiState.value.summary!!.last().after)
    }

    @Test
    fun `a second tap meant for an answered comparison is ignored`() = runTest {
        val (_, _, bike, printer) = full()
        val viewModel = viewModelFor(printer)

        viewModel.choose(printer.id, bike.id)
        viewModel.choose(printer.id, bike.id)

        assertEquals("Laptop", viewModel.uiState.value.comparison!!.opponent.item.name)
    }

    @Test
    fun `priorities that changed while comparing store nothing and say so`() = runTest {
        val printer = full()[3]
        val viewModel = viewModelFor(printer)
        items.conflictOnNextPriorities = true

        viewModel.answer(challengerWins = true)
        viewModel.answer(challengerWins = false)

        assertTrue(viewModel.uiState.value.changedMeanwhile)
        assertEquals(listOf("Camera", "Laptop", "Bike"), ranking())
    }

    @Test
    fun `a slot freed meanwhile takes the challenger without a comparison`() = runTest {
        val camera = add("Camera")
        val printer = add("3D printer")
        items.setPriorities(emptyList(), listOf(camera.id))

        val state = viewModelFor(printer).uiState.value

        assertNull(state.comparison)
        assertEquals(listOf("3D printer" to 2), state.summary!!.map { it.item.name to it.after })
        assertEquals(listOf("Camera", "3D printer"), ranking())
    }

    @Test
    fun `a need or an item that's already a priority can't be compared`() = runTest {
        val camera = full()[0]
        val need = add("Fridge", ItemKind.NEED)

        assertTrue(viewModelFor(need).uiState.value.gone)
        assertTrue(viewModelFor(camera).uiState.value.gone)
    }
}
