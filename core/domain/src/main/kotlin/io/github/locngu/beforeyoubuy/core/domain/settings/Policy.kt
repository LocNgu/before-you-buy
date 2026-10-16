package io.github.locngu.beforeyoubuy.core.domain.settings

import io.github.locngu.beforeyoubuy.core.domain.coolingoff.CoolingOffRules
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

/**
 * The rules every item is held to (brief §4.2, §6.7): the app's one currency and the cooling-off tiers in it.
 * Stored with the items, because changing either rewrites items in the same transaction (ADR-0031, ADR-0033).
 *
 * @property currency ISO 4217 code every stored amount is in. Fixed when the app first starts (the device region's
 *   currency, else EUR, see [defaultCurrency]); only [PolicyRepository.changeCurrency] changes it.
 * @property coolingOffRules always in [currency]; [CoolingOffRules.defaults] until the user sets their own.
 */
data class Policy(val currency: String, val coolingOffRules: CoolingOffRules) {
    init {
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
     * Stores [rules] and, in the same transaction, moves the cooling-off end of every active want that is still
     * cooling off at [changedAt] (ADR-0031, via
     * [io.github.locngu.beforeyoubuy.core.domain.coolingoff.coolingEndAfterRulesChange], in [zone]). Both are written
     * or neither is. [rules] must be in the current currency; anything else throws.
     */
    suspend fun changeCoolingOffRules(rules: CoolingOffRules, changedAt: Instant, zone: ZoneId)

    /**
     * Makes [currency] the app's currency and relabels every stored amount to it in one transaction (ADR-0033):
     * item prices, actual prices in history and the tiers keep their numbers ("100 €" becomes "100 $"; see
     * [io.github.locngu.beforeyoubuy.core.domain.money.relabeled]). Cooling-off ends don't change. Anything not
     * [usable][isUsableCurrency] throws; the current currency changes nothing.
     */
    suspend fun changeCurrency(currency: String)
}
