package io.github.locngu.beforeyoubuy.core.domain.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {
    @Test
    fun `adds and subtracts minor units`() {
        assertEquals(Money(1_550, "EUR"), Money(1_200, "EUR") + Money(350, "EUR"))
        assertEquals(Money(-150, "EUR"), Money(200, "EUR") - Money(350, "EUR"))
    }

    @Test
    fun `compares amounts in one currency`() {
        assertTrue(Money(1_000, "EUR") > Money(999, "EUR"))
        assertTrue(Money(1_000, "EUR") >= Money(1_000, "EUR"))
        assertEquals(listOf(Money(1, "EUR"), Money(5, "EUR")), listOf(Money(5, "EUR"), Money(1, "EUR")).sorted())
    }

    @Test
    fun `refuses to mix currencies`() {
        assertThrows(IllegalArgumentException::class.java) { Money(1, "EUR") + Money(1, "USD") }
        assertThrows(IllegalArgumentException::class.java) { Money(1, "EUR") - Money(1, "USD") }
        assertThrows(IllegalArgumentException::class.java) { Money(1, "EUR") < Money(1, "USD") }
    }

    @Test
    fun `overflow throws instead of wrapping around`() {
        assertThrows(ArithmeticException::class.java) { Money(Long.MAX_VALUE, "EUR") + Money(1, "EUR") }
        assertThrows(ArithmeticException::class.java) { Money(Long.MIN_VALUE, "EUR") - Money(1, "EUR") }
    }

    @Test
    fun `whole units scale exactly to the currency's minor units`() {
        assertEquals(Money(5_000, "EUR"), Money.ofWholeUnits(50, "EUR"))
        assertEquals(Money(50, "JPY"), Money.ofWholeUnits(50, "JPY"))
        assertEquals(Money(50_000, "BHD"), Money.ofWholeUnits(50, "BHD"))
    }
}
