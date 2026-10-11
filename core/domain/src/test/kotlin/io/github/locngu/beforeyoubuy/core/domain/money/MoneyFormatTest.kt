package io.github.locngu.beforeyoubuy.core.domain.money

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MoneyFormatTest {
    private val germany = Locale.forLanguageTag("de-DE")
    private val us = Locale.forLanguageTag("en-US")
    private val uk = Locale.forLanguageTag("en-GB")

    @Test
    fun `de-DE groups with dots, uses a decimal comma and puts the sign after a no-break space`() {
        assertEquals("1.234,56 €", Money(123_456, "EUR").format(germany))
    }

    @Test
    fun `en-US and en-GB put the sign first`() {
        assertEquals("$1,234.56", Money(123_456, "USD").format(us))
        assertEquals("€1,234.56", Money(123_456, "EUR").format(us))
        assertEquals("£1,234.56", Money(123_456, "GBP").format(uk))
        assertEquals("€1,234.56", Money(123_456, "EUR").format(uk))
    }

    @Test
    fun `always shows the currency's minor-unit digits`() {
        assertEquals("12,00 €", Money(1_200, "EUR").format(germany))
        assertEquals("0,05 €", Money(5, "EUR").format(germany))
        assertEquals("€0.00", Money(0, "EUR").format(uk))
    }

    @Test
    fun `currencies without minor units have no decimals`() {
        assertEquals("¥1,234", Money(1_234, "JPY").format(us))
    }

    @Test
    fun `large amounts don't lose precision`() {
        assertEquals("€92,233,720,368,547,758.07", Money(Long.MAX_VALUE, "EUR").format(uk))
    }

    @Test
    fun `currency must be an ISO 4217 code`() {
        assertThrows(IllegalArgumentException::class.java) { Money(1, "eur") }
        assertThrows(IllegalArgumentException::class.java) { Money(1, "€") }
        assertThrows(IllegalArgumentException::class.java) { Money(1, "ABC") }
    }
}
