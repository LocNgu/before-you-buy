package io.github.locngu.beforeyoubuy.core.domain.settings

import io.github.locngu.beforeyoubuy.core.domain.coolingoff.CoolingOffRules
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.priorities.DEFAULT_PRIORITY_SLOTS
import io.github.locngu.beforeyoubuy.core.domain.priorities.MAX_PRIORITY_SLOTS
import io.github.locngu.beforeyoubuy.core.domain.priorities.MIN_PRIORITY_SLOTS
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

/**
 * The rules every item is held to (brief §4.2, §4.7, §6.7): the app's one currency, the cooling-off tiers in it and
 * the number of priority slots. Stored with the items, because changing any of them rewrites items in the same
 * transaction (ADR-0031, ADR-0033, ADR-0047).
 *
 * @property currency ISO 4217 code every stored amount is in. Fixed when the app first starts (the device region's
 *   currency, else EUR, see [defaultCurrency]); only [PolicyRepository.changeCurrency] changes it.
 * @property coolingOffRules always in [currency]; [CoolingOffRules.defaults] until the user sets their own.
 * @property prioritySlots how many wants can be priorities at once, 1–5 (brief §4.7, ADR-0011).
 */
data class Policy(
    val currency: String,
    val coolingOffRules: CoolingOffRules,
    val prioritySlots: Int = DEFAULT_PRIORITY_SLOTS
) {
    init {
        require(prioritySlots in MIN_PRIORITY_SLOTS..MAX_PRIORITY_SLOTS) {
            "Priority slots are $MIN_PRIORITY_SLOTS–$MAX_PRIORITY_SLOTS, was $prioritySlots"
        }
        require(coolingOffRules.currency == currency) {
            "Cooling-off rules in ${coolingOffRules.currency} don't match the currency $currency"
        }
    }
}

/** Stores the [Policy]. Implemented in `:core:data` (Room, next to the items). */
interface PolicyRepository {
    /** The current policy; emits again on every change. */
    val policy: Flow<Policy>

    /**
     * Stores [rules] and, in the same transaction, changes the wait of every active want whose wait isn't complete at
     * [changedAt] (ADR-0031, via [io.github.locngu.beforeyoubuy.core.domain.coolingoff.waitAfterRulesChange], in
     * [zone]). Both are written or neither is. [rules] must be in the current currency; anything else throws.
     */
    suspend fun changeCoolingOffRules(rules: CoolingOffRules, changedAt: Instant, zone: ZoneId)

    /**
     * Makes [currency] the app's currency and relabels every stored amount to it in one transaction (ADR-0033):
     * item prices, actual prices in history, the tiers, the monthly allowance and every pool entry keep their numbers ("100 €" becomes "100 $"; see
     * [io.github.locngu.beforeyoubuy.core.domain.money.relabeled]). Waits don't change. Anything not
     * [usable][isUsableCurrency] throws; the current currency changes nothing.
     */
    suspend fun changeCurrency(currency: String)

    /**
     * Makes [slots] (1–5, anything else throws) the number of priority slots. Fewer slots than priorities send the
     * lowest-ranked back to the wishlist in the same transaction
     * ([io.github.locngu.beforeyoubuy.core.domain.priorities.demotedBySlots]); the screen lists them first (ADR-0047).
     * Their wait pauses at [at], counted in calendar days in [zone] (ADR-0049). More slots change no item.
     *
     * Writes only if the stored priorities are still [expected] (ids, rank 1 first), the ones the screen listed from;
     * returns false and writes nothing otherwise, so a demotion the user didn't see never happens.
     */
    suspend fun changePrioritySlots(slots: Int, expected: List<ItemId>, at: Instant, zone: ZoneId): Boolean
}
