package io.github.locngu.beforeyoubuy.core.data.item

import io.github.locngu.beforeyoubuy.core.data.db.DecisionEntity
import io.github.locngu.beforeyoubuy.core.data.db.ItemDao
import io.github.locngu.beforeyoubuy.core.data.db.ItemEntity
import io.github.locngu.beforeyoubuy.core.data.db.ReflectionEntity
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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

// Entity ↔ domain. Instants become epoch millis (sub-millisecond precision is dropped). Enums are written by name and
// read defensively (ADR-0019): an unknown name, e.g. from a newer app version, falls back instead of crashing.

internal fun Item.toEntity(): ItemEntity = ItemEntity(
    id = id.value,
    name = name,
    priceMinor = price.minorUnits,
    currency = price.currency,
    addedAt = addedAt.toEpochMilli(),
    coolingEndsAt = storedCoolingEnd(),
    kind = kind.name,
    status = status.name,
    link = link,
    imageRef = imageRef,
    category = category,
    notes = notes,
    targetDate = targetDate?.toString(),
    recheckAt = recheckAt?.toEpochMilli(),
    prioritySlot = prioritySlot,
    markedAsNeedAt = markedAsNeedAt?.toEpochMilli(),
    coolingOffDays = coolingOffDays,
    waitedDays = waitedDays,
    waitingSince = waitingSince?.toEpochMilli()
)

/** `cooling_ends_at`: the running wait's end, or [Item.addedAt] while the wait doesn't run ([ItemEntity]). */
internal fun Item.storedCoolingEnd(): Long = (coolingEndsAt ?: addedAt).toEpochMilli()

/**
 * Unknown kind → `WANT`; unknown status → `ACTIVE`; an unreadable target date → none (it's informational). Negative
 * days read as 0 and an end before its start as the start; such a row is read-only, like any read with a fallback.
 */
internal fun ItemEntity.toDomain(): Item {
    val since = waitingSince?.let(Instant::ofEpochMilli)
    return Item(
        id = ItemId(id),
        name = name,
        price = Money(priceMinor, currency),
        addedAt = Instant.ofEpochMilli(addedAt),
        coolingOffDays = coolingOffDays.coerceAtLeast(0),
        waitedDays = waitedDays.coerceAtLeast(0),
        waitingSince = since,
        coolingEndsAt = since?.let { maxOf(it, Instant.ofEpochMilli(coolingEndsAt)) },
        kind = enumOrNull<ItemKind>(kind) ?: ItemKind.WANT,
        status = enumOrNull<ItemStatus>(status) ?: ItemStatus.ACTIVE,
        link = link,
        imageRef = imageRef,
        category = category,
        notes = notes,
        targetDate = targetDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        recheckAt = recheckAt?.let(Instant::ofEpochMilli),
        prioritySlot = prioritySlot,
        markedAsNeedAt = markedAsNeedAt?.let(Instant::ofEpochMilli)
    )
}

/** Writes [item]'s wait (`waitAfterRulesChange`), targeted so other columns survive. */
internal suspend fun ItemDao.setWait(item: Item) = setWait(
    item.id.value,
    item.coolingOffDays,
    item.waitedDays,
    item.waitingSince?.toEpochMilli(),
    item.storedCoolingEnd()
)

/** Writes the slot and wait of each item in [changed] (from `withSlots`), targeted so other columns survive. */
internal suspend fun ItemDao.setSlotsAndWaits(changed: List<Item>) = changed.forEach {
    setSlotAndWait(it.id.value, it.prioritySlot, it.waitedDays, it.waitingSince?.toEpochMilli(), it.storedCoolingEnd())
}

internal fun Reflection.toEntity(itemId: ItemId): ReflectionEntity = ReflectionEntity(
    itemId = itemId.value,
    why = why,
    usageFrequency = usageFrequency?.name,
    reasonsAgainst = Json.encodeToString(reasonsSerializer, reasonsAgainst),
    problemSolved = problemSolved,
    currentAlternative = currentAlternative,
    replaces = replaces,
    whyNow = whyNow,
    upgradeOrNew = upgradeOrNew?.name
)

/** Unknown answers to the choice questions read as not answered. */
internal fun ReflectionEntity.toDomain(): Reflection = Reflection(
    why = why,
    usageFrequency = enumOrNull<UsageFrequency>(usageFrequency),
    reasonsAgainst = runCatching { Json.decodeFromString(reasonsSerializer, reasonsAgainst) }.getOrDefault(emptyList()),
    problemSolved = problemSolved,
    currentAlternative = currentAlternative,
    replaces = replaces,
    whyNow = whyNow,
    upgradeOrNew = enumOrNull<UpgradeOrNew>(upgradeOrNew)
)

internal fun Decision.toEntity(itemId: ItemId): DecisionEntity = DecisionEntity(
    id = 0,
    itemId = itemId.value,
    type = type.name,
    decidedAt = decidedAt.toEpochMilli(),
    source = source.name,
    actualPriceMinor = actualPrice?.minorUnits,
    actualPriceCurrency = actualPrice?.currency,
    earlyReason = earlyReason,
    waitUntil = waitUntil?.toEpochMilli(),
    note = note
)

/**
 * Null for a decision type this version doesn't know: no fallback type would be honest (each carries different
 * fields), so the row is skipped. Unknown source → `APP`.
 */
internal fun DecisionEntity.toDomain(): Decision? {
    val type = enumOrNull<DecisionType>(type) ?: return null
    return Decision(
        type = type,
        decidedAt = Instant.ofEpochMilli(decidedAt),
        source = enumOrNull<DecisionSource>(source) ?: DecisionSource.APP,
        actualPrice = if (actualPriceMinor != null && actualPriceCurrency != null) {
            Money(actualPriceMinor, actualPriceCurrency)
        } else {
            null
        },
        earlyReason = earlyReason,
        waitUntil = waitUntil?.let(Instant::ofEpochMilli),
        note = note
    )
}

private val reasonsSerializer = ListSerializer(String.serializer())

private inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? =
    name?.let { runCatching { enumValueOf<E>(it) }.getOrNull() }
