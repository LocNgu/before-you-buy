package io.github.locngu.beforeyoubuy.core.data.item

import io.github.locngu.beforeyoubuy.core.domain.item.Decision
import io.github.locngu.beforeyoubuy.core.domain.item.DecisionSource
import io.github.locngu.beforeyoubuy.core.domain.item.DecisionType
import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.item.Reflection
import io.github.locngu.beforeyoubuy.core.domain.item.UpgradeOrNew
import io.github.locngu.beforeyoubuy.core.domain.item.UsageFrequency
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Millisecond-precision instants: what the database stores. */
internal val ADDED: Instant = Instant.parse("2026-10-01T10:00:00.123Z")
internal val COOLING_ENDS: Instant = Instant.parse("2026-10-31T10:00:00.456Z")
internal val DECIDED: Instant = Instant.parse("2026-11-02T09:30:00Z")

/** A want with only the required fields, not stored yet. */
internal fun newItem(name: String = "Camera", addedAt: Instant = ADDED) = Item(
    id = ItemId.NEW,
    name = name,
    price = Money(12_000, "EUR"),
    addedAt = addedAt,
    coolingEndsAt = COOLING_ENDS
)

/** Every optional field set. */
internal val FULL_ITEM = Item(
    id = ItemId(42),
    name = "Fridge",
    price = Money(89_900, "EUR"),
    addedAt = ADDED,
    coolingEndsAt = COOLING_ENDS,
    kind = ItemKind.NEED,
    status = ItemStatus.ACTIVE,
    link = "https://example.com/fridge",
    imageRef = "images/42.jpg",
    category = "Kitchen",
    notes = "Old one broke",
    targetDate = LocalDate.of(2026, 12, 24),
    recheckAt = Instant.parse("2026-11-09T09:30:00Z"),
    prioritySlot = 2,
    markedAsNeedAt = Instant.parse("2026-10-02T08:00:00Z")
)

internal val FULL_REFLECTION = Reflection(
    why = "Better photos on hikes",
    usageFrequency = UsageFrequency.WEEKLY,
    reasonsAgainst = listOf("My phone takes decent photos", "Heavy, \"bulky\", and 1,200 €"),
    problemSolved = "Blurry zoom shots",
    currentAlternative = "Phone",
    replaces = "Nothing",
    whyNow = "Trip in spring",
    upgradeOrNew = UpgradeOrNew.NEW
)

internal fun buy(at: Instant = DECIDED, source: DecisionSource = DecisionSource.APP) =
    Decision(DecisionType.BUY, at, source, actualPrice = Money(11_500, "EUR"))

internal fun reject(at: Instant = DECIDED) = Decision(DecisionType.REJECT, at, DecisionSource.NOTIFICATION)

internal fun waitAWeek(at: Instant = DECIDED) =
    Decision(DecisionType.WAIT, at, waitUntil = at.atZone(ZoneOffset.UTC).plusDays(7).toInstant())

/** One decision of each type, every field its type allows set. */
internal val EVERY_DECISION = listOf(
    buy(),
    Decision(DecisionType.BUY_EARLY, DECIDED, actualPrice = Money(12_000, "EUR"), earlyReason = "Sale ends today"),
    Decision(DecisionType.BUY_NEED, DECIDED, actualPrice = Money(0, "EUR")),
    waitAWeek(),
    reject().copy(note = "My phone is enough")
)
