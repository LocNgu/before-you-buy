# ADR-0050: Waits in priority slots: whole calendar days, a part of a day dropped on pause, added columns, no notification for a wait complete on promotion

**Status**: accepted
**Type**: technical
**Date**: 2026-10-31

## Context

ADR-0049 makes the waiting period run only while a want holds a priority slot; losing the slot pauses the wait and never resets it. It left to the domain PR (#82) how a part of a day counts when a slot is lost ("it must never credit time that hasn't passed"), how a paused wait is stored, and how existing items are migrated.

Until now each item stored one instant, `items.cooling_ends_at` (NOT NULL), fixed at capture. `reflections`, `decisions`, `item_notifications` and `allocations` reference `items(id)` with `ON DELETE CASCADE`. Item writes are compare-and-set on the stored row (ADR-0032), so a row must read back exactly as it was written.

Alternatives considered:

- **Time waited as an exact duration**, the end as start + remaining duration: a day would be 24 hours, not a calendar day (ADR-0018), so an end would move by an hour across a daylight-saving change.
- **Counting a started day, or rounding, on pause**: promoting a want and taking it out again around midnight would add days that haven't passed. Dropping the part of a day can't be gamed; at most a part of a day is lost per pause.
- **Making `cooling_ends_at` nullable**: SQLite can't change a column's NOT NULL, so this needs a table rebuild. Dropping `items` while its children cascade risks deleting the user's data inside a migration. Adding columns can't.
- **Counting the migrated time in SQL** (`strftime('%s','now')`, 24-hour days): ignores the device zone, so a 25-hour autumn day counts early, and it reads the system clock instead of the injected one (ADR-0018).
- **Notifying "waiting period over" for a want promoted after waiting its full period**: the user has just chosen it, and Home's "To decide" shows it once its answers are in.

## Decision

- **An item stores its wait** as `coolingOffDays` (the days to wait: set at capture, only extended by a price edit, set again by a tier change while the wait isn't complete), `waitedDays` (whole calendar days waited in earlier stretches), `waitingSince` (when the running stretch started; null while the wait doesn't run) and `coolingEndsAt` (when the running wait is complete; null exactly while `waitingSince` is). `Item` enforces the pairing.
- **Starting** at `t` (the want gets a slot): the wait ends `coolingOffDays − waitedDays` calendar days after `t` in the device zone, or at `t` if it has already waited enough. **Pausing** at `t` (it loses the slot): the whole calendar days from `waitingSince` to `t` are added to `waitedDays`, counted on local date-times like `plusDays` (`wholeDaysBetween`), so a day only counts once the same time of day comes round again. A part of a day is dropped.
- **Cooled off** = a need, or `waitedDays ≥ coolingOffDays`, or the running wait's end has passed. The stage **Parked** (an active want without a slot) is checked before every other stage, so a parked want is never Ready (ADR-0049).
- **Slot writes start and pause waits in the same transaction**: `setPriorities` and `changePrioritySlots` take the time and zone and write slot and wait together (`withSlots`). Compacting ranks after a priority leaves only moves ranks; every priority keeps its slot and its wait.
- **Storage, version 7**: `cooling_off_days` and `waited_days` (`INTEGER NOT NULL DEFAULT 0`) and `waiting_since` (`INTEGER`, nullable) are added. `cooling_ends_at` stays NOT NULL: while no wait runs it holds `added_at`, the value the app writes, so every row reads back as stored.
- **Migration 6 → 7** adds the columns and fills them in place: the days to wait are those from `added_at` to the stored end, rounded to whole days; priorities keep their slot and their end, with their wait running since they were added; every other active want is parked, with the whole calendar days since it was added as waited, in the zone of the injected `Clock` (computed in Kotlin, not SQL); a want that became a need keeps the days it waited as a want; every row whose wait doesn't run gets `cooling_ends_at = added_at`. The database is built with the app's `Clock`.
- **"Waiting period over" is sent only for a wait that ended in its slot**: a priority whose running wait ends after it started. A want that had already waited its full period when it got its slot isn't notified (a product rule on top of ADR-0042 and ADR-0049). The reflection nudge, like the rest, only concerns priorities.
- **Setting money aside** for a want without a slot is refused (`NotAPriority`); money it already holds can still be taken back (ADR-0049).

## Consequences

- Moving a want in and out of the slots within a day loses that day's part; the wait never gets shorter by juggling.
- The end of a running wait is computed in the zone in effect when the stretch starts, as `coolingEndsAt` always was at capture. Travelling across zones can shift it by the zone difference; pausing counts in the zone at that moment.
- The migration's rounding of the days to wait would only be wrong for an end set in a zone more than 12 hours from the one it was captured in; no such end exists.
- `initialCoolingEnd`, `coolingEndAfterPriceEdit` and `coolingEndAfterRulesChange` are replaced by `coolingOffDaysFor`, `startWait`, `pauseWait`, `waitAfterPriceEdit` and `waitAfterRulesChange`; `daysWaited` gives the "N of M days" the UI PR shows for a parked want.
- Version 7 can't be opened by an older app, as with every version change (ADR-0019).
