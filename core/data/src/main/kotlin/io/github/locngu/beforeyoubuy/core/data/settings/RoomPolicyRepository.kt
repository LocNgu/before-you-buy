package io.github.locngu.beforeyoubuy.core.data.settings

import androidx.room.withTransaction
import io.github.locngu.beforeyoubuy.core.data.db.BeforeYouBuyDatabase
import io.github.locngu.beforeyoubuy.core.data.db.PolicyEntity
import io.github.locngu.beforeyoubuy.core.data.item.toDomain
import io.github.locngu.beforeyoubuy.core.domain.coolingoff.CoolingOffRules
import io.github.locngu.beforeyoubuy.core.domain.coolingoff.coolingEndAfterRulesChange
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.core.domain.money.relabeled
import io.github.locngu.beforeyoubuy.core.domain.settings.Policy
import io.github.locngu.beforeyoubuy.core.domain.settings.PolicyRepository
import io.github.locngu.beforeyoubuy.core.domain.settings.isUsableCurrency
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

/** [PolicyRepository] on the policy row, next to the items it rewrites (ADR-0031, ADR-0033). */
internal class RoomPolicyRepository(private val database: BeforeYouBuyDatabase) : PolicyRepository {
    private val policies = database.policyDao()
    private val items = database.itemDao()
    private val decisions = database.decisionDao()

    // The row is created when the database opens (withPolicyRow), so null never lasts; no default is guessed here.
    override val policy: Flow<Policy> = policies.observe().filterNotNull().map { it.toDomain() }.distinctUntilChanged()

    override suspend fun changeCoolingOffRules(rules: CoolingOffRules, changedAt: Instant, zone: ZoneId) =
        database.withTransaction {
            val row = requireRow()
            require(rules.currency == row.currency) {
                "Rules in ${rules.currency} don't match the currency ${row.currency}"
            }
            policies.update(row.copy(coolingOffRules = CoolingOffRulesJson.encode(rules)))
            items.getByStatus(ItemStatus.ACTIVE.name).forEach { entity ->
                val item = entity.toDomain()
                val end = coolingEndAfterRulesChange(item, rules, changedAt, zone)
                if (end != item.coolingEndsAt) items.setCoolingEnd(entity.id, end.toEpochMilli())
            }
        }

    override suspend fun changeCurrency(currency: String) {
        require(isUsableCurrency(currency)) { "Not a usable ISO 4217 currency: \"$currency\"" }
        database.withTransaction {
            val row = requireRow()
            if (row.currency == currency) return@withTransaction
            items.getAll().forEach {
                val price = Money(it.priceMinor, it.currency).relabeled(currency)
                items.setPrice(it.id, price.minorUnits, currency)
            }
            decisions.getWithActualPrice().forEach {
                val paid = Money(it.actualPriceMinor!!, it.actualPriceCurrency!!).relabeled(currency)
                decisions.setActualPrice(it.id, paid.minorUnits, currency)
            }
            // Default tiers stay null: the defaults in the new currency have the same numbers (ADR-0033).
            val rules = row.coolingOffRules?.let { CoolingOffRulesJson.decode(it, row.currency) }?.relabeled(currency)
            policies.update(
                PolicyEntity(currency = currency, coolingOffRules = rules?.let(CoolingOffRulesJson::encode))
            )
        }
    }

    private suspend fun requireRow(): PolicyEntity = checkNotNull(policies.get()) { "The policy row is missing" }
}

/** Unreadable or invalid stored tiers read as the defaults (ADR-0032). */
internal fun PolicyEntity.toDomain(): Policy = Policy(
    currency = currency,
    coolingOffRules = coolingOffRules?.let { CoolingOffRulesJson.decode(it, currency) }
        ?: CoolingOffRules.defaults(currency)
)
