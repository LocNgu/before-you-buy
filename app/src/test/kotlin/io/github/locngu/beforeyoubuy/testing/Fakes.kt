package io.github.locngu.beforeyoubuy.testing

import io.github.locngu.beforeyoubuy.core.domain.coolingoff.CoolingOffRules
import io.github.locngu.beforeyoubuy.core.domain.item.Decision
import io.github.locngu.beforeyoubuy.core.domain.item.HistoryEntry
import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemRepository
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.item.Reflection
import io.github.locngu.beforeyoubuy.core.domain.settings.Policy
import io.github.locngu.beforeyoubuy.core.domain.settings.PolicyRepository
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Keeps added items in memory. Only what view model tests use so far; the rest throws. */
class FakeItemRepository : ItemRepository {
    private val items = MutableStateFlow(emptyList<Item>())

    /** Every stored item, in the order added. */
    val stored: List<Item> get() = items.value

    /** Makes the next [add] throw, like a database error. */
    var failNextAdd = false

    override suspend fun add(item: Item): ItemId {
        if (failNextAdd) {
            failNextAdd = false
            throw IllegalStateException("Storing failed")
        }
        require(item.status == ItemStatus.ACTIVE) { "Only active items can be added" }
        val id = ItemId(items.value.size + 1L)
        items.value += item.copy(id = id)
        return id
    }

    override fun observeActive(): Flow<List<Item>> = items.map { all -> all.filter { it.status == ItemStatus.ACTIVE } }

    override fun observeById(id: ItemId): Flow<Item?> = items.map { all -> all.find { it.id == id } }

    override suspend fun update(current: Item, edited: Item): Boolean = TODO("Not needed yet")

    override fun observeHistory(status: ItemStatus): Flow<List<HistoryEntry>> = TODO("Not needed yet")

    override fun observeReflection(id: ItemId): Flow<Reflection?> = TODO("Not needed yet")

    override fun observeDecisions(id: ItemId): Flow<List<Decision>> = TODO("Not needed yet")

    override suspend fun setReflection(id: ItemId, reflection: Reflection): Boolean = TODO("Not needed yet")

    override suspend fun recordDecision(current: Item, decision: Decision, result: Item): Boolean =
        TODO("Not needed yet")
}

/** A [Policy] in memory, EUR with the default tiers unless set otherwise. */
class FakePolicyRepository(initial: Policy = Policy("EUR", CoolingOffRules.defaults("EUR"))) : PolicyRepository {
    override val policy = MutableStateFlow(initial)

    override suspend fun changeCoolingOffRules(rules: CoolingOffRules, changedAt: Instant, zone: ZoneId) {
        policy.value = policy.value.copy(coolingOffRules = rules)
    }

    override suspend fun changeCurrency(currency: String) = TODO("Not needed yet")
}
