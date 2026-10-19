package io.github.locngu.beforeyoubuy.testing

import io.github.locngu.beforeyoubuy.core.domain.coolingoff.CoolingOffRules
import io.github.locngu.beforeyoubuy.core.domain.item.Decision
import io.github.locngu.beforeyoubuy.core.domain.item.HistoryEntry
import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemImageStore
import io.github.locngu.beforeyoubuy.core.domain.item.ItemRepository
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.item.Reflection
import io.github.locngu.beforeyoubuy.core.domain.settings.Policy
import io.github.locngu.beforeyoubuy.core.domain.settings.PolicyRepository
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Keeps added items in memory. Only what view model tests use so far; the rest throws. */
class FakeItemRepository : ItemRepository {
    private val items = MutableStateFlow(emptyList<Item>())
    private val reflections = MutableStateFlow(emptyMap<ItemId, Reflection>())

    /** Every stored item, in the order added. */
    val stored: List<Item> get() = items.value

    /** Makes the next [add] throw, like a database error. */
    var failNextAdd = false

    /** While set, [add] waits for it, like a slow database. */
    var addGate: CompletableDeferred<Unit>? = null

    override suspend fun add(item: Item): ItemId {
        addGate?.await()
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

    /** Updates seen, as (current, edited). */
    val updates = mutableListOf<Pair<Item, Item>>()

    /** Makes the next [update] find the item changed, as if it was edited elsewhere between reading and writing. */
    var conflictOnNextUpdate = false

    /** Compare-and-set, like the real one: writes only if the stored item still equals [current]. */
    override suspend fun update(current: Item, edited: Item): Boolean {
        require(edited.id == current.id && edited.status == current.status) { "update can't change id or status" }
        updates += current to edited
        if (conflictOnNextUpdate) {
            conflictOnNextUpdate = false
            return false
        }
        if (items.value.none { it == current }) return false
        replace(edited)
        return true
    }

    override fun observeHistory(status: ItemStatus): Flow<List<HistoryEntry>> = TODO("Not needed yet")

    override fun observeReflection(id: ItemId): Flow<Reflection?> = reflections.map { it[id] }

    override fun observeReflections(): Flow<Map<ItemId, Reflection>> = reflections

    /** Stores [reflection] for [id], as the reflection flow will. */
    fun reflect(id: ItemId, reflection: Reflection) {
        reflections.value += id to reflection
    }

    /** Replaces an item as stored, e.g. to move its cooling-off end or end it. */
    fun replace(item: Item) {
        items.value = items.value.map { if (it.id == item.id) item else it }
    }

    override suspend fun imageRefs(): Set<String> = items.value.mapNotNullTo(mutableSetOf()) { it.imageRef }

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

/** A [Clock] that only moves when a test says so. */
class MutableClock(private var now: Instant, private val zone: ZoneId = ZoneOffset.UTC) : Clock() {
    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)

    override fun instant(): Instant = now

    fun advance(duration: Duration) {
        now += duration
    }
}

/** Photos in [directory]: an import "succeeds" for any source except [UNREADABLE], naming the photo after it. */
class FakeItemImageStore(private val directory: File = File("photos")) : ItemImageStore {
    /** Sources imported so far. */
    val imported = mutableListOf<String>()

    /** What the last sweep was asked to keep, and its cutoff. */
    var lastSweep: Pair<Set<String>, Instant>? = null

    override suspend fun import(source: String): String? {
        imported += source
        return if (source == UNREADABLE) null else "${source.substringAfterLast('/')}.jpg"
    }

    override fun fileFor(ref: String): File = File(directory, ref)

    override suspend fun deleteUnreferenced(referenced: Set<String>, writtenBefore: Instant): Set<String> {
        lastSweep = referenced to writtenBefore
        return emptySet()
    }

    companion object {
        const val UNREADABLE = "content://picker/not-an-image"
    }
}
