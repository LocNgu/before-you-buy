package io.github.locngu.beforeyoubuy.core.domain.item

import io.github.locngu.beforeyoubuy.core.domain.money.Money
import java.time.Instant
import java.time.LocalDate

/** Identifies an [Item]; assigned by the database. */
@JvmInline
value class ItemId(val value: Long) {
    companion object {
        /** The id of an item that isn't stored yet. [ItemRepository.add] assigns the real one. */
        val NEW = ItemId(0)
    }
}

/** `NEED` skips cooling-off and reflection (brief §4.4, ADR-0008). */
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
 * The waiting period runs only while a want holds a priority slot (brief §4.2, ADR-0049). Change it only with the
 * functions in [io.github.locngu.beforeyoubuy.core.domain.coolingoff], which keep these fields consistent:
 *
 * @property coolingOffDays how many calendar days the want has to wait: the period for its price, set at capture. A
 *   price edit only extends it (ADR-0005); a tier change sets it again while the wait isn't complete (ADR-0031).
 * @property waitedDays whole calendar days waited in earlier stretches in a slot (ADR-0018); the running stretch isn't
 *   included. A wait that pauses keeps them; it never resets.
 * @property waitingSince when the running stretch started: the want got its slot. Null while the wait doesn't run.
 * @property coolingEndsAt when the running wait is complete; null exactly while [waitingSince] is.
 * @property recheckAt set by a `WAIT` decision; the item isn't due for a decision before it.
 * @property targetDate informational only.
 * @property prioritySlot 1..N while the item holds a priority slot (brief §4.7).
 * @property markedAsNeedAt when a want was changed to a `NEED`; null for wants and for items captured as needs.
 *   Change the kind with [io.github.locngu.beforeyoubuy.core.domain.coolingoff.changeKind].
 */
data class Item(
    val id: ItemId,
    val name: String,
    val price: Money,
    val addedAt: Instant,
    val coolingOffDays: Int,
    val waitedDays: Int = 0,
    val waitingSince: Instant? = null,
    val coolingEndsAt: Instant? = null,
    val kind: ItemKind = ItemKind.WANT,
    val status: ItemStatus = ItemStatus.ACTIVE,
    val link: String? = null,
    val imageRef: String? = null,
    val category: String? = null,
    val notes: String? = null,
    val targetDate: LocalDate? = null,
    val recheckAt: Instant? = null,
    val prioritySlot: Int? = null,
    val markedAsNeedAt: Instant? = null
) {
    init {
        require(price.minorUnits >= 0) { "Price can't be negative, was $price" }
        require(prioritySlot == null || prioritySlot >= 1) { "Priority slots start at 1, was $prioritySlot" }
        require(coolingOffDays >= 0 && waitedDays >= 0) { "Days can't be negative: $coolingOffDays, $waitedDays" }
        require((waitingSince == null) == (coolingEndsAt == null)) {
            "A running wait has a start and an end: $waitingSince, $coolingEndsAt"
        }
        require(waitingSince == null || coolingEndsAt!! >= waitingSince) { "The wait ends before it started" }
    }
}
