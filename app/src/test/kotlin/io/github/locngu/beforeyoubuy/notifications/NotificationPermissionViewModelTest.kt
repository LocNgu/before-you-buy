package io.github.locngu.beforeyoubuy.notifications

import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.core.domain.settings.AppPreferences
import io.github.locngu.beforeyoubuy.testing.FakeItemRepository
import io.github.locngu.beforeyoubuy.testing.FakePreferencesRepository
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationPermissionViewModelTest {
    private val items = FakeItemRepository()
    private val preferences = FakePreferencesRepository()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun add(): ItemId = items.add(
        Item(
            id = ItemId.NEW,
            name = "Camera",
            price = Money(12_000, "EUR"),
            addedAt = Instant.parse("2026-10-01T10:00:00Z"),
            coolingEndsAt = Instant.parse("2026-10-31T10:00:00Z")
        )
    )

    /** Keeps the state collected, as the screen does; unconfined, so it's current after every write. */
    private fun TestScope.subscribe(viewModel: NotificationPermissionViewModel) {
        viewModel.shouldAsk.launchIn(backgroundScope + UnconfinedTestDispatcher(testScheduler))
    }

    @Test
    fun `asks once the first item is there, not before`() = runTest {
        val viewModel = NotificationPermissionViewModel(items, preferences)
        subscribe(viewModel)

        assertFalse(viewModel.shouldAsk.value)
        add()
        assertTrue(viewModel.shouldAsk.value)
    }

    @Test
    fun `an answer, either way, stops it for good`() = runTest {
        add()
        val viewModel = NotificationPermissionViewModel(items, preferences)
        subscribe(viewModel)

        viewModel.answered()

        assertFalse(viewModel.shouldAsk.value)
        assertTrue(preferences.preferences.value.notificationPermissionAsked)
        add()
        assertFalse(viewModel.shouldAsk.value)
    }

    @Test
    fun `never asks a user who already answered, even with items`() = runTest {
        preferences.preferences.value = AppPreferences(notificationPermissionAsked = true)
        add()
        val viewModel = NotificationPermissionViewModel(items, preferences)
        subscribe(viewModel)

        assertFalse(viewModel.shouldAsk.value)
    }

    @Test
    fun `items that ended don't count, there's nothing waiting to be notified about`() = runTest {
        val id = add()
        items.replace(items.stored.single { it.id == id }.copy(status = ItemStatus.REJECTED))
        val viewModel = NotificationPermissionViewModel(items, preferences)
        subscribe(viewModel)

        assertFalse(viewModel.shouldAsk.value)
    }
}
