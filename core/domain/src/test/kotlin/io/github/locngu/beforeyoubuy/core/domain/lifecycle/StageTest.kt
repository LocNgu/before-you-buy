package io.github.locngu.beforeyoubuy.core.domain.lifecycle

import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.item.Reflection
import io.github.locngu.beforeyoubuy.core.domain.item.UsageFrequency
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StageTest {
    // Brief §5 stage table, one test per row; earlier unmet conditions win over later ones.

    @Test
    fun `cooling off while the timer runs, whatever else is true`() {
        assertEquals(Stage.COOLING_OFF, stageOf(item(), null, UNFUNDED, DURING_COOLING))
        assertEquals(Stage.COOLING_OFF, stageOf(item(), COMPLETE_REFLECTION, FUNDED, DURING_COOLING))
    }

    @Test
    fun `needs reflection once cooled off without the required answers, funded or not`() {
        assertEquals(Stage.NEEDS_REFLECTION, stageOf(item(), null, UNFUNDED, AFTER_COOLING))
        assertEquals(Stage.NEEDS_REFLECTION, stageOf(item(), Reflection(), FUNDED, AFTER_COOLING))
    }

    @Test
    fun `saving once cooled off and reflected but not funded`() {
        assertEquals(Stage.SAVING, stageOf(item(), COMPLETE_REFLECTION, UNFUNDED, AFTER_COOLING))
    }

    @Test
    fun `ready once cooled off, reflected and funded`() {
        assertEquals(Stage.READY, stageOf(item(), COMPLETE_REFLECTION, FUNDED, AFTER_COOLING))
    }

    @Test
    fun `cooling off ends exactly at coolingEndsAt`() {
        assertFalse(isCooledOff(item(), COOLING_ENDS.minusMillis(1)))
        assertTrue(isCooledOff(item(), COOLING_ENDS))
    }

    @Test
    fun `reflection needs a non-blank why, a usage frequency and a non-blank reason against`() {
        assertFalse(isReflected(item(), null))
        assertTrue(isReflected(item(), COMPLETE_REFLECTION))
        assertFalse(isReflected(item(), COMPLETE_REFLECTION.copy(why = null)))
        assertFalse(isReflected(item(), COMPLETE_REFLECTION.copy(why = "  ")))
        assertFalse(isReflected(item(), COMPLETE_REFLECTION.copy(usageFrequency = null)))
        assertFalse(isReflected(item(), COMPLETE_REFLECTION.copy(reasonsAgainst = emptyList())))
        assertFalse(isReflected(item(), COMPLETE_REFLECTION.copy(reasonsAgainst = listOf("", " "))))
        assertTrue(isReflected(item(), COMPLETE_REFLECTION.copy(reasonsAgainst = listOf("", "Too heavy"))))
    }

    @Test
    fun `optional answers don't count towards reflected`() {
        val onlyOptional = Reflection(problemSolved = "Blurry photos", whyNow = "Holiday next month")
        assertFalse(isReflected(item(), onlyOptional))
        assertTrue(isReflected(item(), COMPLETE_REFLECTION.copy(usageFrequency = UsageFrequency.ONCE)))
    }

    @Test
    fun `funded when the allocation covers the price`() {
        assertFalse(isFunded(item(), UNFUNDED))
        assertTrue(isFunded(item(), FUNDED))
        assertTrue(isFunded(item(), Money(50_000, "EUR")))
    }

    @Test
    fun `a need skips cooling off and reflection but not funding`() {
        val need = item(ItemKind.NEED)
        assertTrue(isCooledOff(need, ADDED))
        assertTrue(isReflected(need, null))
        assertEquals(Stage.READY, stageOf(need, null, FUNDED, ADDED))
        assertEquals(Stage.SAVING, stageOf(need, null, UNFUNDED, ADDED))
    }

    @Test
    fun `purchased and rejected items have no stage`() {
        for (status in listOf(ItemStatus.PURCHASED, ItemStatus.REJECTED)) {
            assertThrows(IllegalArgumentException::class.java) {
                stageOf(item().copy(status = status), COMPLETE_REFLECTION, FUNDED, AFTER_COOLING)
            }
        }
    }

    @Test
    fun `due for a decision when ready and any re-check date has come`() {
        assertTrue(isDueForDecision(item(), COMPLETE_REFLECTION, FUNDED, AFTER_COOLING))

        val waiting = item().copy(recheckAt = AFTER_COOLING.plusSeconds(60))
        assertFalse(isDueForDecision(waiting, COMPLETE_REFLECTION, FUNDED, AFTER_COOLING))
        assertTrue(isDueForDecision(waiting, COMPLETE_REFLECTION, FUNDED, AFTER_COOLING.plusSeconds(60)))
    }

    @Test
    fun `not due before ready, or once purchased or rejected`() {
        assertFalse(isDueForDecision(item(), COMPLETE_REFLECTION, FUNDED, DURING_COOLING))
        assertFalse(isDueForDecision(item(), null, FUNDED, AFTER_COOLING))
        assertFalse(isDueForDecision(item(), COMPLETE_REFLECTION, UNFUNDED, AFTER_COOLING))
        for (status in listOf(ItemStatus.PURCHASED, ItemStatus.REJECTED)) {
            assertFalse(isDueForDecision(item().copy(status = status), COMPLETE_REFLECTION, FUNDED, AFTER_COOLING))
        }
    }

    @Test
    fun `a funded need is due for a decision right away`() {
        assertTrue(isDueForDecision(item(ItemKind.NEED), null, FUNDED, ADDED))
    }

    @Test
    fun `savings in another currency throw in every stage`() {
        val dollars = Money(12_000, "USD")
        assertThrows(IllegalArgumentException::class.java) { stageOf(item(), null, dollars, DURING_COOLING) }
        assertThrows(IllegalArgumentException::class.java) { isDueForDecision(item(), null, dollars, DURING_COOLING) }
    }

    @Test
    fun `an item can't have a negative price or a priority slot below 1`() {
        assertThrows(IllegalArgumentException::class.java) { item().copy(price = Money(-1, "EUR")) }
        assertThrows(IllegalArgumentException::class.java) { item().copy(prioritySlot = 0) }
    }
}
