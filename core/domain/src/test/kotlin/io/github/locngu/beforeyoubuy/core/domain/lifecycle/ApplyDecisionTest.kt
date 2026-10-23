package io.github.locngu.beforeyoubuy.core.domain.lifecycle

import io.github.locngu.beforeyoubuy.core.domain.item.Decision
import io.github.locngu.beforeyoubuy.core.domain.item.DecisionSource
import io.github.locngu.beforeyoubuy.core.domain.item.DecisionType
import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.item.Reflection
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ApplyDecisionTest {
    private val ready = item().copy(prioritySlot = 2)
    private val need = item(ItemKind.NEED)
    private val paid = Money(11_000, "EUR")

    /** A decision of [type]; purchases carry the price actually paid. */
    private fun decision(
        type: DecisionType,
        at: Instant = AFTER_COOLING,
        earlyReason: String? = null,
        waitUntil: Instant? = null,
        source: DecisionSource = DecisionSource.APP
    ) = Decision(
        type,
        at,
        source,
        actualPrice = paid.takeIf { type.isPurchase },
        earlyReason = earlyReason,
        waitUntil = waitUntil
    )

    private fun decide(
        item: Item,
        type: DecisionType,
        at: Instant = AFTER_COOLING,
        earlyReason: String? = null,
        waitUntil: Instant? = null,
        funding: Funding = FUNDED,
        reflection: Reflection? = COMPLETE_REFLECTION
    ) = applyDecision(item, reflection, funding, decision(type, at, earlyReason, waitUntil))

    private fun applied(result: DecisionResult): Item = (result as DecisionResult.Applied).item

    @Test
    fun `buy is allowed for a ready want and ends it as purchased`() {
        val bought = applied(decide(ready.copy(recheckAt = DURING_COOLING), DecisionType.BUY))
        assertEquals(ready.copy(status = ItemStatus.PURCHASED, recheckAt = null, prioritySlot = null), bought)
    }

    @Test
    fun `buy is refused for a want that isn't ready`() {
        assertEquals(DecisionResult.NotReady, decide(ready, DecisionType.BUY, at = DURING_COOLING))
        assertEquals(DecisionResult.NotReady, decide(ready, DecisionType.BUY, reflection = null))
        assertEquals(DecisionResult.NotReady, decide(ready, DecisionType.BUY, funding = UNFUNDED))
    }

    @Test
    fun `buying early needs a non-blank reason`() {
        val bought = applied(decide(ready, DecisionType.BUY_EARLY, at = DURING_COOLING, earlyReason = "On sale"))
        assertEquals(ItemStatus.PURCHASED, bought.status)

        assertEquals(DecisionResult.MissingEarlyReason, decide(ready, DecisionType.BUY_EARLY, at = DURING_COOLING))
        assertEquals(
            DecisionResult.MissingEarlyReason,
            decide(ready, DecisionType.BUY_EARLY, at = DURING_COOLING, earlyReason = " ")
        )
    }

    @Test
    fun `buying early is allowed in every stage of a want`() {
        val early = ready.copy(status = ItemStatus.PURCHASED, prioritySlot = null)
        assertEquals(early, applied(decide(ready, DecisionType.BUY_EARLY, reflection = null, earlyReason = "On sale")))
        assertEquals(
            early,
            applied(decide(ready, DecisionType.BUY_EARLY, funding = UNFUNDED, earlyReason = "On sale"))
        )
        // Confirmed just as cooling off ended: the friction was already gone through, so it still counts.
        assertEquals(early, applied(decide(ready, DecisionType.BUY_EARLY, at = COOLING_ENDS, earlyReason = "On sale")))
    }

    @Test
    fun `a need is bought with BUY_NEED in any stage, and only a need`() {
        assertEquals(ItemStatus.PURCHASED, applied(decide(need, DecisionType.BUY_NEED, at = ADDED)).status)
        assertEquals(
            ItemStatus.PURCHASED,
            applied(decide(need, DecisionType.BUY_NEED, at = ADDED, funding = UNFUNDED)).status
        )

        assertEquals(DecisionResult.NotNeed, decide(ready, DecisionType.BUY_NEED))
        assertEquals(DecisionResult.IsNeed, decide(need, DecisionType.BUY))
        assertEquals(DecisionResult.IsNeed, decide(need, DecisionType.BUY_EARLY, earlyReason = "Broke"))
    }

    @Test
    fun `wait keeps the item active and sets the re-check date`() {
        val recheck = AFTER_COOLING.plusSeconds(60)
        val waiting = applied(decide(ready, DecisionType.WAIT, waitUntil = recheck))
        assertEquals(ready.copy(recheckAt = recheck), waiting)

        val stillCooling = applied(decide(ready, DecisionType.WAIT, at = DURING_COOLING, waitUntil = recheck))
        assertEquals(recheck, stillCooling.recheckAt)

        assertEquals(need.copy(recheckAt = recheck), applied(decide(need, DecisionType.WAIT, waitUntil = recheck)))
    }

    @Test
    fun `wait needs a date after the decision`() {
        assertEquals(DecisionResult.WaitNotInFuture, decide(ready, DecisionType.WAIT))
        assertEquals(DecisionResult.WaitNotInFuture, decide(ready, DecisionType.WAIT, waitUntil = AFTER_COOLING))
        assertEquals(
            DecisionResult.WaitNotInFuture,
            decide(ready, DecisionType.WAIT, waitUntil = AFTER_COOLING.minusSeconds(1))
        )
    }

    @Test
    fun `reject is allowed in every stage and for needs`() {
        val expected = ready.copy(status = ItemStatus.REJECTED, prioritySlot = null)
        assertEquals(expected, applied(decide(ready, DecisionType.REJECT, at = DURING_COOLING)))
        assertEquals(expected, applied(decide(ready, DecisionType.REJECT, reflection = null)))
        assertEquals(expected, applied(decide(ready, DecisionType.REJECT, funding = UNFUNDED)))
        assertEquals(expected, applied(decide(ready, DecisionType.REJECT)))
        assertEquals(ItemStatus.REJECTED, applied(decide(need, DecisionType.REJECT)).status)

        val letItGo = decision(DecisionType.REJECT, source = DecisionSource.NOTIFICATION)
        assertEquals(expected, applied(applyDecision(ready, COMPLETE_REFLECTION, FUNDED, letItGo)))
    }

    @Test
    fun `purchased and rejected items can't be decided again`() {
        for (status in listOf(ItemStatus.PURCHASED, ItemStatus.REJECTED)) {
            val ended = ready.copy(status = status)
            for (type in DecisionType.entries) {
                val result = decide(
                    ended,
                    type,
                    earlyReason = "On sale".takeIf { type == DecisionType.BUY_EARLY },
                    waitUntil = AFTER_COOLING.plusSeconds(60).takeIf { type == DecisionType.WAIT }
                )
                assertEquals("$type on $status", DecisionResult.NotActive, result)
            }
        }
    }

    @Test
    fun `a purchase needs an actual price in the item's currency`() {
        assertThrows(IllegalArgumentException::class.java) { Decision(DecisionType.BUY, AFTER_COOLING) }
        assertThrows(IllegalArgumentException::class.java) {
            Decision(DecisionType.BUY, AFTER_COOLING, actualPrice = Money(-1, "EUR"))
        }
        val inDollars = Decision(DecisionType.BUY, AFTER_COOLING, actualPrice = Money(11_000, "USD"))
        assertThrows(IllegalArgumentException::class.java) {
            applyDecision(ready, COMPLETE_REFLECTION, FUNDED, inDollars)
        }
    }

    @Test
    fun `savings in another currency throw for every decision`() {
        val dollars = Money(12_000, "USD")
        for (type in listOf(DecisionType.REJECT, DecisionType.BUY_NEED)) {
            assertThrows(IllegalArgumentException::class.java) {
                decide(need, type, funding = Funding(dollars, required = true))
            }
        }
    }

    @Test
    fun `a decision only carries the fields of its type`() {
        assertThrows(IllegalArgumentException::class.java) {
            Decision(DecisionType.WAIT, AFTER_COOLING, actualPrice = paid, waitUntil = AFTER_COOLING.plusSeconds(60))
        }
        assertThrows(IllegalArgumentException::class.java) {
            Decision(DecisionType.BUY, AFTER_COOLING, actualPrice = paid, earlyReason = "On sale")
        }
        assertThrows(IllegalArgumentException::class.java) {
            Decision(DecisionType.REJECT, AFTER_COOLING, waitUntil = AFTER_COOLING.plusSeconds(60))
        }
        assertThrows(IllegalArgumentException::class.java) {
            Decision(DecisionType.WAIT, AFTER_COOLING, waitUntil = AFTER_COOLING.plusSeconds(60), note = "Not now")
        }
    }

    @Test
    fun `a reject keeps its note`() {
        val decision = Decision(DecisionType.REJECT, AFTER_COOLING, note = "My phone is enough")

        assertEquals(
            ready.copy(status = ItemStatus.REJECTED, prioritySlot = null),
            applied(applyDecision(ready, COMPLETE_REFLECTION, FUNDED, decision))
        )
        assertEquals("My phone is enough", decision.note)
    }
}
