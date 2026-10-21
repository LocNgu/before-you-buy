package io.github.locngu.beforeyoubuy.core.domain.notifications

import io.github.locngu.beforeyoubuy.core.domain.item.ItemId

/**
 * Stores what was notified about each item ([ItemNotifications]), so [planNotifications] never sends the same
 * notification twice and keeps to one a day. Implemented in `:core:data`; a record goes when its item is deleted.
 */
interface NotificationLogRepository {
    /** Every item's record, by item id. Items never notified about have none. */
    suspend fun notified(): Map<ItemId, ItemNotifications>

    /**
     * Stores [records] (from [recordSent]) in one transaction, replacing those items' records. Items that no longer
     * exist are skipped.
     */
    suspend fun store(records: Map<ItemId, ItemNotifications>)
}
