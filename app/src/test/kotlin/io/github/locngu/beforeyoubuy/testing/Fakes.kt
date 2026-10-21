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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Keeps added items in memory. Only what view model tests use so far; the rest throws. */
class FakeItemRepository : ItemRepository {
    private val items = MutableStateFlow(emptyList<Item>())
    private val reflections = MutableStateFlow(emptyMap<ItemId, Reflection>())
    private val decisionsById = MutableStateFlow(emptyMap<ItemId, List<Decision>>())

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

    /** Like the real one: ended items with the decision appended last, most recently decided first. */
    override fun observeHistory(status: ItemStatus): Flow<List<HistoryEntry>> {
        require(status != ItemStatus.ACTIVE) { "History holds purchased and rejected items only" }
        return combine(items, decisionsById) { all, decisions ->
            all.filter { it.status == status }
                .mapNotNull { item -> decisions[item.id]?.lastOrNull()?.let { HistoryEntry(item, it) } }
                .sortedByDescending { it.decision.decidedAt }
        }
    }

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

    /** Deletes an item with its answers and decisions, as the user may (brief §5). */
    fun delete(id: ItemId) {
        items.value = items.value.filterNot { it.id == id }
        reflections.value -= id
        decisionsById.value -= id
    }

    /** Items deleted with [delete] (the repository's), in order. */
    val deleted = mutableListOf<Item>()

    /** Makes the next [delete] find the item changed, as if it was changed elsewhere between reading and deleting. */
    var conflictOnNextDelete = false

    /** Compare-and-set, like the real one: deletes an ended item only if it's stored as [current]. */
    override suspend fun delete(current: Item): Boolean {
        require(current.status != ItemStatus.ACTIVE) { "Only ended items can be deleted" }
        if (conflictOnNextDelete) {
            conflictOnNextDelete = false
            return false
        }
        if (items.value.none { it == current }) return false
        deleted += current
        delete(current.id)
        return true
    }

    override suspend fun imageRefs(): Set<String> = items.value.mapNotNullTo(mutableSetOf()) { it.imageRef }

    /** Every recorded decision, in the order recorded. */
    val decisions = mutableListOf<Decision>()

    override fun observeDecisions(id: ItemId): Flow<List<Decision>> = decisionsById.map { it[id].orEmpty() }

    /** Reflections written by [setReflection], in order. */
    val reflectionWrites = mutableListOf<Reflection>()

    /** While set, [setReflection] waits for it, like a slow database. */
    var reflectionGate: CompletableDeferred<Unit>? = null

    /** Like the real one: replaces the answers, false for a missing item. */
    override suspend fun setReflection(id: ItemId, reflection: Reflection): Boolean {
        reflectionGate?.await()
        if (items.value.none { it.id == id }) return false
        reflectionWrites += reflection
        reflections.value += id to reflection
        return true
    }

    /** Makes the next [recordDecision] find the item changed, as if decided from a notification meanwhile. */
    var conflictOnNextDecision = false

    /** Compare-and-set, like the real one: appends the decision and stores [result] only if [current] is unchanged. */
    override suspend fun recordDecision(current: Item, decision: Decision, result: Item): Boolean {
        require(current.status == ItemStatus.ACTIVE && result.id == current.id) { "Only active items can be decided" }
        if (conflictOnNextDecision) {
            conflictOnNextDecision = false
            return false
        }
        if (items.value.none { it == current }) return false
        decisions += decision
        decisionsById.value += current.id to decisionsById.value[current.id].orEmpty() + decision
        replace(result)
        return true
    }
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
