package io.github.locngu.beforeyoubuy.core.domain.item

import io.github.locngu.beforeyoubuy.core.domain.money.Money
import java.time.Instant
import java.time.LocalDate

/** Identifies an [Item]; assigned by the database. */
@JvmInline
value class ItemId(val value: Long)

/** `NEED` skips cooling-off, reflection and funding (brief §4.4, ADR-0008, ADR-0031). */
enum class ItemKind { WANT, NEED }

/** The only stored state of an item's lifecycle. The displayed stage is derived (brief §5, ADR-0007). */
enum class ItemStatus {
    ACTIVE,
    PURCHASED,

    /** Shown as "Decided against" (ADR-0012). */
    REJECTED
}

/**
 * Something the user is considering buying (brief §4.1).
 *
 * There is deliberately no stage field: use [io.github.locngu.beforeyoubuy.core.domain.lifecycle.stageOf].
 * Reflection answers and decisions are stored next to the item, not in it.
 *
 * @property coolingEndsAt when cooling off ends; fixed at capture, price edits only extend it (brief §4.2).
 * @property recheckAt set by a `WAIT` decision; the item isn't due for a decision before it.
 * @property targetDate informational only.
 * @property prioritySlot 1..N while the item holds a priority slot (brief §4.7).
 */
data class Item(
    val id: ItemId,
    val name: String,
    val price: Money,
    val addedAt: Instant,
    val coolingEndsAt: Instant,
    val kind: ItemKind = ItemKind.WANT,
    val status: ItemStatus = ItemStatus.ACTIVE,
    val link: String? = null,
    val imageRef: String? = null,
    val category: String? = null,
    val notes: String? = null,
    val targetDate: LocalDate? = null,
    val recheckAt: Instant? = null,
    val prioritySlot: Int? = null
) {
    init {
        require(price.minorUnits >= 0) { "Price can't be negative, was $price" }
        require(prioritySlot == null || prioritySlot >= 1) { "Priority slots start at 1, was $prioritySlot" }
    }
}
