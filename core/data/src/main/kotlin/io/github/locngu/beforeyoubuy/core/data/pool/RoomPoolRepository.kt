package io.github.locngu.beforeyoubuy.core.data.pool

import androidx.room.withTransaction
import io.github.locngu.beforeyoubuy.core.data.db.AllowanceEntity
import io.github.locngu.beforeyoubuy.core.data.db.BeforeYouBuyDatabase
import io.github.locngu.beforeyoubuy.core.data.db.PoolEntryEntity
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.core.domain.pool.AllowanceSettings
import io.github.locngu.beforeyoubuy.core.domain.pool.PoolEntry
import io.github.locngu.beforeyoubuy.core.domain.pool.PoolRepository
import io.github.locngu.beforeyoubuy.core.domain.pool.accrualsDue
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * [PoolRepository] on the `allowance` row and the `pool_entries` ledger, in the items' database so a currency change
 * relabels them in one transaction (ADR-0033). Accruals are idempotent twice over: each run reads the last credited
 * month inside its transaction, and the unique `accrual_month` index ignores a month that is already there.
 */
internal class RoomPoolRepository(private val database: BeforeYouBuyDatabase) : PoolRepository {
    private val pool = database.poolDao()
    private val policies = database.policyDao()

    override val allowance: Flow<AllowanceSettings?> =
        pool.observeAllowance().map { it?.toDomain() }.distinctUntilChanged()

    override val entries: Flow<List<PoolEntry>> =
        pool.observeEntries().map { rows -> rows.map { it.toDomain() } }.distinctUntilChanged()

    override suspend fun setUp(
        monthlyAmount: Money,
        accrualDay: Int,
        startingBalance: Money,
        now: Instant,
        zone: ZoneId
    ): Boolean = database.withTransaction {
        requireAppCurrency(monthlyAmount, startingBalance)
        val settings = AllowanceSettings(monthlyAmount, accrualDay, now.atZone(zone).toLocalDate())
        val existing = pool.getAllowance()
        if (existing?.toDomain() != null) return@withTransaction false
        // A row this version can't read shows as not set up (ADR-0032); setting up again replaces it.
        if (existing == null) pool.insertAllowance(settings.toEntity()) else pool.updateAllowance(settings.toEntity())
        if (startingBalance.minorUnits > 0) pool.insertEntry(PoolEntry.StartingBalance(startingBalance, now).toEntity())
        accrueWith(settings, now, zone)
        true
    }

    override suspend fun change(
        monthlyAmount: Money,
        accrualDay: Int,
        startingBalance: Money,
        now: Instant,
        zone: ZoneId
    ) = database.withTransaction {
        requireAppCurrency(monthlyAmount, startingBalance)
        val old = checkNotNull(pool.getAllowance()?.toDomain()) { "The allowance isn't set up" }
        // Months already due get the old amount; the new one applies from the next accrual on.
        accrueWith(old, now, zone)
        val new = old.changed(monthlyAmount, accrualDay)
        pool.updateAllowance(new.toEntity())
        // An earlier accrual day may make this month due right away.
        accrueWith(new, now, zone)
        // The starting amount is a setting, not something that happened: it's replaced, keeping its place in time.
        val since = pool.firstStartingBalanceAt()
        pool.deleteStartingBalances()
        if (startingBalance.minorUnits > 0) {
            val at = since?.let(Instant::ofEpochMilli) ?: now
            pool.insertEntry(PoolEntry.StartingBalance(startingBalance, at).toEntity())
        }
    }

    override suspend fun adjust(change: Money, note: String?, at: Instant) {
        database.withTransaction {
            requireAppCurrency(change)
            pool.insertEntry(PoolEntry.Adjustment(change, note?.trim()?.ifEmpty { null }, at).toEntity())
        }
    }

    override suspend fun accrue(now: Instant, zone: ZoneId): Int = database.withTransaction {
        val settings = pool.getAllowance()?.toDomain() ?: return@withTransaction 0
        accrueWith(settings, now, zone)
    }

    /** Credits every month due under [settings]; returns how many were written. Call inside a transaction. */
    private suspend fun accrueWith(settings: AllowanceSettings, now: Instant, zone: ZoneId): Int {
        val last = pool.lastAccruedMonth()?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
        return accrualsDue(settings, last, now, zone).count { month ->
            pool.insertEntry(PoolEntry.Accrual(month, settings.monthlyAmount, now).toEntity()) != -1L
        }
    }

    private suspend fun requireAppCurrency(vararg amounts: Money) {
        val currency = checkNotNull(policies.get()) { "The policy row is missing" }.currency
        amounts.forEach { require(it.currency == currency) { "$it isn't in the app's currency $currency" } }
    }
}

/** Unreadable stored values: a day outside 1–28 is clamped, an unreadable start date reads as not set up (ADR-0032). */
internal fun AllowanceEntity.toDomain(): AllowanceSettings? {
    val start = runCatching { LocalDate.parse(startDate) }.getOrNull() ?: return null
    return AllowanceSettings(Money(monthlyMinor, currency), AllowanceSettings.clampAccrualDay(accrualDay), start)
}

internal fun AllowanceSettings.toEntity(): AllowanceEntity = AllowanceEntity(
    monthlyMinor = monthlyAmount.minorUnits,
    currency = monthlyAmount.currency,
    accrualDay = accrualDay,
    startDate = startDate.toString()
)

/** The stored `type` of each entry. Names are stored, so never rename one (ADR-0019). */
internal enum class PoolEntryType { STARTING_BALANCE, ACCRUAL, ADJUSTMENT, PURCHASE }

/**
 * Rows this version can't read fully (an unknown type, an unreadable month, a negative credit) read as an
 * adjustment by the stored amount, so the balance stays right (ADR-0032).
 */
internal fun PoolEntryEntity.toDomain(): PoolEntry {
    val change = Money(amountMinor, currency)
    val at = Instant.ofEpochMilli(at)
    val type = runCatching { PoolEntryType.valueOf(type) }.getOrNull()
    return runCatching {
        when (type) {
            PoolEntryType.STARTING_BALANCE -> PoolEntry.StartingBalance(change, at)
            PoolEntryType.ACCRUAL -> PoolEntry.Accrual(YearMonth.parse(accrualMonth), change, at)
            PoolEntryType.PURCHASE -> PoolEntry.Purchase(itemId?.let(::ItemId), Money(-amountMinor, currency), at)
            PoolEntryType.ADJUSTMENT, null -> null
        }
    }.getOrNull() ?: PoolEntry.Adjustment(change, note, at)
}

internal fun PoolEntry.toEntity(): PoolEntryEntity {
    val base = PoolEntryEntity(
        type = "",
        amountMinor = change.minorUnits,
        currency = change.currency,
        at = at.toEpochMilli()
    )
    return when (this) {
        is PoolEntry.StartingBalance -> base.copy(type = PoolEntryType.STARTING_BALANCE.name)
        is PoolEntry.Accrual -> base.copy(type = PoolEntryType.ACCRUAL.name, accrualMonth = month.toString())
        is PoolEntry.Adjustment -> base.copy(type = PoolEntryType.ADJUSTMENT.name, note = note)
        is PoolEntry.Purchase -> base.copy(type = PoolEntryType.PURCHASE.name, itemId = itemId?.value)
    }
}
