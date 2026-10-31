package io.github.locngu.beforeyoubuy.core.domain.wishlist

import io.github.locngu.beforeyoubuy.core.domain.capture.CaptureForm
import io.github.locngu.beforeyoubuy.core.domain.coolingoff.CoolingOffRules
import io.github.locngu.beforeyoubuy.core.domain.coolingoff.CoolingOffRulesResult
import io.github.locngu.beforeyoubuy.core.domain.coolingoff.CoolingOffTier
import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.ADDED
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.AFTER_COOLING
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.COOLING_ENDS
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.COOLING_OFF_DAYS
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.DURING_COOLING
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.item
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.parked
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.core.domain.settings.Policy
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ItemEditTest {
    private val policy = Policy("EUR", CoolingOffRules.defaults("EUR"))
    private val german = Locale.GERMANY
    private val zone: ZoneId = ZoneOffset.UTC

    // The fixture: 120 € added on 1 Oct, so 30 days until 31 Oct.
    private val camera = item()

    private fun edit(form: CaptureForm, current: Item = camera) =
        editedItem(current, form, policy, german, DURING_COOLING, zone)

    @Test
    fun `the form starts with what is stored, the price exact`() {
        val stored = camera.copy(
            price = Money(119_999, "EUR"),
            link = "https://shop.example/camera",
            category = "Photo",
            notes = "The silver one",
            targetDate = LocalDate.of(2026, 12, 24)
        )

        assertEquals(
            CaptureForm(
                name = "Camera",
                price = "1199,99",
                link = "https://shop.example/camera",
                kind = ItemKind.WANT,
                category = "Photo",
                notes = "The silver one",
                targetDate = LocalDate.of(2026, 12, 24)
            ),
            editFormOf(stored, german)
        )
    }

    @Test
    fun `saving an unchanged form keeps the item as it is`() {
        val stored = camera.copy(price = Money(11_999, "EUR"), link = "shop.example/camera", notes = "Silver")

        assertEquals(stored, edit(editFormOf(stored, german), stored))
    }

    @Test
    fun `a lower price in a shorter tier doesn't shorten the wait`() {
        val edited = edit(editFormOf(camera, german).copy(price = "50"))

        assertEquals(Money(5_000, "EUR"), edited.price)
        assertEquals(COOLING_ENDS, edited.coolingEndsAt)
        assertEquals(WaitChange.None, waitChange(camera, edited, DURING_COOLING))
    }

    @Test
    fun `a higher price in a longer tier extends the wait from the day it was added`() {
        val edited = edit(editFormOf(camera, german).copy(price = "600"))

        // 60 days from 1 Oct.
        val ends = Instant.parse("2026-11-30T10:00:00Z")
        assertEquals(ends, edited.coolingEndsAt)
        assertEquals(WaitChange.Extended(ends), waitChange(camera, edited, DURING_COOLING))
    }

    @Test
    fun `a higher price can put a cooled off item back into cooling off`() {
        val edited =
            editedItem(camera, editFormOf(camera, german).copy(price = "600"), policy, german, AFTER_COOLING, zone)

        assertEquals(
            WaitChange.Extended(Instant.parse("2026-11-30T10:00:00Z")),
            waitChange(camera, edited, AFTER_COOLING)
        )
    }

    @Test
    fun `a longer tier whose end has passed changes nothing visible`() {
        val longAfter = Instant.parse("2027-03-01T10:00:00Z")
        val edited = editedItem(camera, editFormOf(camera, german).copy(price = "600"), policy, german, longAfter, zone)

        assertEquals(WaitChange.None, waitChange(camera, edited, longAfter))
    }

    @Test
    fun `an unchanged price keeps an end set under other tiers`() {
        // Tiers were lengthened after the wait ended (ADR-0031 keeps it over); renaming mustn't restart it.
        val longer = Policy(
            "EUR",
            (
                CoolingOffRules.from(
                    listOf(CoolingOffTier(Money(0, "EUR"), Period.ofDays(90)))
                ) as CoolingOffRulesResult.Valid
                )
                .rules
        )
        val edited =
            editedItem(
                camera,
                editFormOf(camera, german).copy(name = "Camera body"),
                longer,
                german,
                AFTER_COOLING,
                zone
            )

        assertEquals(COOLING_ENDS, edited.coolingEndsAt)
        assertEquals(WaitChange.None, waitChange(camera, edited, AFTER_COOLING))
    }

    @Test
    fun `the photo is kept unless another is picked or it's removed`() {
        val stored = camera.copy(imageRef = "old.jpg")
        val form = editFormOf(stored, german)

        assertEquals("old.jpg", edit(form, stored).imageRef)
        assertEquals("new.jpg", editedItem(stored, form, policy, german, DURING_COOLING, zone, "new.jpg").imageRef)
        assertNull(editedItem(stored, form, policy, german, DURING_COOLING, zone, imageRef = null).imageRef)
    }

    @Test
    fun `text is trimmed and blank optional fields are cleared`() {
        val stored = camera.copy(link = "https://shop.example", category = "Photo", notes = "Silver")
        val form = editFormOf(stored, german).copy(name = "  Camera body ", link = " ", category = "", notes = "  ")

        val edited = edit(form, stored)

        assertEquals("Camera body", edited.name)
        assertNull(edited.link)
        assertNull(edited.category)
        assertNull(edited.notes)
    }

    @Test
    fun `a priority marked as need leaves the priorities, keeps what it waited and records when`() {
        val edited = edit(editFormOf(camera, german).copy(kind = ItemKind.NEED))

        assertEquals(ItemKind.NEED, edited.kind)
        assertEquals(DURING_COOLING, edited.markedAsNeedAt)
        assertNull(edited.prioritySlot)
        assertNull(edited.coolingEndsAt)
        assertEquals(14, edited.waitedDays)
        assertEquals(WaitChange.None, waitChange(camera, edited, DURING_COOLING))
    }

    @Test
    fun `a need that becomes a want is parked with the days of its new price`() {
        val need = item(ItemKind.NEED)

        val edited = edit(editFormOf(need, german).copy(kind = ItemKind.WANT, price = "600"), need)

        assertEquals(ItemKind.WANT, edited.kind)
        assertEquals(60, edited.coolingOffDays)
        assertNull(edited.prioritySlot)
        assertNull(edited.coolingEndsAt)
        assertEquals(WaitChange.None, waitChange(need, edited, DURING_COOLING))
    }

    @Test
    fun `a parked want's price change extends its days but shows no end date`() {
        val edited = edit(editFormOf(parked(), german).copy(price = "600"), parked())

        assertEquals(60, edited.coolingOffDays)
        assertNull(edited.coolingEndsAt)
        assertEquals(WaitChange.None, waitChange(parked(), edited, DURING_COOLING))
    }

    @Test
    fun `a need's price change doesn't start a wait`() {
        val need = item(ItemKind.NEED)

        val edited = edit(editFormOf(need, german).copy(price = "2000"), need)

        assertNull(edited.coolingEndsAt)
        assertEquals(COOLING_OFF_DAYS, edited.coolingOffDays)
        assertEquals(WaitChange.None, waitChange(need, edited, DURING_COOLING))
    }

    @Test
    fun `only valid forms of active items can be saved`() {
        assertThrows(IllegalArgumentException::class.java) { edit(editFormOf(camera, german).copy(name = " ")) }
        assertThrows(IllegalArgumentException::class.java) { edit(editFormOf(camera, german).copy(price = "abc")) }
        assertThrows(IllegalArgumentException::class.java) {
            edit(editFormOf(camera, german), camera.copy(status = ItemStatus.REJECTED))
        }
    }
}
