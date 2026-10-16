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
    fun `de-DE groups with dots and puts the sign after a no-break space`() {
        assertEquals("1.235 €", Money(123_456, "EUR").format(germany))
    }

    @Test
    fun `en-US and en-GB put the sign first`() {
        assertEquals("$1,235", Money(123_456, "USD").format(us))
        assertEquals("€1,235", Money(123_456, "EUR").format(us))
        assertEquals("£1,235", Money(123_456, "GBP").format(uk))
        assertEquals("€1,235", Money(123_456, "EUR").format(uk))
    }

    @Test
    fun `whole amounts show no minor-unit digits`() {
        assertEquals("120 €", Money(12_000, "EUR").format(germany))
        assertEquals("€120", Money(12_000, "EUR").format(uk))
    }

    @Test
    fun `any minor units round up to the next whole unit`() {
        assertEquals("20 €", Money(1_999, "EUR").format(germany))
        assertEquals("20 €", Money(1_901, "EUR").format(germany))
        assertEquals("€20", Money(1_999, "EUR").format(us))
        assertEquals("1 €", Money(5, "EUR").format(germany))
    }

    @Test
    fun `zero stays zero`() {
        assertEquals("0 €", Money(0, "EUR").format(germany))
        assertEquals("€0", Money(0, "EUR").format(uk))
    }

    @Test
    fun `currencies without minor units look as before`() {
        assertEquals("¥1,234", Money(1_234, "JPY").format(us))
    }

    @Test
    fun `currencies with three minor-unit digits round up too`() {
        assertEquals("2 BHD", Money(1_001, "BHD").format(germany))
    }

    @Test
    fun `negative amounts round away from zero, never towards it`() {
        assertEquals("-20 €", Money(-1_999, "EUR").format(germany))
    }

    @Test
    fun `large amounts group and don't overflow`() {
        assertEquals("1.234.568 €", Money(123_456_701, "EUR").format(germany))
        // The largest amount parseMoney accepts.
        assertEquals("€92,233,720,368,547,758", Money(Long.MAX_VALUE, "EUR").format(uk))
        assertEquals("¥9,223,372,036,854,775,807", Money(Long.MAX_VALUE, "JPY").format(us))
    }

    @Test
    fun `totals add the exact amounts and round once`() {
        val cent = Money(1, "EUR")
        assertEquals(Money(2, "EUR"), listOf(cent, cent).total("EUR"))
        assertEquals("1 €", listOf(cent, cent).total("EUR").format(germany))
        assertEquals("40 €", listOf(Money(1_999, "EUR"), Money(1_999, "EUR")).total("EUR").format(germany))
    }

    @Test
    fun `the total of nothing is zero`() {
        assertEquals(Money(0, "EUR"), emptyList<Money>().total("EUR"))
    }

    @Test
    fun `totals refuse mixed currencies and overflow`() {
        assertThrows(IllegalArgumentException::class.java) {
            listOf(Money(1, "EUR"), Money(1, "USD")).total("EUR")
        }
        assertThrows(ArithmeticException::class.java) {
            listOf(Money(Long.MAX_VALUE, "EUR"), Money(1, "EUR")).total("EUR")
        }
    }

    @Test
    fun `input text is exact, without sign or grouping, in the locale's decimal separator`() {
        assertEquals("19,99", Money(1_999, "EUR").formatForInput(germany))
        assertEquals("19.99", Money(1_999, "EUR").formatForInput(us))
        assertEquals("1234,05", Money(123_405, "EUR").formatForInput(germany))
        assertEquals("19,90", Money(1_990, "EUR").formatForInput(germany))
    }

    @Test
    fun `input text for whole amounts has no decimals`() {
        assertEquals("120", Money(12_000, "EUR").formatForInput(germany))
        assertEquals("0", Money(0, "EUR").formatForInput(us))
        assertEquals("1234", Money(1_234, "JPY").formatForInput(germany))
    }

    @Test
    fun `input text reads back to the same amount`() {
        val locales = listOf(germany, us, uk, Locale.forLanguageTag("de-CH"), Locale.forLanguageTag("en-ZA"))
        val amounts =
            listOf(
                Money(0, "EUR"),
                Money(1, "EUR"),
                Money(1_999, "EUR"),
                Money(12_000, "EUR"),
                Money(123_456, "EUR"),
                Money(Long.MAX_VALUE, "EUR"),
                Money(1_234, "JPY"),
                Money(Long.MAX_VALUE, "JPY"),
                Money(1_234, "BHD"),
                Money(1_234_567, "BHD"),
            )
        for (locale in locales) {
            for (money in amounts) {
                val text = money.formatForInput(locale)
                assertEquals(
                    "$money as \"$text\" in $locale",
                    MoneyParseResult.Success(money),
                    parseMoney(text, money.currency, locale),
                )
            }
        }
    }

    @Test
    fun `input text refuses negative amounts`() {
        assertThrows(IllegalArgumentException::class.java) { Money(-1, "EUR").formatForInput(germany) }
    }

    @Test
    fun `currency must be an ISO 4217 code`() {
        assertThrows(IllegalArgumentException::class.java) { Money(1, "eur") }
        assertThrows(IllegalArgumentException::class.java) { Money(1, "€") }
        assertThrows(IllegalArgumentException::class.java) { Money(1, "ABC") }
    }
}
