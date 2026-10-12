package io.github.locngu.beforeyoubuy.core.domain.lifecycle

import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.Reflection
import io.github.locngu.beforeyoubuy.core.domain.item.UsageFrequency
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import java.time.Instant

/** Shared fixtures: a 120 € item added on [ADDED] that cools off until [COOLING_ENDS]. */
internal val ADDED: Instant = Instant.parse("2026-10-01T10:00:00Z")
internal val COOLING_ENDS: Instant = Instant.parse("2026-10-31T10:00:00Z")
internal val DURING_COOLING: Instant = Instant.parse("2026-10-15T10:00:00Z")
internal val AFTER_COOLING: Instant = Instant.parse("2026-11-02T10:00:00Z")

internal val PRICE = Money(12_000, "EUR")
internal val FUNDED = PRICE
internal val UNFUNDED = Money(11_999, "EUR")

internal fun item(kind: ItemKind = ItemKind.WANT) =
    Item(id = ItemId(1), name = "Camera", price = PRICE, addedAt = ADDED, coolingEndsAt = COOLING_ENDS, kind = kind)

internal val COMPLETE_REFLECTION = Reflection(
    why = "Better photos on hikes",
    usageFrequency = UsageFrequency.WEEKLY,
    reasonsAgainst = listOf("My phone takes decent photos")
)
