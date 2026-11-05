package io.github.locngu.beforeyoubuy.core.data.backup

import io.github.locngu.beforeyoubuy.core.data.db.AllocationEntity
import io.github.locngu.beforeyoubuy.core.data.db.AllowanceEntity
import io.github.locngu.beforeyoubuy.core.data.db.DecisionEntity
import io.github.locngu.beforeyoubuy.core.data.db.ItemEntity
import io.github.locngu.beforeyoubuy.core.data.db.ItemNotificationsEntity
import io.github.locngu.beforeyoubuy.core.data.db.PolicyEntity
import io.github.locngu.beforeyoubuy.core.data.db.PoolEntryEntity
import io.github.locngu.beforeyoubuy.core.data.db.ReflectionEntity
import kotlinx.serialization.Serializable

// The backup file's JSON (ADR-0055): DTOs of their own, so renaming a Room column never changes the file format.
// Same conventions as the database (ADR-0032): money is minor units + currency, instants epoch millis, dates ISO text,
// enums their names as strings. Rows keep their ids, since rows refer to each other. Every field after
// formatVersion 1 must have a default, so an older app's file still reads.

/** Read first and alone, so a file from a newer app is recognised even if the rest of it has another shape. */
@Serializable
internal data class BackupHeader(val formatVersion: Int = 0)

@Serializable
internal data class BackupDto(
    val formatVersion: Int,
    val exportedAt: Long,
    val appVersion: String,
    val settings: SettingsDto,
    val policy: PolicyDto,
    val items: List<ItemDto> = emptyList(),
    val reflections: List<ReflectionDto> = emptyList(),
    val decisions: List<DecisionDto> = emptyList(),
    val itemNotifications: List<ItemNotificationsDto> = emptyList(),
    val allowance: AllowanceDto? = null,
    val poolEntries: List<PoolEntryDto> = emptyList(),
    val allocations: List<AllocationDto> = emptyList()
)

/** The preferences worth carrying to another phone. Not "notification permission asked": that is the phone's. */
@Serializable
internal data class SettingsDto(
    val notifyWaitingPeriods: Boolean = true,
    val notifyReflectionReminders: Boolean = true,
    val onboardingComplete: Boolean = false
)

@Serializable
internal data class PolicyDto(val currency: String, val coolingOffRules: String? = null, val prioritySlots: Int = 3)

@Serializable
internal data class ItemDto(
    val id: Long,
    val name: String,
    val priceMinor: Long,
    val currency: String,
    val addedAt: Long,
    val coolingEndsAt: Long,
    val kind: String,
    val status: String,
    val link: String? = null,
    val imageRef: String? = null,
    val category: String? = null,
    val notes: String? = null,
    val targetDate: String? = null,
    val recheckAt: Long? = null,
    val prioritySlot: Int? = null,
    val markedAsNeedAt: Long? = null,
    val coolingOffDays: Int = 0,
    val waitedDays: Int = 0,
    val waitingSince: Long? = null
)

@Serializable
internal data class ReflectionDto(
    val itemId: Long,
    val why: String? = null,
    val usageFrequency: String? = null,
    val reasonsAgainst: String = "[]",
    val problemSolved: String? = null,
    val currentAlternative: String? = null,
    val replaces: String? = null,
    val whyNow: String? = null,
    val upgradeOrNew: String? = null
)

@Serializable
internal data class DecisionDto(
    val id: Long,
    val itemId: Long,
    val type: String,
    val decidedAt: Long,
    val source: String,
    val actualPriceMinor: Long? = null,
    val actualPriceCurrency: String? = null,
    val earlyReason: String? = null,
    val waitUntil: Long? = null,
    val note: String? = null
)

@Serializable
internal data class ItemNotificationsDto(
    val itemId: Long,
    val waitingPeriodOverAt: Long? = null,
    val reflectionNudgedAt: Long? = null
)

@Serializable
internal data class AllowanceDto(
    val monthlyMinor: Long,
    val currency: String,
    val accrualDay: Int,
    val startDate: String,
    val wantsNeedFunding: Boolean = true
)

@Serializable
internal data class PoolEntryDto(
    val id: Long,
    val type: String,
    val amountMinor: Long,
    val currency: String,
    val at: Long,
    val accrualMonth: String? = null,
    val itemId: Long? = null,
    val note: String? = null
)

@Serializable
internal data class AllocationDto(
    val id: Long,
    val itemId: Long,
    val amountMinor: Long,
    val currency: String,
    val at: Long
)

internal fun PolicyEntity.toDto() = PolicyDto(currency, coolingOffRules, prioritySlots)

internal fun PolicyDto.toEntity() = PolicyEntity(PolicyEntity.ID, currency, coolingOffRules, prioritySlots)

internal fun ItemEntity.toDto() = ItemDto(
    id, name, priceMinor, currency, addedAt, coolingEndsAt, kind, status, link, imageRef, category, notes, targetDate,
    recheckAt, prioritySlot, markedAsNeedAt, coolingOffDays, waitedDays, waitingSince
)

internal fun ItemDto.toEntity() = ItemEntity(
    id, name, priceMinor, currency, addedAt, coolingEndsAt, kind, status, link, imageRef, category, notes, targetDate,
    recheckAt, prioritySlot, markedAsNeedAt, coolingOffDays, waitedDays, waitingSince
)

internal fun ReflectionEntity.toDto() = ReflectionDto(
    itemId, why, usageFrequency, reasonsAgainst, problemSolved, currentAlternative, replaces, whyNow, upgradeOrNew
)

internal fun ReflectionDto.toEntity() = ReflectionEntity(
    itemId, why, usageFrequency, reasonsAgainst, problemSolved, currentAlternative, replaces, whyNow, upgradeOrNew
)

internal fun DecisionEntity.toDto() = DecisionDto(
    id, itemId, type, decidedAt, source, actualPriceMinor, actualPriceCurrency, earlyReason, waitUntil, note
)

internal fun DecisionDto.toEntity() = DecisionEntity(
    id, itemId, type, decidedAt, source, actualPriceMinor, actualPriceCurrency, earlyReason, waitUntil, note
)

internal fun ItemNotificationsEntity.toDto() = ItemNotificationsDto(itemId, waitingPeriodOverAt, reflectionNudgedAt)

internal fun ItemNotificationsDto.toEntity() = ItemNotificationsEntity(itemId, waitingPeriodOverAt, reflectionNudgedAt)

internal fun AllowanceEntity.toDto() = AllowanceDto(monthlyMinor, currency, accrualDay, startDate, wantsNeedFunding)

internal fun AllowanceDto.toEntity() =
    AllowanceEntity(AllowanceEntity.ID, monthlyMinor, currency, accrualDay, startDate, wantsNeedFunding)

internal fun PoolEntryEntity.toDto() = PoolEntryDto(id, type, amountMinor, currency, at, accrualMonth, itemId, note)

internal fun PoolEntryDto.toEntity() = PoolEntryEntity(id, type, amountMinor, currency, at, accrualMonth, itemId, note)

internal fun AllocationEntity.toDto() = AllocationDto(id, itemId, amountMinor, currency, at)

internal fun AllocationDto.toEntity() = AllocationEntity(id, itemId, amountMinor, currency, at)
