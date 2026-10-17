package io.github.locngu.beforeyoubuy.core.domain.capture

import io.github.locngu.beforeyoubuy.core.domain.coolingoff.CoolingOffRules
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.core.domain.money.MoneyParseResult
import io.github.locngu.beforeyoubuy.core.domain.settings.Policy
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureTest {
    private val policy = Policy("EUR", CoolingOffRules.defaults("EUR"))
    private val german = Locale.GERMANY
    private val english = Locale.US
    private val berlin: ZoneId = ZoneId.of("Europe/Berlin")

    // The evening before the clocks go back (25 Oct 2026): a calendar day isn't always 24 hours.
    private val added = Instant.parse("2026-10-24T20:00:00Z")

    private fun capture(form: CaptureForm, locale: Locale = german) = capturedItem(form, policy, locale, added, berlin)

    @Test
    fun `name and price are required`() {
        val empty = checkCapture(CaptureForm(), "EUR", german)
        assertTrue(empty.nameMissing)
        assertEquals(MoneyParseResult.Empty, empty.price)
        assertFalse(empty.isValid)

        assertFalse(checkCapture(CaptureForm(name = "Camera"), "EUR", german).isValid)
        assertFalse(checkCapture(CaptureForm(price = "12"), "EUR", german).isValid)
        assertTrue(checkCapture(CaptureForm(name = "Camera", price = "12"), "EUR", german).isValid)
    }

    @Test
    fun `a blank name is missing`() {
        assertTrue(checkCapture(CaptureForm(name = "  \n", price = "12"), "EUR", german).nameMissing)
    }

    @Test
    fun `the price reads both separator styles in either language`() {
        val expected = MoneyParseResult.Success(Money(123_456, "EUR"))
        assertEquals(expected, checkCapture(CaptureForm(name = "TV", price = "1.234,56"), "EUR", german).price)
        assertEquals(expected, checkCapture(CaptureForm(name = "TV", price = "1,234.56"), "EUR", english).price)
    }

    @Test
    fun `a price the parser rejects says why`() {
        assertEquals(
            MoneyParseResult.Invalid,
            checkCapture(CaptureForm(name = "TV", price = "abc"), "EUR", german).price
        )
        assertEquals(
            MoneyParseResult.TooManyDecimals(2),
            checkCapture(CaptureForm(name = "TV", price = "1,234"), "EUR", german).price
        )
    }

    @Test
    fun `a captured want waits for its tier, counted in calendar days`() {
        val item = capture(CaptureForm(name = "  Camera ", price = "1.200"))

        assertEquals(ItemId.NEW, item.id)
        assertEquals(ItemStatus.ACTIVE, item.status)
        assertEquals("Camera", item.name)
        assertEquals(Money(120_000, "EUR"), item.price)
        assertEquals(added, item.addedAt)
        assertEquals(ItemKind.WANT, item.kind)
        // 90 days later at the same wall-clock time in Berlin, one hour more than 90 × 24 h.
        assertEquals(Instant.parse("2027-01-22T21:00:00Z"), item.coolingEndsAt)
    }

    @Test
    fun `a captured need doesn't wait`() {
        val item = capture(CaptureForm(name = "Fridge", price = "600", kind = ItemKind.NEED))

        assertEquals(ItemKind.NEED, item.kind)
        assertEquals(added, item.coolingEndsAt)
        assertNull(item.markedAsNeedAt)
    }

    @Test
    fun `optional fields are trimmed and blank ones left out`() {
        val blank = capture(CaptureForm(name = "Camera", price = "5", link = " ", category = "", notes = "\n"))
        assertNull(blank.link)
        assertNull(blank.category)
        assertNull(blank.notes)
        assertNull(blank.targetDate)

        val filled = capture(
            CaptureForm(
                name = "Camera",
                price = "5",
                link = " https://example.com/camera ",
                category = " Photo ",
                notes = " Line one\nline two ",
                targetDate = LocalDate.of(2026, 12, 24)
            )
        )
        assertEquals("https://example.com/camera", filled.link)
        assertEquals("Photo", filled.category)
        assertEquals("Line one\nline two", filled.notes)
        assertEquals(LocalDate.of(2026, 12, 24), filled.targetDate)
    }

    @Test
    fun `an invalid form can't be captured`() {
        assertThrows(IllegalArgumentException::class.java) { capture(CaptureForm(name = "", price = "5")) }
        assertThrows(IllegalArgumentException::class.java) { capture(CaptureForm(name = "Camera", price = "x")) }
    }

    @Test
    fun `cooling-off days match each default tier`() {
        val cases = listOf("0" to 7, "99,99" to 7, "100" to 30, "499,99" to 30, "500" to 60, "1.000" to 90)
        for ((price, days) in cases) {
            val item = capture(CaptureForm(name = "Thing", price = price))
            assertEquals("price $price", days, coolingOffDays(item, berlin))
        }
    }

    @Test
    fun `a need has no cooling-off days`() {
        val item = capture(CaptureForm(name = "Fridge", price = "600", kind = ItemKind.NEED))
        assertEquals(0, coolingOffDays(item, berlin))
    }
}
