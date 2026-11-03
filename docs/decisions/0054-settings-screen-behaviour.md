# ADR-0054: Settings: a gear on Home's top bar, links to the existing screens and dialogs, the phone's notification state shown, the language in the system settings, About says "source available"

**Status**: accepted
**Type**: product
**Date**: 2026-11-03

## Context

#26 builds the Settings screen (brief §6.7, §9). ADR-0043 gave Home no Settings entry until the screen exists; ADR-0045 and ADR-0047 built the fun money screen and the slots dialog for Settings to reuse. The owner rewrote the issue on 2026-11-02: waiting periods and the currency move to #97, the third-party licences screen to #98, the backup row comes with #27, and on Android 8–12 the language section is one line. Left open: what the entry on Home looks like, what Settings says when the phone blocks notifications the app's switches ask for, and how About states the licence.

Alternatives considered:

- **A "Settings" text button** next to "History": "Verlauf" + "Einstellungen" next to the app name leaves the title little room in German, less still with large text.
- **An in-app language picker** (AppCompat's per-app locales on Android 8–12), as `docs/architecture.md` once planned: an AppCompat dependency and a second place to choose the language; the owner chose one line instead.
- **Silent switches**: if the permission is denied or a channel is off, a switch that's on does nothing, and nothing says why.
- **Asking for the permission again from Settings**: Android shows the dialog at most twice, then never; the system's notification page always works.
- **Settings' own copy of the fun money form or the slots choice**: two forms for one setting.

## Decision

- **Entry:** a gear icon (content description "Settings") in Home's top bar after "History". Settings opens on top of Home; system back returns to Home. No bottom bar (ADR-0043).
- **Order:** Fun money, Priorities, Notifications, Language, About. #27 adds Backup, #97 Waiting periods and Currency, #98 "Third-party licences" under About.
- **Fun money:** one row opening the existing fun money screen (ADR-0045). No amounts on Settings.
- **Priorities:** "Priority slots" with "2 of 3 slots used", opening the wishlist's slots dialog (`PrioritySlotsDialog`); Save goes through `changePrioritySlots` with the priorities the dialog listed, and a change meanwhile writes nothing and says so (ADR-0047). The wishlist keeps its own "Change".
- **Notifications:** the two switches of `NotificationPrefs`, named and described like their channels in the system settings, stored in DataStore. What the phone does with them is read each time Settings comes to the front (`notificationNoticeOf` in `:core:domain`): with the permission denied or the app's notifications off, one line ("Notifications are off for this app in the system settings, so none will show.") and "Open system settings" to the app's notification page; with a channel off while its switch is on, a line under that switch and its channel's page. Nothing is said while both switches are off.
- **Language:** Android 13+: "App language" with the language the app shows, opening the system's per-app language page (`locales_config.xml`). Android 8–12: "Follows the system language", nothing to tap. No in-app picker.
- **About:** the version; "Source available, free for personal and non-commercial use" with "PolyForm Noncommercial License 1.0.0", opening the licence on polyformproject.org; "Source code" opening the repository; and the privacy statement from brief §9 (no account, server, analytics or ads, no internet permission). Links open in the browser, so the app still needs no permission (ADR-0003). No string anywhere says "open source" (a test checks en + de).

## Consequences

- `docs/architecture.md`'s plan for an AppCompat language picker is dropped; Android 8–12 users change the language with the phone's.
- Settings depends on the system's pages existing; a phone without one gets a snackbar instead of a crash.
- The channel and permission state comes from `Notifier`, the same source as the daily check, so Settings and the check never disagree about what can show.
- Amends ADR-0043 ("No Settings entry until #26" is now the gear).
