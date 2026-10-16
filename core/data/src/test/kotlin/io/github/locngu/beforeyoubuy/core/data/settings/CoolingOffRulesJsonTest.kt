package io.github.locngu.beforeyoubuy.core.data.settings

import io.github.locngu.beforeyoubuy.core.domain.coolingoff.CoolingOffRules
import io.github.locngu.beforeyoubuy.core.domain.coolingoff.CoolingOffRulesResult
import io.github.locngu.beforeyoubuy.core.domain.coolingoff.CoolingOffTier
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import java.time.Period
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoolingOffRulesJsonTest {
    private val custom = (
        CoolingOffRules.from(
            listOf(
                CoolingOffTier(Money(0, "EUR"), Period.ofDays(3)),
                CoolingOffTier(Money(5_000, "EUR"), Period.ofDays(14))
            )
        ) as CoolingOffRulesResult.Valid
        ).rules

    @Test
    fun `rules round trip`() {
        assertEquals(custom, CoolingOffRulesJson.decode(CoolingOffRulesJson.encode(custom), "EUR"))
        val yen = CoolingOffRules.defaults("JPY")
        assertEquals(yen, CoolingOffRulesJson.decode(CoolingOffRulesJson.encode(yen), "JPY"))
    }

    @Test
    fun `stores thresholds in minor units and periods in days`() {
        assertEquals(
            """{"currency":"EUR","tiers":[{"minPriceMinor":0,"days":3},{"minPriceMinor":5000,"days":14}]}""",
            CoolingOffRulesJson.encode(custom)
        )
    }

    @Test
    fun `rules in another currency don't apply`() {
        assertNull(CoolingOffRulesJson.decode(CoolingOffRulesJson.encode(custom), "USD"))
    }

    @Test
    fun `unreadable or invalid rules read as null`() {
        assertNull(CoolingOffRulesJson.decode("not json", "EUR"))
        assertNull(CoolingOffRulesJson.decode("""{"currency":"EUR","tiers":[]}""", "EUR"))
        assertNull(CoolingOffRulesJson.decode("""{"currency":"EUR","tiers":[{"minPriceMinor":100,"days":7}]}""", "EUR"))
        assertNull(CoolingOffRulesJson.decode("""{"currency":"EUR","tiers":[{"minPriceMinor":0,"days":0}]}""", "EUR"))
        assertNull(CoolingOffRulesJson.decode("""{"currency":"eur","tiers":[{"minPriceMinor":0,"days":7}]}""", "eur"))
    }

    @Test
    fun `ignores fields a newer version added`() {
        val text = """{"currency":"EUR","version":2,"tiers":[{"minPriceMinor":0,"days":3,"label":"x"},""" +
            """{"minPriceMinor":5000,"days":14}]}"""

        assertEquals(custom, CoolingOffRulesJson.decode(text, "EUR"))
    }
}
