---
description: Settings — the notification notice in :core:domain, the screen, view model and system-page intents in :app
paths:
  - "core/domain/src/**/settings/**/*"
  - "app/src/main/kotlin/**/feature/settings/**/*"
  - "app/src/test/kotlin/**/feature/settings/**/*"
---

# Settings (brief §6.7, §9, ADR-0003, ADR-0004, ADR-0043, ADR-0045, ADR-0047, ADR-0054)

## Rules (`:core:domain` `settings/`)

- `notificationNoticeOf(prefs, system)` → `NotificationNotice`: `None` while both switches are off or the phone shows everything; `AppBlocked` when `SystemNotifications.appAllowed` is false (said once, not per channel); else `ChannelsOff(waitingPeriods, reflectionReminders)`, true only for a channel that's off while its switch is on.
- `SystemNotifications` is read by the app (`Notifier.read()`, the same checks as `canShow` in the daily check, but read-only: it creates no channels, and a channel not created yet counts as on); the rule stays pure.

## Screen and view model (`:app` `feature/settings/`)

- Opened from Home's gear (`Settings` NavKey); back returns to Home. Sections in order: Fun money, Priorities, Notifications, Language, About. #27 adds Backup, #97 Waiting periods and Currency, #98 "Third-party licences" under About; put them in this order, not at the end.
- `SettingsViewModel(preferences, policies, items, SystemNotificationsReader, clock)`: `uiState` is null until loaded (show only the title), including the system state, which only `refreshSystemNotifications()` reads (once per resume, the first one too). Switches write `preferences.setNotificationPrefs` from the **stored** prefs, not the shown ones, one at a time under a `Mutex` (DataStore writes suspend; two quick taps would otherwise both read the old prefs). `changePrioritySlots` passes the priorities the dialog listed, like the wishlist; on `false` `priorityEvents` emits `ChangedMeanwhile` (snackbar via `priorityMessage`). `refreshSystemNotifications()` runs on every resume (`LifecycleResumeEffect`), since the user may change the system settings and come back.
- `SettingsContent(state, version, perAppLanguage, actions)` is stateless (screenshots); `SettingsActions` holds every callback, no-ops by default; `SettingsScreen` `remember`s it so a new state doesn't recompose every section.
- Fun money: one row to `FunMoney` (ADR-0045). Priorities: "Priority slots" with `priority_slots_used`, opening `PrioritySlotsDialog` (ADR-0047).
- Notifications: the two `NotificationPrefs` switches, labelled with the channel names and descriptions (`notification_channel_*`), whole row toggleable. `AppBlocked` → a line above them and "Open system settings" (`SettingsIntents.appNotifications`); `ChannelsOff` → a line under the affected switch and its channel's page (`SettingsIntents.channel`, `Notifier.CHANNEL_*`).
- Language: `hasPerAppLanguageSettings()` (Android 13+) → "App language" with `settings_language_current` (the language the app shows, per `values*/`), opening `SettingsIntents.appLanguage`; else the quiet line "Follows the system language". No in-app picker.
- About: version (`PackageInfo.versionName`, from the tag), "Source available, free for personal and non-commercial use" + "PolyForm Noncommercial License 1.0.0" → `LICENCE_URL`, "Source code" → `SOURCE_URL` (both via `LocalUriHandler`), the privacy statement. **Never "open source"** (ADR-0004); `SourceAvailableWordingTest` checks every string in en + de.
- A system page or browser that's missing (`ActivityNotFoundException`, `openUri` failing) → snackbar `settings_no_app`, never a crash. No new permission: every page is opened in another app.

## Tests

- Rule: `NotificationNoticeTest` (JVM). View model: `SettingsViewModelTest` (`FakePreferencesRepository`, `FakePolicyRepository` with `items`, a lambda `SystemNotificationsReader`). Stateless screen: `SettingsContentTest` (language per version, channel page). Real app: `SettingsFlowTest` (Home ↔ Settings, fun money row, a switch stored across visits, the blocked line and its page with the permission denied and gone after a resume, language page, licence and source links; started intents via `shadowOf(application).nextStartedActivity`). Robolectric only has Android 37 here, so the Android 8–12 line is covered by `hasPerAppLanguageSettings` and the stateless screen. Screenshots: `SettingsScreenshotTest` typical, notifications blocked, channel off + Android 8–12 line, loading × en/de × light/dark, on a taller screen (`h1300dp`) so the whole page is in one image.
