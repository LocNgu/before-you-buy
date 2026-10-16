package io.github.locngu.beforeyoubuy.core.data.settings

import io.github.locngu.beforeyoubuy.core.domain.coolingoff.CoolingOffRules
import io.github.locngu.beforeyoubuy.core.domain.coolingoff.CoolingOffRulesResult
import io.github.locngu.beforeyoubuy.core.domain.coolingoff.CoolingOffTier
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import java.time.Period
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Cooling-off tiers as stored in settings: `{"currency":"EUR","tiers":[{"minPriceMinor":0,"days":7},…]}`.
 * Periods are whole days, which [CoolingOffRules] guarantees.
 */
internal object CoolingOffRulesJson {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(rules: CoolingOffRules): String = json.encodeToString(
        StoredRules.serializer(),
        StoredRules(rules.currency, rules.tiers.map { StoredTier(it.minPriceInclusive.minorUnits, it.period.days) })
    )

    /** The stored rules, or null if [text] is unreadable, invalid, or in a currency other than [currency]. */
    fun decode(text: String, currency: String): CoolingOffRules? = runCatching {
        val stored = json.decodeFromString(StoredRules.serializer(), text)
        if (stored.currency != currency) return@runCatching null
        val tiers = stored.tiers.map { CoolingOffTier(Money(it.minPriceMinor, currency), Period.ofDays(it.days)) }
        (CoolingOffRules.from(tiers) as? CoolingOffRulesResult.Valid)?.rules
    }.getOrNull()

    @Serializable
    private data class StoredRules(val currency: String, val tiers: List<StoredTier>)

    @Serializable
    private data class StoredTier(val minPriceMinor: Long, val days: Int)
}
