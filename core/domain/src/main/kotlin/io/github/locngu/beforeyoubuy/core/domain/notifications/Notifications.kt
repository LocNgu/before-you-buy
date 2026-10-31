package io.github.locngu.beforeyoubuy.core.domain.notifications

import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.item.ItemId
import io.github.locngu.beforeyoubuy.core.domain.item.Reflection
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.isCooledOff
import io.github.locngu.beforeyoubuy.core.domain.lifecycle.isReflected
import io.github.locngu.beforeyoubuy.core.domain.priorities.prioritiesOf
import io.github.locngu.beforeyoubuy.core.domain.settings.NotificationPrefs
import io.github.locngu.beforeyoubuy.core.domain.wishlist.wantedForDays
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** The two notifications the app sends (brief §8, ADR-0014), one channel each. Never money, deals or suggestions. */
enum class NotificationKind {
    /** "You've wanted <name> for <N> days. Your waiting period is over." with Still want it / Let it go. */
    WAITING_PERIOD_OVER,

    /** "Take a minute to note why you want <name>." Once per item, ~2 days after it was added. */
    REFLECTION_NUDGE
}

/**
 * When the app last notified about an item, per kind. Null: never. Stored next to the item; the planner reads it so
 * nothing is notified twice.
 *
 * @property waitingPeriodOverAt when "your waiting period is over" was sent. A later cooling-off end (a price edit
 *   extended it after the notification) makes the item due again.
 * @property reflectionNudgedAt when the reflection nudge was sent; it's sent once per item.
 */
data class ItemNotifications(val waitingPeriodOverAt: Instant? = null, val reflectionNudgedAt: Instant? = null) {
    /** Every time a notification about the item was sent. */
    val sentAt: List<Instant> get() = listOfNotNull(waitingPeriodOverAt, reflectionNudgedAt)
}

/** An item in a [NotificationPlan], with what its text needs. */
data class NotifiedItem(val item: Item, val wantedForDays: Int)

/**
 * The one notification to send now (brief §8). One item gets its own text and, for [NotificationKind.WAITING_PERIOD_OVER],
 * the Still want it / Let it go actions; several are grouped into one digest ("3 items finished their waiting period").
 */
data class NotificationPlan(val kind: NotificationKind, val items: List<NotifiedItem>) {
    init {
        require(items.isNotEmpty()) { "A notification is about at least one item" }
    }

    val isDigest: Boolean get() = items.size > 1
}

/** The reflection nudge comes this many calendar days after the item was added (brief §8 "~day 2"). */
const val REFLECTION_NUDGE_AFTER_DAYS = 2L

/** The daily check runs around this local time, so notifications don't arrive at night. */
val DAILY_CHECK_TIME: LocalTime = LocalTime.of(10, 0)

/**
 * The notification to send at [now] in [zone], or null (brief §8, ADR-0014). Pure: the caller records what it sent
 * with [ItemNotifications] and passes it back as [notified] next time.
 *
 * - **At most one a day**: nothing if anything in [notified] was sent on today's date.
 * - **Only priorities** are notified about (ADR-0049): a parked want is left alone on purpose, and needs never have a
 *   waiting period (ADR-0008).
 * - **Waiting period over** (if [NotificationPrefs.waitingPeriods]): priorities whose running wait has ended in their
 *   slot and that weren't notified for this end yet. A want that had already waited its full period when it got its
 *   slot isn't notified: the user has just chosen it (ADR-0050). Items the user already chose to "Wait" on are left
 *   out.
 * - **Reflection nudge** (if [NotificationPrefs.reflectionReminders]): priorities still cooling off, without the
 *   required answers, added at least [REFLECTION_NUDGE_AFTER_DAYS] calendar days ago and never nudged. Once cooling
 *   off has ended, the waiting-period notification leads to the questions instead.
 * - Waiting periods come first; nudges that lose to them stay due for the next day. Several items of the kind make
 *   one digest; items are ordered by cooling-off end (waiting periods) or added date (nudges), then id.
 *
 * [prefs] should already say false for a kind the system won't show (permission denied, channel switched off), so
 * nothing is recorded as sent that the user never saw.
 */
fun planNotifications(
    items: List<Item>,
    reflections: Map<ItemId, Reflection>,
    notified: Map<ItemId, ItemNotifications>,
    now: Instant,
    zone: ZoneId,
    prefs: NotificationPrefs
): NotificationPlan? {
    val today = now.atZone(zone).toLocalDate()
    if (notified.values.any { record -> record.sentAt.any { it.atZone(zone).toLocalDate() == today } }) return null
    val priorities = prioritiesOf(items)

    val waitingOver = if (prefs.waitingPeriods) {
        priorities.filter { item ->
            val sent = notified[item.id]?.waitingPeriodOverAt
            val start = item.waitingSince
            val end = item.coolingEndsAt
            // A wait that was already complete when the slot came ends where it starts: it didn't end in the slot.
            start != null && end != null && end > start && now >= end && item.recheckAt == null &&
                (sent == null || sent < end)
        }.sortedWith(compareBy({ it.coolingEndsAt }, { it.id.value }))
    } else {
        emptyList()
    }
    if (waitingOver.isNotEmpty()) return plan(NotificationKind.WAITING_PERIOD_OVER, waitingOver, now, zone)

    val nudges = if (prefs.reflectionReminders) {
        priorities.filter { item ->
            val nudgeDay = item.addedAt.atZone(zone).toLocalDate().plusDays(REFLECTION_NUDGE_AFTER_DAYS)
            !isCooledOff(item, now) &&
                !isReflected(item, reflections[item.id]) &&
                !today.isBefore(nudgeDay) &&
                notified[item.id]?.reflectionNudgedAt == null
        }.sortedWith(compareBy({ it.addedAt }, { it.id.value }))
    } else {
        emptyList()
    }
    return if (nudges.isEmpty()) null else plan(NotificationKind.REFLECTION_NUDGE, nudges, now, zone)
}

private fun plan(kind: NotificationKind, items: List<Item>, now: Instant, zone: ZoneId) =
    NotificationPlan(kind, items.map { NotifiedItem(it, wantedForDays(it, now, zone)) })

/**
 * [current] with [plan] recorded as sent at [sentAt]: what to store for each of its items, by id. Only the plan's
 * kind changes.
 */
fun recordSent(
    plan: NotificationPlan,
    current: Map<ItemId, ItemNotifications>,
    sentAt: Instant
): Map<ItemId, ItemNotifications> = plan.items.associate { (item) ->
    val record = current[item.id] ?: ItemNotifications()
    item.id to when (plan.kind) {
        NotificationKind.WAITING_PERIOD_OVER -> record.copy(waitingPeriodOverAt = sentAt)
        NotificationKind.REFLECTION_NUDGE -> record.copy(reflectionNudgedAt = sentAt)
    }
}

/**
 * How long from [now] until the next [DAILY_CHECK_TIME] in [zone]: the first run of the daily check. Later runs
 * follow a day apart. Calendar arithmetic (ADR-0018), so it's right across daylight-saving changes; never zero.
 */
fun untilNextDailyCheck(now: Instant, zone: ZoneId): Duration {
    val local = now.atZone(zone)
    val todayAtCheck = local.toLocalDate().atTime(DAILY_CHECK_TIME).atZone(zone)
    val next = if (todayAtCheck.toInstant() > now) todayAtCheck else todayAtCheck.plusDays(1)
    return Duration.between(now, next.toInstant())
}
