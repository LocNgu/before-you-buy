package io.github.locngu.beforeyoubuy.core.domain.wishlist

import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.item.Reflection
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.ADDED
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.AFTER_COOLING
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.COMPLETE_REFLECTION
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.DURING_COOLING
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.Stage
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.item
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemDetailTest {
    private val zone: ZoneId = ZoneOffset.UTC

    @Test
    fun `while cooling off the link isn't prominent and the reasons are asked for`() {
        val detail = itemDetailOf(item(), null, DURING_COOLING, zone)

        assertEquals(Stage.COOLING_OFF, detail.stage)
        assertEquals(16, detail.daysLeft)
        assertEquals(14, detail.wantedForDays)
        assertFalse(detail.reflected)
        assertFalse(detail.linkProminent)
    }

    @Test
    fun `after cooling off the link may show as a button`() {
        val detail = itemDetailOf(item(), COMPLETE_REFLECTION, AFTER_COOLING, zone)

        assertEquals(Stage.READY, detail.stage)
        assertEquals(0, detail.daysLeft)
        assertTrue(detail.reflected)
        assertTrue(detail.linkProminent)
    }

    @Test
    fun `partial answers still ask for reflection`() {
        val detail = itemDetailOf(item(), Reflection(why = "Hikes"), AFTER_COOLING, zone)

        assertEquals(Stage.NEEDS_REFLECTION, detail.stage)
        assertFalse(detail.reflected)
    }

    @Test
    fun `a need is ready at once and never asked for reasons`() {
        val detail = itemDetailOf(item(ItemKind.NEED), null, DURING_COOLING, zone)

        assertEquals(Stage.READY, detail.stage)
        assertTrue(detail.reflected)
        assertTrue(detail.linkProminent)
        assertFalse(item(ItemKind.NEED).canMarkAsNeed)
        assertTrue(item().canMarkAsNeed)
    }

    @Test
    fun `ended items have no detail`() {
        assertThrows(IllegalArgumentException::class.java) {
            itemDetailOf(item().copy(status = ItemStatus.REJECTED), null, DURING_COOLING, zone)
        }
    }

    @Test
    fun `wanted for counts calendar days in the zone`() {
        val berlin = ZoneId.of("Europe/Berlin")
        // Added 1 Oct 12:00 in Berlin; 23:30 the same day is still day 0, 00:30 the next day is day 1.
        assertEquals(0, wantedForDays(item(), Instant.parse("2026-10-01T21:30:00Z"), berlin))
        assertEquals(1, wantedForDays(item(), Instant.parse("2026-10-01T22:30:00Z"), berlin))
        assertEquals(0, wantedForDays(item(), ADDED.minusSeconds(3600 * 24), zone))
    }

    @Test
    fun `web links open, other schemes only copy`() {
        assertEquals("https://shop.example/camera", webLink("https://shop.example/camera"))
        assertEquals("http://shop.example", webLink(" http://shop.example "))
        assertEquals("https://SHOP.EXAMPLE/Camera", webLink("HTTPS://SHOP.EXAMPLE/Camera"))
        assertEquals("https://shop.example/camera", webLink("shop.example/camera"))
        assertEquals("https://shop.example:8080/x", webLink("shop.example:8080/x"))
        assertNull(webLink("intent://scan#Intent;scheme=zxing;end"))
        assertNull(webLink("javascript:alert(1)"))
        assertNull(webLink("file:///sdcard/x"))
        assertNull(webLink("mailto:shop@example.com"))
        assertNull(webLink(""))
        assertNull(webLink("two words"))
    }
}
