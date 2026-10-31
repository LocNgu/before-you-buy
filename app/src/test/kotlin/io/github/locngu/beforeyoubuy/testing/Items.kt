package io.github.locngu.beforeyoubuy.testing

import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import java.time.Instant

/**
 * [this] as a priority in [slot] whose wait runs from [Item.addedAt] until [endsAt] (ADR-0049): what "cooling off
 * until" means now. A need is returned as it is: it doesn't wait and holds no slot.
 */
fun Item.asPriority(endsAt: Instant, slot: Int = 1): Item = if (kind == ItemKind.NEED) {
    this
} else {
    copy(prioritySlot = slot, waitingSince = minOf(addedAt, endsAt), coolingEndsAt = endsAt)
}
