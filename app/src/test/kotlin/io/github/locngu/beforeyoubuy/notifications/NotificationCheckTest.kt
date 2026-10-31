package io.github.locngu.beforeyoubuy.notifications

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.core.domain.notifications.ItemNotifications
import io.github.locngu.beforeyoubuy.core.domain.notifications.NotificationKind
import io.github.locngu.beforeyoubuy.core.domain.settings.AppPreferences
import io.github.locngu.beforeyoubuy.core.domain.settings.NotificationPrefs
import io.github.locngu.beforeyoubuy.testing.asPriority
import io.github.locngu.beforeyoubuy.testing.FakeItemRepository
import io.github.locngu.beforeyoubuy.testing.FakeNotificationLogRepository
import io.github.locngu.beforeyoubuy.testing.FakePreferencesRepository
import io.github.locngu.beforeyoubuy.testing.MutableClock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The daily check with the real [Notifier]: what the user sees in the notification shade. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en")
class NotificationCheckTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val items = FakeItemRepository()
    private val log = FakeNotificationLogRepository(items)
    private val preferences = FakePreferencesRepository()
    private val clock = MutableClock(Instant.parse("2026-11-02T10:00:00Z"))
    private val check = NotificationCheck(items, log, preferences, Notifier(context), clock)
    private val manager = context.getSystemService(NotificationManager::class.java)

    @Before
    fun allowNotifications() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** Added 1 Oct, cooling off until [endsAt]. */
    private suspend fun add(name: String, endsAt: String = "2026-10-31T10:00:00Z", kind: ItemKind = ItemKind.WANT) =
        items.add(
            Item(
                id = ItemId.NEW,
                name = name,
                price = Money(12_000, "EUR"),
                addedAt = Instant.parse("2026-10-01T10:00:00Z"),
                coolingOffDays = 30,
                kind = kind
            ).asPriority(Instant.parse(endsAt))
        )

    /** What the shade shows, oldest first. */
    private fun shown(): List<StatusBarNotification> = manager.activeNotifications.sortedBy { it.postTime }

    private fun posted(): List<Notification> = shown().map { it.notification }

    private val Notification.title get() = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
    private val Notification.text get() = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

    @Test
    fun `one item that finished waiting shows its name, how long it was wanted, Still want it and Let it go`() =
        runTest {
            val camera = add("Camera")

            check.run()

            val notification = posted().single()
            assertEquals("Camera", notification.title)
            assertEquals("You’ve wanted Camera for 32 days. Your waiting period is over.", notification.text)
            assertEquals(Notifier.CHANNEL_WAITING_PERIODS, notification.channelId)
            assertEquals(listOf("Still want it", "Let it go"), notification.actions.map { it.title.toString() })
            // "Let it go" never opens the app; "Still want it" and a tap do.
            assertTrue(shadowOf(notification.actions[1].actionIntent).isBroadcastIntent)
            assertTrue(shadowOf(notification.actions[0].actionIntent).isActivityIntent)
            assertTrue(shadowOf(notification.contentIntent).isActivityIntent)
            assertEquals(
                NotificationTap(NotificationTarget.DECISION, camera, notification.id()),
                NotificationIntents.tapOf(shadowOf(notification.actions[0].actionIntent).savedIntent)
            )
            assertEquals(
                NotificationTap(NotificationTarget.ITEM, camera, notification.id()),
                NotificationIntents.tapOf(shadowOf(notification.contentIntent).savedIntent)
            )
            assertEquals(
                LetItGoRequest(camera, notification.id()),
                NotificationIntents.letItGoOf(shadowOf(notification.actions[1].actionIntent).savedIntent)
            )
            assertEquals(mapOf(camera to ItemNotifications(waitingPeriodOverAt = clock.instant())), log.records)
        }

    @Test
    @Config(qualifiers = "de")
    fun `the notification is in German`() = runTest {
        add("Kamera")

        check.run()

        val notification = posted().single()
        assertEquals("Du willst Kamera seit 32 Tagen. Deine Wartezeit ist vorbei.", notification.text)
        assertEquals(listOf("Will ich noch", "Loslassen"), notification.actions.map { it.title.toString() })
    }

    @Test
    fun `several items make one digest that opens the wishlist, without per-item actions`() = runTest {
        add("Camera")
        add("Lamp", endsAt = "2026-10-30T10:00:00Z")

        check.run()

        val notification = posted().single()
        assertEquals("2 items finished their waiting period", notification.title)
        assertEquals("Lamp, Camera", notification.text)
        assertNull(notification.actions)
        assertEquals(
            NotificationTarget.WISHLIST,
            NotificationIntents.tapOf(shadowOf(notification.contentIntent).savedIntent)?.target
        )
    }

    @Test
    fun `a second check the same day shows nothing more, the next day nothing for the same items`() = runTest {
        add("Camera")
        check.run()

        clock.advance(Duration.ofHours(6))
        assertNull(check.run())
        clock.advance(Duration.ofDays(1))
        assertNull(check.run())

        assertEquals(1, posted().size)
    }

    @Test
    fun `a reflection nudge goes to the quieter channel and opens the questions`() = runTest {
        val camera = add("Camera", endsAt = "2026-12-01T10:00:00Z")

        check.run()

        val notification = posted().single()
        assertEquals("Take a minute to note why you want Camera.", notification.text)
        assertEquals(Notifier.CHANNEL_REFLECTION, notification.channelId)
        assertNull(notification.actions)
        assertEquals(
            NotificationTap(NotificationTarget.REFLECTION, camera, notification.id()),
            NotificationIntents.tapOf(shadowOf(notification.contentIntent).savedIntent)
        )
        assertEquals(
            NotificationManagerCompat.IMPORTANCE_LOW,
            manager.getNotificationChannel(Notifier.CHANNEL_REFLECTION).importance
        )
    }

    @Test
    fun `several nudges make one digest`() = runTest {
        add("Camera", endsAt = "2026-12-01T10:00:00Z")
        add("Lamp", endsAt = "2026-12-01T10:00:00Z")

        check.run()

        val notification = posted().single()
        assertEquals("Take a minute for 2 items", notification.title)
        assertEquals("Note why you want Camera, Lamp.", notification.text)
    }

    @Test
    fun `needs get no notification`() = runTest {
        add("Fridge", kind = ItemKind.NEED)

        assertNull(check.run())
        assertEquals(emptyList<Notification>(), posted())
    }

    @Test
    fun `without the permission nothing is shown and nothing is recorded, so it comes once allowed`() = runTest {
        shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        add("Camera")

        assertNull(check.run())
        assertEquals(emptyMap<ItemId, ItemNotifications>(), log.records)

        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertEquals(NotificationKind.WAITING_PERIOD_OVER, check.run()?.kind)
    }

    @Test
    fun `a channel switched off in the system settings counts as off, the other kind still comes`() = runTest {
        Notifier(context).createChannels()
        manager.getNotificationChannel(Notifier.CHANNEL_WAITING_PERIODS).let {
            it.importance = NotificationManager.IMPORTANCE_NONE
            manager.createNotificationChannel(it)
        }
        add("Camera")
        add("Lamp", endsAt = "2026-12-01T10:00:00Z")

        assertEquals(NotificationKind.REFLECTION_NUDGE, check.run()?.kind)
    }

    @Test
    fun `a kind switched off in the app's preferences isn't sent`() = runTest {
        preferences.preferences.value = AppPreferences(notifications = NotificationPrefs(waitingPeriods = false))
        add("Camera")

        assertNull(check.run())
    }

    @Test
    fun `each day's notification has its own id, so it doesn't replace yesterday's`() = runTest {
        add("Camera")
        check.run()
        clock.advance(Duration.ofDays(1))
        add("Lamp", endsAt = "2026-11-02T12:00:00Z")

        check.run()

        assertEquals(2, shown().map { it.id }.distinct().size)
    }

    private fun Notification.id(): Int = shown().single { it.notification === this }.id
}
