package io.github.locngu.beforeyoubuy.core.domain.priorities

import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId

/**
 * "Which would you rather have?" (brief §4.7, ADR-0011): a [challenger] earns a place among full [priorities]
 * (rank 1 first) one comparison at a time, starting from the lowest-ranked. Each win moves it up to the next one; the
 * first loss, or a win against #1, ends it. Pure and deterministic: the same choices always give the same [outcome].
 *
 * @property wins comparisons the challenger has won so far.
 * @property lost the challenger lost the last comparison, so it's over.
 */
data class HeadToHead(
    val challenger: Item,
    val priorities: List<Item>,
    val slots: Int,
    val wins: Int = 0,
    val lost: Boolean = false
) {
    init {
        require(priorities.isNotEmpty()) { "A head-to-head needs at least one priority" }
        require(priorities.none { it.id == challenger.id }) { "${challenger.id} can't challenge itself" }
        require(wins in 0..priorities.size) { "$wins wins against ${priorities.size} priorities" }
    }

    /** No comparison is left: the challenger lost one or beat every priority. */
    val finished: Boolean get() = lost || wins == priorities.size

    /** The priority the challenger faces now, the lowest-ranked one it hasn't beaten; null once [finished]. */
    val opponent: Item? get() = if (finished) null else priorities[priorities.size - 1 - wins]

    /** The rank of [opponent] (1-based); null once [finished]. */
    val opponentRank: Int? get() = if (finished) null else priorities.size - wins

    /** The most comparisons this can still take, the current one included. */
    val comparisonsLeft: Int get() = if (finished) 0 else priorities.size - wins

    /**
     * The answer to the current comparison: [winner] is the [challenger] or the [opponent]. Anything else, or a
     * choice after it's [finished], throws: the screen offers exactly those two.
     */
    fun choose(winner: ItemId): HeadToHead {
        val opponent = checkNotNull(opponent) { "The head-to-head is over" }
        return when (winner) {
            challenger.id -> copy(wins = wins + 1)
            opponent.id -> copy(lost = true)
            else -> throw IllegalArgumentException("$winner isn't in this comparison")
        }
    }

    /** What changes, once [finished]; null before. */
    val outcome: HeadToHeadOutcome?
        get() {
            if (!finished) return null
            val before = priorities.map { it.id }
            if (wins == 0) return HeadToHeadOutcome(before, before, linesOf(before))
            val ranked = priorities.toMutableList<Item>().apply { add(priorities.size - wins, challenger) }
            // The challenger takes its place; whatever no longer fits the slots goes back to the wishlist.
            val after = ranked.take(slots).map { it.id }
            return HeadToHeadOutcome(before, after, linesOf(after))
        }

    /**
     * One line per item, by its new rank, then what moved back, then the challenger if it stays on the wishlist:
     * "Camera stays #1, 3D printer is now #2, Bike moved back to the wishlist".
     */
    private fun linesOf(after: List<ItemId>): List<OutcomeLine> {
        val all = priorities + challenger
        val rankBefore = priorities.withIndex().associate { (index, item) -> item.id to index + 1 }
        val rankAfter = after.withIndex().associate { (index, id) -> id to index + 1 }
        return all
            .map { OutcomeLine(it, rankBefore[it.id], rankAfter[it.id]) }
            .sortedWith(
                compareBy<OutcomeLine, Int?>(nullsLast()) { it.after }
                    .thenBy { it.item.id == challenger.id }
                    .thenBy { it.before }
            )
    }

    companion object {
        /** A head-to-head for [challenger] against the full [priorities] (rank 1 first) with [slots] slots. */
        fun start(challenger: Item, priorities: List<Item>, slots: Int): HeadToHead =
            HeadToHead(challenger, priorities, slots)
    }
}

/**
 * The result of a [HeadToHead]: the priorities [before] and [after] (ids, rank 1 first) and a [lines] summary. The
 * lowest priority goes back to the wishlist only if the challenger won at least once; otherwise nothing changes.
 */
data class HeadToHeadOutcome(val before: List<ItemId>, val after: List<ItemId>, val lines: List<OutcomeLine>) {
    /** Whether anything is stored: false when the challenger lost the first comparison. */
    val changed: Boolean get() = before != after
}

/**
 * How one item came out of a head-to-head: its rank [before] and [after] (null = not a priority).
 *
 * - `before == after`: stays #n (or, both null, the challenger stays on the wishlist)
 * - `after == null`: moved back to the wishlist
 * - otherwise: is now #n
 */
data class OutcomeLine(val item: Item, val before: Int?, val after: Int?)
