package io.github.locngu.beforeyoubuy.core.data.notifications

import io.github.locngu.beforeyoubuy.core.data.db.DatabaseTest
import io.github.locngu.beforeyoubuy.core.data.item.DECIDED
import io.github.locngu.beforeyoubuy.core.data.item.RoomItemRepository
import io.github.locngu.beforeyoubuy.core.data.item.newItem
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.notifications.ItemNotifications
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class RoomNotificationLogRepositoryTest : DatabaseTest() {
    private lateinit var items: RoomItemRepository
    private lateinit var repository: RoomNotificationLogRepository

    private val nudged = Instant.parse("2026-10-03T10:00:00.123Z")

    @Before
    fun createRepositories() {
        items = RoomItemRepository(database)
        repository = RoomNotificationLogRepository(database)
    }

    @Test
    fun `nothing was notified on a fresh install`() = runTest {
        assertEquals(emptyMap<ItemId, ItemNotifications>(), repository.notified())
    }

    @Test
    fun `stores and reads back each item's record, replacing the previous one`() = runTest {
        val camera = items.add(newItem("Camera"))
        val lamp = items.add(newItem("Lamp"))

        repository.store(mapOf(camera to ItemNotifications(reflectionNudgedAt = nudged)))
        repository.store(
            mapOf(
                camera to ItemNotifications(waitingPeriodOverAt = DECIDED, reflectionNudgedAt = nudged),
                lamp to ItemNotifications(waitingPeriodOverAt = DECIDED)
            )
        )

        assertEquals(
            mapOf(
                camera to ItemNotifications(waitingPeriodOverAt = DECIDED, reflectionNudgedAt = nudged),
                lamp to ItemNotifications(waitingPeriodOverAt = DECIDED)
            ),
            repository.notified()
        )
    }

    @Test
    fun `a record for an item that doesn't exist is skipped, the others are stored`() = runTest {
        val camera = items.add(newItem("Camera"))

        repository.store(
            mapOf(
                ItemId(999) to ItemNotifications(waitingPeriodOverAt = DECIDED),
                camera to ItemNotifications(waitingPeriodOverAt = DECIDED)
            )
        )

        assertEquals(mapOf(camera to ItemNotifications(waitingPeriodOverAt = DECIDED)), repository.notified())
    }

    @Test
    fun `a record goes with its item`() = runTest {
        val camera = items.add(newItem("Camera"))
        repository.store(mapOf(camera to ItemNotifications(reflectionNudgedAt = nudged)))

        execSql("DELETE FROM items WHERE id = ${camera.value}")

        assertEquals(emptyMap<ItemId, ItemNotifications>(), repository.notified())
    }
}
