package io.github.locngu.beforeyoubuy.core.data.notifications

import androidx.room.withTransaction
import io.github.locngu.beforeyoubuy.core.data.db.BeforeYouBuyDatabase
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.notifications.ItemNotifications
import io.github.locngu.beforeyoubuy.core.domain.notifications.NotificationLogRepository
import java.time.Instant

internal class RoomNotificationLogRepository(private val database: BeforeYouBuyDatabase) : NotificationLogRepository {
    private val notifications = database.notificationDao()

    override suspend fun notified(): Map<ItemId, ItemNotifications> = notifications.getAll().associate { row ->
        ItemId(row.itemId) to ItemNotifications(
            waitingPeriodOverAt = row.waitingPeriodOverAt?.let(Instant::ofEpochMilli),
            reflectionNudgedAt = row.reflectionNudgedAt?.let(Instant::ofEpochMilli)
        )
    }

    override suspend fun store(records: Map<ItemId, ItemNotifications>) = database.withTransaction {
        records.forEach { (id, record) ->
            notifications.setIfItemExists(
                itemId = id.value,
                waitingPeriodOverAt = record.waitingPeriodOverAt?.toEpochMilli(),
                reflectionNudgedAt = record.reflectionNudgedAt?.toEpochMilli()
            )
        }
    }
}
