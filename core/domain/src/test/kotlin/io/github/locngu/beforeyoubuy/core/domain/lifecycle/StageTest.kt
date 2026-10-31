package io.github.locngu.beforeyoubuy.core.domain.lifecycle

import io.github.locngu.beforeyoubuy.core.domain.coolingoff.startWait
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.item.Reflection
import io.github.locngu.beforeyoubuy.core.domain.item.UsageFrequency
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StageTest {
    // Brief §5 stage table, one test per row; earlier unmet conditions win over later ones.

    @Test
    fun `parked wins over every other stage, however long the want has waited`() {
        assertEquals(Stage.PARKED, stageOf(parked(), null, UNFUNDED, DURING_COOLING))
        assertEquals(Stage.PARKED, stageOf(parked(waitedDays = 40), COMPLETE_REFLECTION, FUNDED, AFTER_COOLING))
        assertTrue(isCooledOff(parked(waitedDays = 30), DURING_COOLING))
        assertFalse(isCooledOff(parked(waitedDays = 29), AFTER_COOLING))
    }

    @Test
    fun `a want promoted after waiting its full period is ready at once`() {
        val promoted = startWait(parked(waitedDays = 30).copy(prioritySlot = 1), AFTER_COOLING, ZoneOffset.UTC)

        assertEquals(Stage.READY, stageOf(promoted, COMPLETE_REFLECTION, FUNDED, AFTER_COOLING))
        assertEquals(Stage.NEEDS_REFLECTION, stageOf(promoted, null, FUNDED, AFTER_COOLING))
    }

    @Test
    fun `a need is never parked, with or without a slot`() {
        assertFalse(item(ItemKind.NEED).isParked)
        assertFalse(parked().copy(status = ItemStatus.PURCHASED).isParked)
        assertEquals(Stage.READY, stageOf(item(ItemKind.NEED), null, UNFUNDED, DURING_COOLING))
    }

    @Test
    fun `a parked want is never due for a decision`() {
        assertFalse(isDueForDecision(parked(waitedDays = 40), COMPLETE_REFLECTION, FUNDED, AFTER_COOLING))
    }

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
        assertTrue(isFunded(item(), Funding(Money(50_000, "EUR"), required = true)))
    }

    @Test
    fun `a need skips cooling off, reflection and funding`() {
        val need = item(ItemKind.NEED)
        assertTrue(isCooledOff(need, ADDED))
        assertTrue(isReflected(need, null))
        // Needs never use fun money (ADR-0044): nothing set aside still counts as funded.
        assertTrue(isFunded(need, Funding(Money(0, "EUR"), required = true)))
        assertEquals(Stage.READY, stageOf(need, null, FUNDED, ADDED))
        assertEquals(Stage.READY, stageOf(need, null, UNFUNDED, ADDED))
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
        assertThrows(IllegalArgumentException::class.java) {
            stageOf(item(), null, Funding(dollars, required = true), DURING_COOLING)
        }
        assertThrows(IllegalArgumentException::class.java) {
            isDueForDecision(item(), null, Funding(dollars, required = true), DURING_COOLING)
        }
    }

    @Test
    fun `an item can't have a negative price or a priority slot below 1`() {
        assertThrows(IllegalArgumentException::class.java) { item().copy(price = Money(-1, "EUR")) }
        assertThrows(IllegalArgumentException::class.java) { item().copy(prioritySlot = 0) }
    }

    @Test
    fun `a running wait has both a start and an end, the end not before the start, and days aren't negative`() {
        assertThrows(IllegalArgumentException::class.java) { item().copy(waitingSince = null) }
        assertThrows(IllegalArgumentException::class.java) { parked().copy(coolingEndsAt = COOLING_ENDS) }
        assertThrows(IllegalArgumentException::class.java) { item().copy(coolingEndsAt = ADDED.minusSeconds(1)) }
        assertThrows(IllegalArgumentException::class.java) { item().copy(waitedDays = -1) }
        assertThrows(IllegalArgumentException::class.java) { item().copy(coolingOffDays = -1) }
    }
}
