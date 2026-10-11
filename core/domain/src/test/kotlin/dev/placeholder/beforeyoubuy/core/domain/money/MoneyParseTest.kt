package dev.placeholder.beforeyoubuy.core.domain.money

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyParseTest {
    private val germany = Locale.forLanguageTag("de-DE")
    private val us = Locale.forLanguageTag("en-US")
    private val uk = Locale.forLanguageTag("en-GB")

    private fun parse(text: String, locale: Locale, currency: String = "EUR") = parseMoney(text, currency, locale)

    private fun success(minorUnits: Long, currency: String = "EUR") =
        MoneyParseResult.Success(Money(minorUnits, currency))

    @Test
    fun `whole amounts`() {
        for (locale in listOf(germany, us, uk)) {
            assertEquals(success(123_400), parse("1234", locale))
            assertEquals(success(0), parse("0", locale))
        }
    }

    @Test
    fun `fully formatted amounts work in every locale`() {
        for (locale in listOf(germany, us, uk)) {
            assertEquals(success(123_456), parse("1.234,56", locale))
            assertEquals(success(123_456), parse("1,234.56", locale))
            assertEquals(success(123_456_789), parse("1.234.567,89", locale))
            assertEquals(success(123_456_789), parse("1,234,567.89", locale))
        }
    }

    @Test
    fun `a single short decimal part uses either separator`() {
        for (locale in listOf(germany, us, uk)) {
            assertEquals(success(1_250), parse("12,5", locale))
            assertEquals(success(1_250), parse("12.5", locale))
            assertEquals(success(1_299), parse("12,99", locale))
            assertEquals(success(50), parse(",5", locale))
            assertEquals(success(1_200), parse("12.", locale))
        }
    }

    @Test
    fun `a single separator followed by three digits is read the locale's way`() {
        assertEquals(success(123_400), parse("1.234", germany))
        assertEquals(MoneyParseResult.TooManyDecimals(2), parse("1,234", germany))

        assertEquals(success(123_400), parse("1,234", us))
        assertEquals(MoneyParseResult.TooManyDecimals(2), parse("1.234", us))
        assertEquals(success(123_400), parse("1,234", uk))
    }

    @Test
    fun `repeated separators group digits`() {
        assertEquals(success(123_456_700), parse("1.234.567", us))
        assertEquals(success(123_456_700), parse("1,234,567", germany))
    }

    @Test
    fun `spaces, no-break spaces and apostrophes group digits`() {
        assertEquals(success(123_456), parse("1 234,56", germany))
        assertEquals(success(123_456), parse("1 234,56", germany))
        assertEquals(success(123_456), parse("1 234.56", uk))
        assertEquals(success(123_456), parse("1’234.56", uk))
        assertEquals(success(123_456_789), parse("1 234 567,89", germany))
    }

    @Test
    fun `the currency sign or code may be typed around the amount`() {
        assertEquals(success(123_456), parse("1.234,56 €", germany))
        assertEquals(success(123_456), parse("€1,234.56", uk))
        assertEquals(success(1_250), parse("12,50 EUR", germany))
        assertEquals(success(999, "GBP"), parse("£9.99", uk, currency = "GBP"))
        assertEquals(success(999, "USD"), parse(" $ 9.99 ", us, currency = "USD"))
    }

    @Test
    fun `nothing entered is empty`() {
        assertEquals(MoneyParseResult.Empty, parse("", germany))
        assertEquals(MoneyParseResult.Empty, parse("   ", germany))
        assertEquals(MoneyParseResult.Empty, parse("€", germany))
    }

    @Test
    fun `invalid input`() {
        val invalid =
            listOf(
                "abc",
                "12a",
                "-5",
                "+5",
                "1e3",
                ".",
                ",",
                "1..5",
                "1,,234",
                "12,34,5",
                "1.23.456",
                "1234.567,89",
                "0.123.456",
                "1,234.5.6",
                "12, 50",
                "١٢٣",
                "12 $",
                "12€50",
                "1,234.567,89",
                "1.234 567",
                "1\u2028234",
                "1.2\u20283",
                "12\t50"
            )
        for (text in invalid) {
            assertEquals("\"$text\"", MoneyParseResult.Invalid, parse(text, germany))
        }
    }

    @Test
    fun `more decimals than the currency has are rejected, not rounded`() {
        assertEquals(MoneyParseResult.TooManyDecimals(2), parse("12,345", germany))
        assertEquals(MoneyParseResult.TooManyDecimals(2), parse("1.234,567", germany))
        assertEquals(MoneyParseResult.TooManyDecimals(2), parse("0.001", uk))
        assertEquals(MoneyParseResult.TooManyDecimals(0), parse("12.5", us, currency = "JPY"))
    }

    @Test
    fun `currencies without minor units`() {
        assertEquals(success(1_234, "JPY"), parse("1,234", us, currency = "JPY"))
        assertEquals(success(500, "JPY"), parse("500", us, currency = "JPY"))
        assertEquals(success(1_234, "JPY"), parse("1,234", germany, currency = "JPY"))
        assertEquals(success(1_234, "JPY"), parse("1.234", us, currency = "JPY"))
    }

    @Test
    fun `amounts beyond Long minor units are too large`() {
        assertEquals(success(Long.MAX_VALUE), parse("92233720368547758.07", uk))
        assertEquals(MoneyParseResult.TooLarge, parse("92233720368547758.08", uk))
        assertEquals(MoneyParseResult.TooLarge, parse("99999999999999999999999", uk))
    }
}
