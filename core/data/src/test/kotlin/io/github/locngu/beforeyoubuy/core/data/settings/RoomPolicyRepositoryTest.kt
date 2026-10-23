package io.github.locngu.beforeyoubuy.core.data.settings

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import io.github.locngu.beforeyoubuy.core.data.db.BeforeYouBuyDatabase
import io.github.locngu.beforeyoubuy.core.data.db.DatabaseTest
import io.github.locngu.beforeyoubuy.core.data.db.withPolicyRow
import io.github.locngu.beforeyoubuy.core.data.item.FULL_REFLECTION
import io.github.locngu.beforeyoubuy.core.data.item.RoomItemRepository
import io.github.locngu.beforeyoubuy.core.data.item.buy
import io.github.locngu.beforeyoubuy.core.data.item.reject
import io.github.locngu.beforeyoubuy.core.data.pool.RoomPoolRepository
import io.github.locngu.beforeyoubuy.core.data.testing.assertThrowsIllegalArgument
import io.github.locngu.beforeyoubuy.core.domain.coolingoff.CoolingOffRules
import io.github.locngu.beforeyoubuy.core.domain.coolingoff.CoolingOffRulesResult
import io.github.locngu.beforeyoubuy.core.domain.coolingoff.CoolingOffTier
import io.github.locngu.beforeyoubuy.core.domain.coolingoff.initialCoolingEnd
import io.github.locngu.beforeyoubuy.core.domain.item.Decision
import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.DecisionResult
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.applyDecision
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.core.domain.settings.Policy
import java.time.Instant
import java.time.Period
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

internal class RoomPolicyRepositoryTest : DatabaseTest() {
    private lateinit var policies: RoomPolicyRepository
    private lateinit var items: RoomItemRepository

    private val zone: ZoneId = ZoneOffset.UTC
    private val changedAt: Instant = Instant.parse("2026-10-10T12:00:00Z")

    @Before
    fun createRepositories() {
        policies = RoomPolicyRepository(database)
        items = RoomItemRepository(database)
    }

    /** A want or need added at [addedAt], its end set by the default tiers like quick add would. */
    private suspend fun add(
        name: String,
        addedAt: String,
        price: Money = Money(12_000, "EUR"),
        kind: ItemKind = ItemKind.WANT
    ): Item {
        val added = Instant.parse(addedAt)
        val end = initialCoolingEnd(added, price, kind, CoolingOffRules.defaults(price.currency), zone)
        val id = items.add(Item(ItemId.NEW, name, price, added, end, kind))
        return items.observeById(id).first()!!
    }

    private suspend fun stored(item: Item): Item = items.observeById(item.id).first()!!

    private suspend fun decide(item: Item, decision: Decision) {
        val applied = applyDecision(item, FULL_REFLECTION, item.price, decision) as DecisionResult.Applied
        items.recordDecision(item, decision, applied.item)
    }

    @Test
    fun `a new database starts with its default currency and the default tiers`() = runTest {
        assertEquals(Policy("EUR", CoolingOffRules.defaults("EUR")), policies.policy.first())
    }

