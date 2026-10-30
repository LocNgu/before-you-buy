# ADR-0042: Notifications: waiting periods before nudges, per-item log in Room, daily check around 10:00, permission asked once after the first item

**Status**: accepted; amended by ADR-0049 (both kinds only concern priorities; parked wants are never notified about)
**Type**: product
**Date**: 2026-10-21

## Context

Issue #20 builds the notifications of brief §8 within ADR-0014 (at most one a day; "Still want it / Let it go"; one nudge per item; never money or deals). It leaves open: which notification wins when both kinds are due on the same day, what a digest's tap and actions do, which items count as "cooling-off ended since last check", where "last notified" is stored, when in the day the check runs, how the permission is asked, and what "Let it go" shows afterwards.

Alternatives considered:

- **Waiting periods and nudges in one notification**: one channel can't then be switched off on its own, and the issue asks for two.
- **"Since last check" as a global last-run time**: an item skipped because of the one-a-day cap would never be notified. A per-item record can't lose one.
- **Last notified in the items table**: item writes are compare-and-set on the whole row (ADR-0032), so the daily check writing there would make an open edit fail. A separate table keeps them apart.
- **The global "sent today" in DataStore**: a second store, not written atomically with the per-item records. It's the newest of those records instead.
- **Per-item actions in a digest** ("Let it go" on each line): Android notifications can't do that in one notification; separate notifications would break the one-a-day rule.
- **A dialog asking for the permission at first start**: out of context, before the user has anything to be notified about.

## Decision

- **Kinds and channels**: "Waiting periods" (default importance) and "Reflection reminders" (low importance: a reminder, not news). The user can switch each off in the system settings; a kind that's switched off, or any kind without the permission, counts as off, so nothing is recorded as sent that wasn't shown.
- **Waiting period over**: active wants whose cooling-off has ended and that weren't notified for this end. A cooling-off end extended after the notification (price edit) makes the item due again. Items the user already chose to "Wait" on are left out (a re-check reminder is not in #20). Needs never. One item: "You've wanted <name> for <N> days. Your waiting period is over.", tap → item detail, **Still want it** → decision screen, **Let it go** → `REJECT` from `NOTIFICATION` without opening the app. Several: "3 items finished their waiting period" with their names, tap → wishlist, no actions.
- **Reflection nudge**: active wants still cooling off, without the required answers, from the second calendar day after adding (device zone, ADR-0018), once per item. Once cooling off ends, the waiting-period notification leads to the questions instead. One item: "Take a minute to note why you want <name>.", tap → the reflection questions. Several: one digest, tap → wishlist.
- **One a day**: nothing if anything was sent on today's date. When both kinds are due, waiting periods go first; the nudges stay due for the next day.
- **Storage**: table `item_notifications` (Room version 3), one row per item notified about, with when each kind was sent; it goes with its item. Records are written before the notification is shown, so a failure can't repeat the notification every day.
- **Schedule**: WorkManager periodic work, once a day with a 2-hour flexible window, first run around 10:00 local time, kept across app starts. No exact alarms.
- **Permission (Android 13+)**: asked once, as soon as there is an active item (right after the first quick add, or on the next app start after a share), with a one-line explanation and "Allow notifications" / "Not now" as equal text buttons. Either answer, or dismissing it, ends it; there is no in-app place to change it until Settings (M3). The app works fully without it.
- **After "Let it go"**: the notification is removed, no confirmation notification. If the item ended meanwhile, nothing is written and the notification still goes.
- **Debug builds** can move the app's time forward and run the check (`DebugNotificationsReceiver`, adb broadcasts); release builds always use the system time.

## Consequences

- Waiting-period notifications are not batched across days: an item whose wait ends the day after a digest gets its own notification that day.
- A user with many unreflected items and a steady stream of ended waits may get nudges late; acceptable, the waiting-period notification leads to the questions too.
- On the first check after updating, every item whose wait ended earlier gets one digest; it's true and only once.
- Deleting an ended item notified about today removes its record, so a second notification could go out the same day. Rare enough to accept.
- Notification text says "waiting period" as the issue does, while the English app says "Cooling off"; the terms are settled in #29.
