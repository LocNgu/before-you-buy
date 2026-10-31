package io.github.locngu.beforeyoubuy.core.domain.lifecycle

import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.Reflection
import io.github.locngu.beforeyoubuy.core.domain.item.UsageFrequency
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import java.time.Instant

/**
 * Shared fixtures: a 120 € want added on [ADDED] that has held priority slot 1 since then, so its 30-day wait runs
 * until [COOLING_ENDS] (ADR-0049). [parked] is the same want without a slot.
 */
internal val ADDED: Instant = Instant.parse("2026-10-01T10:00:00Z")
internal val COOLING_ENDS: Instant = Instant.parse("2026-10-31T10:00:00Z")
internal val DURING_COOLING: Instant = Instant.parse("2026-10-15T10:00:00Z")
internal val AFTER_COOLING: Instant = Instant.parse("2026-11-02T10:00:00Z")

internal val PRICE = Money(12_000, "EUR")

/** The price is set aside, and wants need it (ADR-0046). */
internal val FUNDED = Funding(PRICE, required = true)

/** 119,49 € (shown as 119 €) of 120 €, and wants need the price set aside. */
internal val UNFUNDED = Funding(Money(11_949, "EUR"), required = true)

/** Money condition off: without fun money, or with its switch off (ADR-0046). */
internal fun notRequired(price: Money = PRICE) = Funding(Money(0, price.currency), required = false)

/** Days the fixture waits: [ADDED] to [COOLING_ENDS]. */
internal const val COOLING_OFF_DAYS = 30

/** The priority fixture, or a need, which holds no slot and doesn't wait (ADR-0008, ADR-0047). */
internal fun item(kind: ItemKind = ItemKind.WANT): Item {
    val want = kind == ItemKind.WANT
    return Item(
        id = ItemId(1),
        name = "Camera",
        price = PRICE,
        addedAt = ADDED,
        coolingOffDays = COOLING_OFF_DAYS,
        waitingSince = ADDED.takeIf { want },
        coolingEndsAt = COOLING_ENDS.takeIf { want },
        kind = kind,
        prioritySlot = 1.takeIf { want }
    )
}

/** The want parked on the wishlist: no slot, its wait not running, [waitedDays] waited so far. */
internal fun parked(waitedDays: Int = 0) =
    item().copy(prioritySlot = null, waitingSince = null, coolingEndsAt = null, waitedDays = waitedDays)

internal val COMPLETE_REFLECTION = Reflection(
    why = "Better photos on hikes",
    usageFrequency = UsageFrequency.WEEKLY,
    reasonsAgainst = listOf("My phone takes decent photos")
)
