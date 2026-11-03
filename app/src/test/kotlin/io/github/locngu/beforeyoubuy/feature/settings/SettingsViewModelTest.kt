package io.github.locngu.beforeyoubuy.feature.settings

import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.core.domain.settings.AppPreferences
import io.github.locngu.beforeyoubuy.core.domain.settings.NotificationNotice
import io.github.locngu.beforeyoubuy.core.domain.settings.NotificationPrefs
import io.github.locngu.beforeyoubuy.core.domain.settings.SystemNotifications
import io.github.locngu.beforeyoubuy.feature.priorities.PriorityEvent
import io.github.locngu.beforeyoubuy.testing.FakeItemRepository
import io.github.locngu.beforeyoubuy.testing.FakePolicyRepository
import io.github.locngu.beforeyoubuy.testing.FakePreferencesRepository
import io.github.locngu.beforeyoubuy.testing.MutableClock
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val start = Instant.parse("2026-11-03T10:00:00Z")
    private val clock = MutableClock(start)
    private val items = FakeItemRepository()
    private val policies = FakePolicyRepository().also { it.items = items }
    private val preferences = FakePreferencesRepository()
    private var system = SystemNotifications()
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = SettingsViewModel(preferences, policies, items, { system }, clock)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val state get() = viewModel.uiState.value!!

    private suspend fun add(name: String): Item {
        val id = items.add(
            Item(id = ItemId.NEW, name = name, price = Money(12_000, "EUR"), addedAt = start, coolingOffDays = 30)
        )
        return items.stored.first { it.id == id }
    }

    @Test
    fun `nothing is shown until loaded, then the stored switches and slots`() = runTest(UnconfinedTestDispatcher()) {
        assertNull(viewModel.uiState.value)
        preferences.preferences.value =
            AppPreferences(notifications = NotificationPrefs(waitingPeriods = false, reflectionReminders = true))
        viewModel.uiState.launchIn(backgroundScope)

        assertEquals(NotificationPrefs(waitingPeriods = false, reflectionReminders = true), state.notifications)
        assertEquals(3, state.prioritySlots)
        assertEquals(0, state.priorityCount)
        assertEquals(NotificationNotice.None, state.notice)
    }

    @Test
    fun `the switches are stored in the preferences`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.uiState.launchIn(backgroundScope)

        viewModel.setWaitingPeriods(false)
        assertEquals(
            NotificationPrefs(waitingPeriods = false, reflectionReminders = true),
            preferences.preferences.value.notifications
        )

        viewModel.setReflectionReminders(false)
        viewModel.setWaitingPeriods(true)
        assertEquals(
            NotificationPrefs(waitingPeriods = true, reflectionReminders = false),
            preferences.preferences.value.notifications
        )
        assertEquals(preferences.preferences.value.notifications, state.notifications)
    }

    @Test
    fun `a switch changes the stored preferences, not an older shown copy`() = runTest(UnconfinedTestDispatcher()) {
        // Not collected: what the screen last showed is behind what's stored.
        preferences.setNotificationPrefs(NotificationPrefs(waitingPeriods = true, reflectionReminders = false))

        viewModel.setWaitingPeriods(false)

        assertEquals(
            NotificationPrefs(waitingPeriods = false, reflectionReminders = false),
            preferences.preferences.value.notifications
        )
    }

    @Test
    fun `what the phone blocks is read again on refresh`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.uiState.launchIn(backgroundScope)
        assertEquals(NotificationNotice.None, state.notice)

        system = SystemNotifications(appAllowed = false)
        // Nothing reads the system by itself; the screen refreshes when it comes back to the front.
        assertEquals(NotificationNotice.None, state.notice)
        viewModel.refreshSystemNotifications()
        assertEquals(NotificationNotice.AppBlocked, state.notice)

        system = SystemNotifications(reflectionRemindersChannelOn = false)
        viewModel.refreshSystemNotifications()
        assertEquals(NotificationNotice.ChannelsOff(waitingPeriods = false, reflectionReminders = true), state.notice)

        // Switched off in the app too: nothing left to say.
        viewModel.setReflectionReminders(false)
        assertEquals(NotificationNotice.None, state.notice)
    }

    @Test
    fun `fewer slots go through changePrioritySlots and move the lowest-ranked back`() =
        runTest(UnconfinedTestDispatcher()) {
            val camera = add("Camera")
            val bike = add("Bike")
            items.setPriorities(emptyList(), listOf(camera.id, bike.id), clock.instant(), clock.zone)
            viewModel.uiState.launchIn(backgroundScope)
            assertEquals(2, state.priorityCount)

            viewModel.changePrioritySlots(1)

            assertEquals(listOf(1), policies.slotChanges)
            assertEquals(1, state.prioritySlots)
            assertEquals(1, state.priorityCount)
            assertEquals(1, items.stored.first { it.id == camera.id }.prioritySlot)
            assertNull(items.stored.first { it.id == bike.id }.prioritySlot)
        }

    @Test
    fun `fewer slots based on priorities that changed meanwhile write nothing and say so`() =
        runTest(UnconfinedTestDispatcher()) {
            val camera = add("Camera")
            val bike = add("Bike")
            items.setPriorities(emptyList(), listOf(camera.id, bike.id), clock.instant(), clock.zone)
            // The state isn't collected, so what the dialog listed from (nothing) is behind the stored priorities.

            viewModel.changePrioritySlots(1)

            assertEquals(PriorityEvent.ChangedMeanwhile, viewModel.priorityEvents.first())
            assertEquals(emptyList<Int>(), policies.slotChanges)
        }
}
