package io.github.locngu.beforeyoubuy.core.domain.notifications

import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.ItemKind
import io.github.locngu.beforeyoubuy.core.domain.item.ItemStatus
import io.github.locngu.beforeyoubuy.core.domain.item.Reflection
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.ADDED
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.AFTER_COOLING
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.COMPLETE_REFLECTION
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.COOLING_ENDS
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.DURING_COOLING
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.item
import io.github.locngu.beforeyoubuy.core.domain.settings.NotificationPrefs
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationsTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val allOn = NotificationPrefs(waitingPeriods = true, reflectionReminders = true)

    /** Added on [ADDED] (1 Oct, 10:00), cooling off until [endsAt] (default [COOLING_ENDS], 31 Oct). */
    private fun wish(id: Long, endsAt: Instant = COOLING_ENDS, addedAt: Instant = ADDED): Item =
        item().copy(id = ItemId(id), name = "Item $id", addedAt = addedAt, coolingEndsAt = endsAt)

    private fun plan(
        items: List<Item>,
        now: Instant,
        reflections: Map<ItemId, Reflection> = emptyMap(),
        notified: Map<ItemId, ItemNotifications> = emptyMap(),
        prefs: NotificationPrefs = allOn
    ) = planNotifications(items, reflections, notified, now, zone, prefs)

    private fun NotificationPlan?.ids() = this?.items?.map { it.item.id.value }

    // Waiting period over

    @Test
    fun `an item whose cooling-off ended gets its own notification with how long it was wanted`() {
        val camera = wish(1)

        val plan = plan(listOf(camera), AFTER_COOLING)!!

        assertEquals(NotificationKind.WAITING_PERIOD_OVER, plan.kind)
        assertEquals(listOf(NotifiedItem(camera, wantedForDays = 32)), plan.items)
        assertFalse(plan.isDigest)
    }

    @Test
    fun `nothing while every item is still cooling off and reflected`() {
        assertNull(plan(listOf(wish(1)), DURING_COOLING, reflections = mapOf(ItemId(1) to COMPLETE_REFLECTION)))
    }

    @Test
    fun `the notification comes the moment cooling-off ends, not before`() {
        assertNull(plan(listOf(wish(1)), COOLING_ENDS.minusMillis(1), mapOf(ItemId(1) to COMPLETE_REFLECTION)))
        assertEquals(listOf(1L), plan(listOf(wish(1)), COOLING_ENDS).ids())
    }

    @Test
    fun `several items that finished waiting make one digest, soonest end first`() {
        val items = listOf(
            wish(3, endsAt = Instant.parse("2026-10-30T10:00:00Z")),
            wish(1, endsAt = Instant.parse("2026-10-31T10:00:00Z")),
            wish(2, endsAt = Instant.parse("2026-10-30T10:00:00Z"))
        )

        val plan = plan(items, AFTER_COOLING)!!

        assertEquals(NotificationKind.WAITING_PERIOD_OVER, plan.kind)
        assertTrue(plan.isDigest)
        assertEquals(listOf(2L, 3L, 1L), plan.ids())
    }

    @Test
    fun `an item already notified for this end isn't notified again`() {
        val notified = mapOf(ItemId(1) to ItemNotifications(waitingPeriodOverAt = COOLING_ENDS.plusSeconds(60)))

        assertNull(plan(listOf(wish(1)), AFTER_COOLING, notified = notified))
    }

    @Test
    fun `a cooling-off end extended after the notification makes the item due again once the new end passes`() {
        // Notified on 1 Nov; then a price edit moved the end to 10 Nov.
        val notified =
            mapOf(ItemId(1) to ItemNotifications(waitingPeriodOverAt = Instant.parse("2026-11-01T10:00:00Z")))
        val extended = wish(1, endsAt = Instant.parse("2026-11-10T10:00:00Z"))
        val reflected = mapOf(ItemId(1) to COMPLETE_REFLECTION)

        assertNull(plan(listOf(extended), Instant.parse("2026-11-09T10:00:00Z"), reflected, notified))
        assertEquals(
            listOf(1L),
            plan(listOf(extended), Instant.parse("2026-11-10T11:00:00Z"), reflected, notified).ids()
        )
    }

    @Test
    fun `needs never get a waiting-period notification`() {
        val need = wish(1).copy(kind = ItemKind.NEED)

        assertNull(plan(listOf(need), AFTER_COOLING))
        assertNull(plan(listOf(need), DURING_COOLING))
    }

    @Test
    fun `purchased and decided-against items are never notified about`() {
        val ended = listOf(
            wish(1).copy(status = ItemStatus.PURCHASED),
            wish(2).copy(status = ItemStatus.REJECTED)
        )

        assertNull(plan(ended, AFTER_COOLING))
        assertNull(plan(ended, DURING_COOLING))
    }

    @Test
    fun `an item the user chose to wait on isn't told its waiting period is over`() {
        val waited = wish(1).copy(recheckAt = Instant.parse("2026-11-09T00:00:00Z"))

        assertNull(plan(listOf(waited), AFTER_COOLING))
    }

    @Test
    fun `an unreflected item whose cooling-off ended gets the waiting-period notification, not a nudge`() {
        assertEquals(NotificationKind.WAITING_PERIOD_OVER, plan(listOf(wish(1)), AFTER_COOLING)?.kind)
    }

    @Test
    fun `waiting-period notifications switched off send none, and a nudge can go instead`() {
        val ended = wish(1)
        val unreflected = wish(2, endsAt = Instant.parse("2026-12-01T10:00:00Z"))
        val prefs = allOn.copy(waitingPeriods = false)

        val plan = plan(listOf(ended, unreflected), AFTER_COOLING, prefs = prefs)

        assertEquals(NotificationKind.REFLECTION_NUDGE, plan?.kind)
        assertEquals(listOf(2L), plan.ids())
    }

    // Reflection nudge

    @Test
    fun `the nudge comes from the second calendar day after adding, not before`() {
        val camera = wish(1, addedAt = Instant.parse("2026-10-01T23:00:00Z"))

        assertNull(plan(listOf(camera), Instant.parse("2026-10-02T23:59:00Z")))
        val plan = plan(listOf(camera), Instant.parse("2026-10-03T00:00:00Z"))!!
        assertEquals(NotificationKind.REFLECTION_NUDGE, plan.kind)
        assertEquals(listOf(NotifiedItem(camera, wantedForDays = 2)), plan.items)
    }

    @Test
    fun `the nudge counts calendar days in the device zone`() {
        val berlin = ZoneId.of("Europe/Berlin")
        // 1 Oct 23:30 in Berlin, 21:30 UTC.
        val camera = wish(1, addedAt = Instant.parse("2026-10-01T21:30:00Z"))

        // 3 Oct 00:30 in Berlin is still 2 Oct in UTC.
        val now = Instant.parse("2026-10-02T22:30:00Z")
        assertEquals(listOf(1L), planNotifications(listOf(camera), emptyMap(), emptyMap(), now, berlin, allOn).ids())
        assertNull(planNotifications(listOf(camera), emptyMap(), emptyMap(), now, zone, allOn))
    }

    @Test
    fun `the nudge is sent only once`() {
        val notified = mapOf(ItemId(1) to ItemNotifications(reflectionNudgedAt = Instant.parse("2026-10-03T10:00:00Z")))

        assertNull(plan(listOf(wish(1)), DURING_COOLING, notified = notified))
    }

    @Test
    fun `no nudge once the required answers are there, even if optional ones are missing`() {
        assertNull(plan(listOf(wish(1)), DURING_COOLING, reflections = mapOf(ItemId(1) to COMPLETE_REFLECTION)))
    }

    @Test
    fun `partial answers still get the nudge`() {
        val partial = mapOf(ItemId(1) to Reflection(why = "Better photos"))

        assertEquals(NotificationKind.REFLECTION_NUDGE, plan(listOf(wish(1)), DURING_COOLING, partial)?.kind)
    }

    @Test
    fun `needs are never nudged`() {
        assertNull(plan(listOf(wish(1).copy(kind = ItemKind.NEED)), DURING_COOLING))
    }

    @Test
    fun `reflection reminders switched off send no nudge`() {
        assertNull(plan(listOf(wish(1)), DURING_COOLING, prefs = allOn.copy(reflectionReminders = false)))
    }

    @Test
    fun `several items to nudge make one digest, oldest first`() {
        val items = listOf(
            wish(2, addedAt = Instant.parse("2026-10-02T10:00:00Z")),
            wish(1, addedAt = Instant.parse("2026-10-03T10:00:00Z")),
            wish(3, addedAt = Instant.parse("2026-10-02T10:00:00Z"))
        )

        val plan = plan(items, DURING_COOLING)!!

        assertEquals(NotificationKind.REFLECTION_NUDGE, plan.kind)
        assertTrue(plan.isDigest)
        assertEquals(listOf(2L, 3L, 1L), plan.ids())
    }

    // One a day

    @Test
    fun `waiting periods win over nudges on the same day, and the nudge stays due for the next one`() {
        val ended = wish(1)
        val unreflected = wish(2, endsAt = Instant.parse("2026-12-01T10:00:00Z"))
        val items = listOf(ended, unreflected)

        val first = plan(items, AFTER_COOLING)!!
        assertEquals(NotificationKind.WAITING_PERIOD_OVER, first.kind)
        assertEquals(listOf(1L), first.ids())

        val notified = recordSent(first, emptyMap(), AFTER_COOLING)
        assertNull(plan(items, AFTER_COOLING.plus(Duration.ofHours(13)), notified = notified))

        val nextDay = plan(items, AFTER_COOLING.plus(Duration.ofDays(1)), notified = notified)!!
        assertEquals(NotificationKind.REFLECTION_NUDGE, nextDay.kind)
        assertEquals(listOf(2L), nextDay.ids())
    }

    @Test
    fun `at most one notification a calendar day, whatever item it was about`() {
        val sentToday =
            mapOf(ItemId(9) to ItemNotifications(reflectionNudgedAt = Instant.parse("2026-11-02T00:01:00Z")))

        assertNull(plan(listOf(wish(1)), Instant.parse("2026-11-02T23:59:00Z"), notified = sentToday))
        assertEquals(
            listOf(1L),
            plan(listOf(wish(1)), Instant.parse("2026-11-03T00:00:00Z"), notified = sentToday).ids()
        )
    }

    @Test
    fun `running the check again after sending sends nothing more, the same day or later`() {
        val items = listOf(wish(1), wish(2))
        var notified = emptyMap<ItemId, ItemNotifications>()
        val sent = mutableListOf<NotificationPlan>()

        for (day in 0L..5L) {
            for (hour in listOf(0L, 6L, 12L)) {
                val now = AFTER_COOLING.plus(Duration.ofDays(day)).plus(Duration.ofHours(hour))
                plan(items, now, notified = notified)?.let {
                    sent += it
                    notified = notified + recordSent(it, notified, now)
                }
            }
        }

        assertEquals(1, sent.size)
        assertEquals(listOf(1L, 2L), sent.single().ids())
    }

    @Test
    fun `recordSent changes only the plan's kind and keeps the other record`() {
        val nudgedAt = Instant.parse("2026-10-03T10:00:00Z")
        val current = mapOf(ItemId(1) to ItemNotifications(reflectionNudgedAt = nudgedAt))
        val plan = plan(listOf(wish(1), wish(2)), AFTER_COOLING)!!

        val recorded = recordSent(plan, current, AFTER_COOLING)

        assertEquals(
            mapOf(
                ItemId(1) to ItemNotifications(waitingPeriodOverAt = AFTER_COOLING, reflectionNudgedAt = nudgedAt),
                ItemId(2) to ItemNotifications(waitingPeriodOverAt = AFTER_COOLING)
            ),
            recorded
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a plan without items is a programming error`() {
        NotificationPlan(NotificationKind.REFLECTION_NUDGE, emptyList())
    }

    // Daily check time

    @Test
    fun `the first daily check is at the next 10 o'clock in the zone`() {
        assertEquals(Duration.ofHours(2), untilNextDailyCheck(Instant.parse("2026-10-15T08:00:00Z"), zone))
        assertEquals(Duration.ofHours(23), untilNextDailyCheck(Instant.parse("2026-10-15T11:00:00Z"), zone))
        assertEquals(Duration.ofDays(1), untilNextDailyCheck(Instant.parse("2026-10-15T10:00:00Z"), zone))
    }

    @Test
    fun `the daily check time follows the wall clock across daylight-saving changes`() {
        val berlin = ZoneId.of("Europe/Berlin")
        // 24 Oct 2026, 11:00 in Berlin (summer time); the clocks go back on 25 Oct, so 10:00 is 24 hours later.
        assertEquals(Duration.ofHours(24), untilNextDailyCheck(Instant.parse("2026-10-24T09:00:00Z"), berlin))
    }
}
