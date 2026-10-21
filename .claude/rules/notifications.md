---
description: Notifications — the planner and log in :core:domain, the daily check, worker, channels, "Let it go" receiver, deep links and permission prompt in :app
paths:
  - "core/domain/src/**/notifications/**/*"
  - "core/data/src/**/notifications/**/*"
  - "app/src/main/kotlin/**/notifications/**/*"
  - "app/src/test/kotlin/**/notifications/**/*"
  - "app/src/debug/**/*"
  - "app/src/release/**/*"
---

# Notifications (brief §8, ADR-0012, ADR-0014, ADR-0042)

## Rules (`:core:domain` `notifications/`)

- `planNotifications(items, reflections, notified, now, zone, prefs)` returns the **one** `NotificationPlan` to send now, or null. Pure; the rules and their order are in its KDoc and ADR-0042: one a day (anything in `notified` sent on today's date blocks), waiting periods before nudges, a digest when several items qualify, needs never, ended items never, "Wait"ed items never get "waiting period over".
- `ItemNotifications` is the per-item record (when each kind was sent). "Waiting period over" is due again only if `coolingEndsAt` moved past the record (price edit). The nudge is once per item, ever.
- `recordSent(plan, current, sentAt)` gives the records to store; `untilNextDailyCheck(now, zone)` the first run's delay (next `DAILY_CHECK_TIME`, calendar arithmetic).
- `prefs` passed to the planner must already be false for kinds the system won't show, so nothing unseen is recorded.
- Never add a notification about money, deals, prices or suggestions (ADR-0014). New kinds need an ADR and their own channel.

## Storage (`:core:data` `notifications/`)

- `item_notifications` (Room version 3): one row per item, `ON DELETE CASCADE`. `NotificationLogRepository.store` writes in one transaction and skips items that don't exist. Not in `items`: item writes are compare-and-set on the whole row (ADR-0032).

## App (`:app` `notifications/`)

- `NotificationCheck.run()` (singleton, mutex): reads prefs, asks `Notifier.canShow` per kind, plans, **stores the records, then shows**. `NotificationWorker` runs it daily (`schedule`: periodic 1 day, flex `FLEX`, KEEP, from `onAppStart`); `runNow` is for debug. Workers and receivers get dependencies through `NotificationsEntryPoint`, not injection.
- `Notifier`: channels `waiting_periods` (default importance) and `reflection_reminders` (low), (re)created at app start and before each check. Ids are per kind and day. One waiting item: title name, text with days, tap → item detail, actions "Still want it" (→ decision) and "Let it go" (broadcast). Digests and nudges have no actions; digests open the wishlist, a single nudge the reflection flow.
- `NotificationIntents` builds and reads the explicit intents; `MainActivity` reads a tap in `onCreate` (only without saved state) and `onNewIntent`, cancels that notification and hands `NotificationTap` to `AppNavigation`, which replaces the back stack with `backStackFor(tap)` (Home → Wishlist → detail → target).
- `LetItGoReceiver` (not exported) → `handle(...)` → `LetItGo.run`: `REJECT` from `NOTIFICATION` through `applyDecision` + `recordDecision`, re-read on a conflict (up to 3 tries), nothing for ended/gone items. The notification is removed either way; no confirmation notification.
- Permission (Android 13+): `NotificationPermissionViewModel` (app-wide, in `AppNavigation`) asks once when there is an active item and `notificationPermissionAsked` is false; any answer sets it. The dialog is two equal text buttons. The app works fully without the permission.
- Manifest: `POST_NOTIFICATIONS` only. Never `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` (`NotificationWorkerTest` checks).
- Copy: `notification_*` strings and plurals, en + de, tone per `copy-and-tone.md`.

## Debug builds (`app/src/debug`)

- `ClockOffsetModule` binds `DebugClockOffset` (SharedPreferences `debug_clock`); `TimeModule` wraps the system clock with it, so the whole app moves. Release binds `ClockOffset.None` (`app/src/release`).
- `DebugNotificationsReceiver` (exported, protected by `DUMP`): `adb shell am broadcast -p io.github.locngu.beforeyoubuy -a io.github.locngu.beforeyoubuy.debug.ADVANCE_CLOCK --ei days 7` (then checks), `…debug.RUN_NOTIFICATION_CHECK`, `…debug.RESET_CLOCK`. The one-a-day rule follows the moved clock.

## Tests

- Planner: `NotificationsTest` (JVM). Storage: `RoomNotificationLogRepositoryTest`, `MigrationTest` 2 → 3. App: `NotificationCheckTest` (real `Notifier`, Robolectric's notification manager: text en/de, actions, channels, permission/channel off), `LetItGoTest` (incl. the receiver: no activity started, notification removed), `NotificationWorkerTest` (work-testing: schedule, KEEP, worker in the real graph, manifest permissions), `NotificationLinksTest` (back stacks, intents, launching `MainActivity` from a tap), `NotificationPermissionViewModelTest`, `NotificationPermissionFlowTest` (real app with the permission denied).
- `TestApplication` grants `POST_NOTIFICATIONS` so the prompt never covers other flow tests; a test that needs it denied does so before the activity starts.
