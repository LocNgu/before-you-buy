package io.github.locngu.beforeyoubuy.core.domain.priorities

import io.github.locngu.beforeyoubuy.core.domain.coolingoff.CoolingOffRules
import io.github.locngu.beforeyoubuy.core.domain.coolingoff.changeKind
import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.ADDED
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.DURING_COOLING
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.item
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.parked
import java.time.Duration
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PrioritiesTest {
    /** A priority whose wait runs from [ADDED], a parked want without [slot], or a need. */
    private fun want(id: Long, slot: Int? = null, kind: ItemKind = ItemKind.WANT): Item {
        val base = if (slot == null && kind == ItemKind.WANT) parked() else item(kind)
        return base.copy(id = ItemId(id), name = "Item $id", prioritySlot = slot, addedAt = ADDED.plusSeconds(id))
    }

    private fun ids(vararg values: Long) = values.map(::ItemId)

    @Test
    fun `slots are 1 to 5, 3 by default, and stored values outside that read as the nearest`() {
        assertEquals(3, DEFAULT_PRIORITY_SLOTS)
        assertEquals(1, clampPrioritySlots(0))
        assertEquals(5, clampPrioritySlots(9))
        assertEquals(4, clampPrioritySlots(4))
    }

    @Test
    fun `priorities are the active wants with a slot, rank 1 first`() {
        val third = want(1, slot = 3)
        val first = want(2, slot = 1)
        val none = want(3)
        val need = want(4, slot = 2, kind = ItemKind.NEED)
        val bought = want(5, slot = 2).copy(status = ItemStatus.PURCHASED)

        assertEquals(listOf(first, third), prioritiesOf(listOf(third, first, none, need, bought)))
    }

    @Test
    fun `only an active want that isn't a priority yet can become one`() {
        assertTrue(want(1).canBePriority)
        assertFalse(want(1, slot = 1).canBePriority)
        assertFalse(want(1, kind = ItemKind.NEED).canBePriority)
        assertFalse(want(1).copy(status = ItemStatus.REJECTED).canBePriority)
    }

    @Test
    fun `with a free slot, make priority takes the lowest free rank`() {
        val active = listOf(want(1, slot = 1), want(2, slot = 2), want(3))

        assertEquals(Promotion.Promoted(3, ids(1, 2, 3)), promote(active[2], active, slots = 3))
        assertEquals(Promotion.Promoted(1, ids(3)), promote(want(3), listOf(want(3)), slots = 1))
    }

    @Test
    fun `with full slots, make priority always starts a head-to-head against the lowest-ranked`() {
        val active = listOf(want(1, slot = 1), want(2, slot = 2), want(3, slot = 3), want(4))

        val promotion = promote(active[3], active, slots = 3)

        val headToHead = (promotion as Promotion.Compare).headToHead
        assertEquals(active[2], headToHead.opponent)
        assertEquals(3, headToHead.opponentRank)
        assertEquals(3, headToHead.comparisonsLeft)
        assertEquals(null, headToHead.outcome)
    }

    @Test
    fun `needs, ended items and priorities can't be promoted`() {
        val active = listOf(want(1, slot = 1))

        assertEquals(Promotion.NotEligible, promote(want(2, kind = ItemKind.NEED), active, slots = 3))
        assertEquals(Promotion.NotEligible, promote(want(2).copy(status = ItemStatus.PURCHASED), active, slots = 3))
        assertEquals(Promotion.NotEligible, promote(active[0], active, slots = 3))
    }

    @Test
    fun `reducing the slots demotes the lowest-ranked extras, more slots demote nobody`() {
        val active = listOf(want(1, slot = 1), want(2, slot = 2), want(3, slot = 3), want(4))

        assertEquals(listOf(active[1], active[2]), demotedBySlots(active, 1))
        assertEquals(emptyList<Item>(), demotedBySlots(active, 3))
        assertEquals(emptyList<Item>(), demotedBySlots(active, 5))
        assertThrows(IllegalArgumentException::class.java) { demotedBySlots(active, 0) }
        assertThrows(IllegalArgumentException::class.java) { demotedBySlots(active, 6) }
    }

    @Test
    fun `when a priority leaves, the ones below move up and keep their order`() {
        // #2 was bought: its slot is cleared by the decision, #3 and #4 are left with a gap.
        val active = listOf(want(1, slot = 1), want(3, slot = 3), want(4, slot = 4), want(5))

        assertEquals(mapOf(ItemId(3) to 2, ItemId(4) to 3), compactedSlots(active))
    }

    @Test
    fun `compacting ranks gaps at the top too and takes the slot off a need`() {
        val active = listOf(want(1, slot = 2), want(2, slot = 3, kind = ItemKind.NEED))

        assertEquals(mapOf(ItemId(1) to 1, ItemId(2) to null), compactedSlots(active))
        assertEquals(emptyMap<ItemId, Int?>(), compactedSlots(listOf(want(1, slot = 1), want(2, slot = 2))))
    }

    @Test
    fun `remove from priorities keeps the others in order, and the changes are only the slots that move`() {
        val active = listOf(want(1, slot = 1), want(2, slot = 2), want(3, slot = 3))

        val ranking = rankingWithout(active, ItemId(1))

        assertEquals(ids(2, 3), ranking)
        assertEquals(mapOf(ItemId(1) to null, ItemId(2) to 1, ItemId(3) to 2), slotChanges(active, ranking))
        assertThrows(IllegalArgumentException::class.java) { slotChanges(active, ids(1, 1)) }
    }

    @Test
    fun `a want that gets a slot starts waiting, one that loses it pauses, one that moves up keeps waiting`() {
        val first = want(1, slot = 1)
        val second = want(2, slot = 2)
        val challenger = want(3).copy(waitedDays = 5)
        val active = listOf(first, second, challenger)
        val at = DURING_COOLING // 14 days after ADDED

        val written = withSlots(active, slotChanges(active, ids(3, 1)), at, ZoneOffset.UTC).associateBy { it.id }

        assertEquals(setOf(ItemId(1), ItemId(2), ItemId(3)), written.keys)
        val promoted = written.getValue(ItemId(3))
        assertEquals(1, promoted.prioritySlot)
        assertEquals(at, promoted.waitingSince)
        assertEquals(at.plus(Duration.ofDays(25)), promoted.coolingEndsAt) // 30 days, 5 already waited
        val moved = written.getValue(ItemId(1))
        assertEquals(2, moved.prioritySlot)
        assertEquals(first.coolingEndsAt, moved.coolingEndsAt)
        val demoted = written.getValue(ItemId(2))
        assertNull(demoted.prioritySlot)
        assertNull(demoted.coolingEndsAt)
        assertEquals(14, demoted.waitedDays)
    }

    @Test
    fun `fewer slots pause the waits of the priorities that go back`() {
        val active = listOf(want(1, slot = 1), want(2, slot = 2), want(3, slot = 3))
        val changes = demotedBySlots(active, 1).associate { it.id to null }

        val written = withSlots(active, changes, DURING_COOLING, ZoneOffset.UTC)

        assertEquals(ids(2, 3), written.map { it.id })
        assertTrue(written.all { it.prioritySlot == null && it.waitingSince == null && it.waitedDays == 14 })
    }

    @Test
    fun `marking a priority as a need takes it out of the priorities`() {
        val rules = CoolingOffRules.defaults("EUR")

        val need = changeKind(want(1, slot = 2), ItemKind.NEED, DURING_COOLING, rules, ZoneOffset.UTC)

        assertNull(need.prioritySlot)
    }
}
