package io.github.locngu.beforeyoubuy.notifications

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.locngu.beforeyoubuy.R
import io.github.locngu.beforeyoubuy.core.domain.item.Decision
import io.github.locngu.beforeyoubuy.core.domain.item.DecisionSource
import io.github.locngu.beforeyoubuy.core.domain.item.DecisionType
import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.testing.FakeItemRepository
import io.github.locngu.beforeyoubuy.testing.MutableClock
import io.github.locngu.beforeyoubuy.testing.asPriority
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class LetItGoTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val items = FakeItemRepository()
    private val now = Instant.parse("2026-11-02T10:00:00Z")
    private val letItGo = LetItGo(items, MutableClock(now))

    private suspend fun add(): ItemId = items.add(
        Item(
            id = ItemId.NEW,
            name = "Camera",
            price = Money(12_000, "EUR"),
            addedAt = Instant.parse("2026-10-01T10:00:00Z"),
            coolingOffDays = 30
        ).asPriority(Instant.parse("2026-10-31T10:00:00Z"))
    )

    @Test
    fun `records a reject from the notification`() = runTest {
        val id = add()

        assertTrue(letItGo.run(id))

        assertEquals(listOf(Decision(DecisionType.REJECT, now, DecisionSource.NOTIFICATION)), items.decisions)
        assertEquals(ItemStatus.REJECTED, items.stored.single().status)
    }

    @Test
    fun `works whatever the stage, like Reject in the app`() = runTest {
        val id = add()
        items.replace(items.stored.single().copy(coolingEndsAt = Instant.parse("2026-12-01T10:00:00Z")))

        assertTrue(letItGo.run(id))
    }

    @Test
    fun `an item that ended meanwhile stays as it is`() = runTest {
        val id = add()
        items.replace(items.stored.single().copy(status = ItemStatus.PURCHASED))

        assertFalse(letItGo.run(id))
        assertEquals(emptyList<Decision>(), items.decisions)
    }

    @Test
    fun `a deleted item writes nothing`() = runTest {
        assertFalse(letItGo.run(ItemId(99)))
        assertEquals(emptyList<Decision>(), items.decisions)
    }

    @Test
    fun `an edit that lands in between is read again and the reject still applies`() = runTest {
        val id = add()
        items.conflictOnNextDecision = true

        assertTrue(letItGo.run(id))
        assertEquals(1, items.decisions.size)
    }

    @Test
    fun `the receiver records it without opening the app and removes the notification`() = runTest {
        val id = add()
        val manager = context.getSystemService(NotificationManager::class.java)
        Notifier(context).createChannels()
        val notification = NotificationCompat.Builder(context, Notifier.CHANNEL_WAITING_PERIODS)
            .setSmallIcon(R.drawable.ic_notification)
            .build()
        manager.notify(7, notification)

        LetItGoReceiver().handle(context, LetItGoRequest(id, notificationId = 7), letItGo)

        assertEquals(ItemStatus.REJECTED, items.stored.single().status)
        assertEquals(0, manager.activeNotifications.size)
        assertNull(shadowOf(context as Application).nextStartedActivity)
    }

    @Test
    fun `the notification goes even if the item had ended`() = runTest {
        val manager = context.getSystemService(NotificationManager::class.java)
        Notifier(context).createChannels()
        manager.notify(
            7,
            NotificationCompat.Builder(
                context,
                Notifier.CHANNEL_WAITING_PERIODS
            ).setSmallIcon(R.drawable.ic_notification).build()
        )

        LetItGoReceiver().handle(context, LetItGoRequest(ItemId(99), notificationId = 7), letItGo)

        assertEquals(0, manager.activeNotifications.size)
    }
}
