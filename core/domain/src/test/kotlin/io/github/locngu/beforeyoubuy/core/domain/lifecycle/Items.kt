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

/** The price is set aside, and wants need it (ADR-0046). */
internal val FUNDED = Funding(PRICE, required = true)

/** One cent short, and wants need the price set aside. */
internal val UNFUNDED = Funding(Money(11_999, "EUR"), required = true)

/** Money condition off: without fun money, or with its switch off (ADR-0046). */
internal fun notRequired(price: Money = PRICE) = Funding(Money(0, price.currency), required = false)

internal fun item(kind: ItemKind = ItemKind.WANT) =
    Item(id = ItemId(1), name = "Camera", price = PRICE, addedAt = ADDED, coolingEndsAt = COOLING_ENDS, kind = kind)

internal val COMPLETE_REFLECTION = Reflection(
    why = "Better photos on hikes",
    usageFrequency = UsageFrequency.WEEKLY,
    reasonsAgainst = listOf("My phone takes decent photos")
)