    @Test
    fun `the currency is fixed on first start and doesn't follow the region afterwards`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        fun open(region: String) = Room.databaseBuilder(context, BeforeYouBuyDatabase::class.java, "policy-test.db")
            .withPolicyRow { region }.allowMainThreadQueries().build()
        try {
            open("CHF").apply { assertEquals("CHF", RoomPolicyRepository(this).policy.first().currency) }.close()

            open("USD").apply { assertEquals("CHF", RoomPolicyRepository(this).policy.first().currency) }.close()
        } finally {
            context.deleteDatabase("policy-test.db")
        }
    }

    @Test
    fun `shorter tiers move waiting wants only, in the same transaction as the tiers`() = runTest {
        val waiting = add("Camera", "2026-10-01T10:00:00Z") // 30 days: until 31 Oct
        val cooled = add("Lamp", "2026-08-01T10:00:00Z") // until 31 Aug, over before the change
        val need = add("Fridge", "2026-10-01T10:00:00Z", kind = ItemKind.NEED)
        val ended = add("Bike", "2026-10-01T10:00:00Z")
        decide(ended, reject(Instant.parse("2026-10-05T10:00:00Z")))
        val threeDays = rules("EUR", 0L to 3)

        policies.changeCoolingOffRules(threeDays, changedAt, zone)

        assertEquals(threeDays, policies.policy.first().coolingOffRules)
        assertEquals(Instant.parse("2026-10-04T10:00:00Z"), stored(waiting).coolingEndsAt) // already passed: cooled off
        assertEquals(cooled.coolingEndsAt, stored(cooled).coolingEndsAt)
        assertEquals(need.coolingEndsAt, stored(need).coolingEndsAt)
        assertEquals(ended.coolingEndsAt, stored(ended).coolingEndsAt)
    }

    @Test
    fun `longer tiers extend waiting wants`() = runTest {
        val waiting = add("Camera", "2026-10-01T10:00:00Z")

        policies.changeCoolingOffRules(rules("EUR", 0L to 60), changedAt, zone)

        assertEquals(Instant.parse("2026-11-30T10:00:00Z"), stored(waiting).coolingEndsAt)
    }

    @Test
    fun `tiers in another currency are refused and change nothing`() = runTest {
        val waiting = add("Camera", "2026-10-01T10:00:00Z")

        assertThrowsIllegalArgument { policies.changeCoolingOffRules(rules("USD", 0L to 3), changedAt, zone) }

        assertEquals(CoolingOffRules.defaults("EUR"), policies.policy.first().coolingOffRules)
        assertEquals(waiting, stored(waiting))
    }

    @Test
    fun `a tier change is atomic - a failed item update keeps the old tiers`() = runTest {
        val waiting = add("Camera", "2026-10-01T10:00:00Z")
        execSql("CREATE TRIGGER fail_item_update BEFORE UPDATE ON items BEGIN SELECT RAISE(ABORT, 'boom'); END")

        val failure = runCatching { policies.changeCoolingOffRules(rules("EUR", 0L to 3), changedAt, zone) }

        assertNotNull(failure.exceptionOrNull())
        assertEquals(CoolingOffRules.defaults("EUR"), policies.policy.first().coolingOffRules)
        assertEquals(waiting, stored(waiting))
    }

    @Test
    fun `a currency change relabels every amount and keeps the numbers`() = runTest {
        val waiting = add("Camera", "2026-10-01T10:00:00Z")
        val bought = add("Bike", "2026-10-01T10:00:00Z")
        val decision = buy(Instant.parse("2026-11-02T09:30:00Z")) // paid 115,00 €
        decide(bought, decision)
        val custom = rules("EUR", 0L to 3, 5_000L to 14)
        policies.changeCoolingOffRules(custom, changedAt, zone)
        val endBefore = stored(waiting).coolingEndsAt

        policies.changeCurrency("USD")

        assertEquals(Policy("USD", rules("USD", 0L to 3, 5_000L to 14)), policies.policy.first())
        assertEquals(Money(12_000, "USD"), stored(waiting).price)
        assertEquals(Money(12_000, "USD"), stored(bought).price)
        assertEquals(Money(11_500, "USD"), items.observeDecisions(bought.id).first().single().actualPrice)
        assertEquals(endBefore, stored(waiting).coolingEndsAt)
    }

    @Test
    fun `a currency change relabels the fun money too`() = runTest {
        val pool = RoomPoolRepository(database)
        val setUpAt = Instant.parse("2026-10-01T10:00:00Z")
        pool.setUp(Money(30_000, "EUR"), 1, Money(5_050, "EUR"), setUpAt, zone)
        pool.adjust(Money(-1_025, "EUR"), "Cash", setUpAt)

        policies.changeCurrency("JPY")

        assertEquals(Money(300, "JPY"), pool.allowance.first()?.monthlyAmount)
        // 50,50 € → 51 ¥, 300,00 € → 300 ¥, −10,25 € → −10 ¥.
        assertEquals(
            listOf(Money(51, "JPY"), Money(300, "JPY"), Money(-10, "JPY")),
            pool.entries.first().map { it.change }
        )
        // Accruals keep working in the new currency.
        pool.change(Money(500, "JPY"), 1, Money(51, "JPY"), setUpAt, zone)
    }

    @Test
    fun `a currency change rescales minor units, and default tiers stay the defaults`() = runTest {
        val item = add("Camera", "2026-10-01T10:00:00Z", price = Money(12_350, "EUR"))

        policies.changeCurrency("JPY")

        assertEquals(Money(124, "JPY"), stored(item).price) // 123,50 € → 124 ¥
        assertEquals(Policy("JPY", CoolingOffRules.defaults("JPY")), policies.policy.first())
    }

    @Test
    fun `the current currency changes nothing and an unusable one is refused`() = runTest {
        val item = add("Camera", "2026-10-01T10:00:00Z")

        policies.changeCurrency("EUR")
        assertThrowsIllegalArgument { policies.changeCurrency("XXX") }
        assertThrowsIllegalArgument { policies.changeCurrency("Dollar") }

        assertEquals(item, stored(item))
        assertEquals("EUR", policies.policy.first().currency)
    }

    @Test
    fun `items must be in the app's currency`() = runTest {
        val dollars = Item(ItemId.NEW, "Camera", Money(12_000, "USD"), changedAt, changedAt)
        assertThrowsIllegalArgument { items.add(dollars) }

        val euros = add("Camera", "2026-10-01T10:00:00Z")
        assertThrowsIllegalArgument { items.update(euros, euros.copy(price = Money(12_000, "USD"))) }

        policies.changeCurrency("USD")
        assertEquals("Camera", items.observeById(items.add(dollars)).first()?.name)
    }

    @Test
    fun `the policy emits on every change`() = runTest {
        policies.policy.test {
            assertEquals("EUR", awaitItem().currency)
            policies.changeCurrency("CHF")
            assertEquals("CHF", awaitItem().currency)
            policies.changeCoolingOffRules(rules("CHF", 0L to 3), changedAt, zone)
            assertEquals(rules("CHF", 0L to 3), awaitItem().coolingOffRules)
        }
    }

    private fun rules(currency: String, vararg tiers: Pair<Long, Int>): CoolingOffRules {
        val result = CoolingOffRules.from(
            tiers.map { (min, days) -> CoolingOffTier(Money(min, currency), Period.ofDays(days)) }
        )
        return (result as CoolingOffRulesResult.Valid).rules
    }
}
