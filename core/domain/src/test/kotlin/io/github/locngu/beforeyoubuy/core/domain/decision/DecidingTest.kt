package io.github.locngu.beforeyoubuy.core.domain.decision

import io.github.locngu.beforeyoubuy.core.domain.item.DecisionType
import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.Reflection
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.ADDED
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.AFTER_COOLING
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.COMPLETE_REFLECTION
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.DURING_COOLING
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.DecisionResult
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.Funding
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.Stage
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.applyDecision
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.isDueForDecision
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.item
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.core.domain.money.MoneyParseResult
import io.github.locngu.beforeyoubuy.core.domain.pool.FunMoney
import io.github.locngu.beforeyoubuy.core.domain.pool.Savings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DecidingTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val locale: Locale = Locale.GERMANY
    private val ready = item()
    private val need = item(ItemKind.NEED)
    private val today: LocalDate = LocalDate.of(2026, 11, 2)

    private val duringCooling: LocalDate = LocalDate.of(2026, 10, 15)

    private fun form(item: Item = ready, on: LocalDate = today) = decisionFormOf(item, on, locale)

    private fun overviewOf(item: Item, reflection: Reflection?, now: Instant) =
        decisionOverviewOf(item, reflection, emptyList(), Savings.NONE, FunMoney.NotSetUp, now, zone)

    private fun other(id: Long, addedAt: Instant = ADDED, slot: Int? = null) =
        item().copy(id = ItemId(id), name = "Item $id", addedAt = addedAt, prioritySlot = slot)

    @Test
    fun `a ready want is bought plainly, a need as a need, anything else early`() {
        assertEquals(
            BuyPath.BUY,
            overviewOf(ready, COMPLETE_REFLECTION, AFTER_COOLING).buyPath
        )
        assertEquals(
            BuyPath.BUY_NEED,
            overviewOf(need, null, DURING_COOLING).buyPath
        )
        assertEquals(
            BuyPath.EARLY,
            overviewOf(ready, COMPLETE_REFLECTION, DURING_COOLING).buyPath
        )
        assertEquals(
            BuyPath.EARLY,
            overviewOf(ready, null, AFTER_COOLING).buyPath
        )
    }

    @Test
    fun `only the plain buy paths offer wait`() {
        assertTrue(BuyPath.BUY.canWait)
        assertTrue(BuyPath.BUY_NEED.canWait)
        assertFalse(BuyPath.EARLY.canWait)
    }

    @Test
    fun `the overview has the days left, how long it's wanted and whether it's reflected`() {
        val overview = overviewOf(ready, null, DURING_COOLING)

        assertEquals(Stage.COOLING_OFF, overview.stage)
        assertEquals(16, overview.daysLeft)
        assertEquals(14, overview.wantedForDays)
        assertFalse(overview.reflected)
        assertNull(overview.recheckOn)
    }

    @Test
    fun `the overview carries what else the money could do`() {
        val priority = other(4, slot = 1).copy(price = Money(24_000, "EUR"))

        val overview = decisionOverviewOf(
            ready,
            COMPLETE_REFLECTION,
            listOf(ready, priority),
            Savings.NONE,
            FunMoney.NotSetUp,
            AFTER_COOLING,
            zone
        )

        assertEquals(
            opportunityCostOf(ready, listOf(priority), Savings.NONE, FunMoney.NotSetUp),
            overview.opportunityCost
        )
        assertEquals(priority, overview.opportunityCost.keepFor()?.priority)
    }

    @Test
    fun `an earlier wait's date shows while it's ahead`() {
        val waited = ready.copy(recheckAt = Instant.parse("2026-11-09T00:00:00Z"))

        assertEquals(
            LocalDate.of(2026, 11, 9),
            overviewOf(waited, COMPLETE_REFLECTION, AFTER_COOLING).recheckOn
        )
    }

    @Test
    fun `the form starts with the exact price, today and the first wait option`() {
        val form = form(ready.copy(price = Money(119_999, "EUR")))

        assertEquals("1199,99", form.price)
        assertEquals(today, form.boughtOn)
        assertEquals(today.plusDays(7), form.waitUntil)
        assertEquals(listOf(7, 14, 30), WAIT_DAYS)
    }

    @Test
    fun `a plain buy can be dated back to the day cooling off ended, others to the day they were added`() {
        assertEquals(LocalDate.of(2026, 10, 31)..today, purchaseDates(ready, BuyPath.BUY, today, zone))
        assertEquals(LocalDate.of(2026, 10, 1)..today, purchaseDates(ready, BuyPath.EARLY, today, zone))
        assertEquals(LocalDate.of(2026, 10, 1)..today, purchaseDates(need, BuyPath.BUY_NEED, today, zone))
    }

    @Test
    fun `an early purchase needs a reason, every purchase a price and a date in range`() {
        assertTrue(checkPurchase(form(), ready, BuyPath.BUY, locale, today, zone).isValid)

        val early = checkPurchase(form().copy(earlyReason = "  "), ready, BuyPath.EARLY, locale, today, zone)
        assertTrue(early.reasonMissing)
        assertFalse(early.isValid)
        assertTrue(checkPurchase(form().copy(earlyReason = "Gift"), ready, BuyPath.EARLY, locale, today, zone).isValid)

        val noPrice = checkPurchase(form().copy(price = ""), ready, BuyPath.BUY, locale, today, zone)
        assertEquals(MoneyParseResult.Empty, noPrice.price)
        assertFalse(noPrice.isValid)

        val tooEarly = form().copy(boughtOn = LocalDate.of(2026, 10, 30))
        assertTrue(checkPurchase(tooEarly, ready, BuyPath.BUY, locale, today, zone).dateOutOfRange)
        val future = form().copy(boughtOn = today.plusDays(1))
        assertTrue(checkPurchase(future, ready, BuyPath.BUY, locale, today, zone).dateOutOfRange)
    }

    @Test
    fun `a purchase today is decided now with the price actually paid`() {
        val decision = purchaseDecision(form().copy(price = "110"), ready, BuyPath.BUY, locale, AFTER_COOLING, zone)

        assertEquals(DecisionType.BUY, decision.type)
        assertEquals(AFTER_COOLING, decision.decidedAt)
        assertEquals(Money(11_000, "EUR"), decision.actualPrice)
        assertNull(decision.earlyReason)
        assertTrue(
            applyDecision(
                ready,
                COMPLETE_REFLECTION,
                Funding(ready.price, required = true),
                decision
            ) is DecisionResult.Applied
        )
    }

    @Test
    fun `a purchase dated back to the day cooling off ended still counts as ready`() {
        val onEndDay = form().copy(boughtOn = LocalDate.of(2026, 10, 31))

        val decision = purchaseDecision(onEndDay, ready, BuyPath.BUY, locale, AFTER_COOLING, zone)

        assertEquals(Instant.parse("2026-10-31T23:59:59.999Z"), decision.decidedAt)
        assertTrue(
            applyDecision(
                ready,
                COMPLETE_REFLECTION,
                Funding(ready.price, required = true),
                decision
            ) is DecisionResult.Applied
        )
    }

    @Test
    fun `an early purchase keeps its trimmed reason and passes domain validation`() {
        val form = form(on = duringCooling).copy(earlyReason = "  Old one broke ")

        val decision = purchaseDecision(form, ready, BuyPath.EARLY, locale, DURING_COOLING, zone)

        assertEquals(DecisionType.BUY_EARLY, decision.type)
        assertEquals("Old one broke", decision.earlyReason)
        assertTrue(
            applyDecision(ready, null, Funding(ready.price, required = true), decision) is DecisionResult.Applied
        )
    }

    @Test
    fun `an early purchase without a reason can't be decided`() {
        assertThrows(IllegalArgumentException::class.java) {
            purchaseDecision(form(on = duringCooling), ready, BuyPath.EARLY, locale, DURING_COOLING, zone)
        }
    }

    @Test
    fun `a need is bought as a need`() {
        val decision = purchaseDecision(form(need, duringCooling), need, BuyPath.BUY_NEED, locale, DURING_COOLING, zone)

        assertEquals(DecisionType.BUY_NEED, decision.type)
        assertTrue(applyDecision(need, null, Funding(need.price, required = true), decision) is DecisionResult.Applied)
    }

    @Test
    fun `wait looks again at the start of the chosen day and hides the item from ready until then`() {
        val decision = waitDecision(form(), AFTER_COOLING, zone)
        val waitUntil = Instant.parse("2026-11-09T00:00:00Z")

        assertEquals(DecisionType.WAIT, decision.type)
        assertEquals(waitUntil, decision.waitUntil)
        val waited = (
            applyDecision(
                ready,
                COMPLETE_REFLECTION,
                Funding(ready.price, required = true),
                decision
            ) as DecisionResult.Applied
            ).item
        assertFalse(
            isDueForDecision(
                waited,
                COMPLETE_REFLECTION,
                Funding(ready.price, required = true),
                waitUntil.minusMillis(1)
            )
        )
        assertTrue(isDueForDecision(waited, COMPLETE_REFLECTION, Funding(ready.price, required = true), waitUntil))
    }

    @Test
    fun `wait needs a day after today`() {
        assertEquals(today.plusDays(1), earliestWaitDate(today))
        assertThrows(IllegalArgumentException::class.java) {
            waitDecision(form().copy(waitUntil = today), AFTER_COOLING, zone)
        }
    }

    @Test
    fun `wait counts calendar days across a daylight saving change`() {
        val berlin = ZoneId.of("Europe/Berlin")
        val now = Instant.parse("2026-10-20T10:00:00Z")

        val decision = waitDecision(form().copy(waitUntil = LocalDate.of(2026, 10, 27)), now, berlin)

        assertEquals(Instant.parse("2026-10-26T23:00:00Z"), decision.waitUntil)
    }

    @Test
    fun `reject keeps a trimmed note, a blank one is none`() {
        assertEquals("Too big", rejectDecision(form().copy(note = " Too big "), AFTER_COOLING).note)
        val plain = rejectDecision(form().copy(note = "  "), AFTER_COOLING)
        assertEquals(DecisionType.REJECT, plain.type)
        assertNull(plain.note)
    }
}
