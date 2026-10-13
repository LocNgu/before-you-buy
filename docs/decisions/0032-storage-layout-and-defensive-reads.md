# ADR-0032: Storage layout — reasons against as JSON, append-only decisions, guarded writes, settings read defensively

**Status**: accepted
**Type**: technical
**Date**: 2026-10-13

## Context

Issue #13 creates the first Room schema and the settings store. Some choices shape every later schema change and export (M3):

- `reasons_against` (a list per reflection) could be a child table or a JSON column.
- Decisions are history. Saving a decision changes the item too, and a notification action ("Let it go") can race with the app.
- Settings hold values that depend on each other (the cooling-off tiers are in the app's currency) and must never stop the app from starting.

## Decision

- **Reasons against are a JSON array of strings** in `reflections.reasons_against`. They're always read and written together with the rest of the reflection, nothing queries single reasons, and one row per reflection keeps the upsert a single statement. A child table would need delete-and-reinsert in a transaction on every save. If insights (Later) ever need to query reasons, that's a migration to a child table.
- **Decisions are append-only.** The DAO has no update or delete. Rows go only with their item (`ON DELETE CASCADE`), when the user deletes it explicitly.
- **`recordDecision` writes in one transaction and only while the stored item is active**: it appends the decision and stores the item's new state, or writes nothing and returns false. An item can't be ended twice by a notification and the app at the same moment.
- **`update` never changes the status** and writes only while the stored status equals the item's. An edit that races with a decision doesn't undo it. Both guards compare the stored text, so a status written by a newer version isn't overwritten.
- **The ending decision of a history entry is the last one appended** (highest id), not the latest `decided_at`: a purchase may be dated back before an earlier "Wait".
- **Instants are epoch millis**, so read-back instants are truncated to milliseconds. **Dates are ISO-8601 text.**
- **Unknown stored values fall back** (ADR-0019): kind → `WANT`, status → `ACTIVE`, reflection choices → unanswered, decision source → `APP`. A decision of an unknown type is skipped, because no other type has the same fields; its history entry is hidden until the app knows the type.
- **Settings store only what the user set.** Defaults are computed on read. The currency defaults to the device region's (system locale, not the per-app language), else EUR. Stored cooling-off tiers apply only in the currency they were set in; otherwise, or if unreadable or invalid, the defaults in the current currency apply. An unreadable value or file reads as its default.

## Consequences

- One reflection row per item; export (M3) can copy the JSON as is.
- Callers check the `Boolean` from `update`/`recordDecision` and reload when it's false.
- Changing the currency without new tiers resets the effective tiers to the defaults in the new currency. Settings (M3) sets the currency first, then tiers in it, and applies ADR-0031 to waiting items.
- After a downgrade, an item whose ending decision has an unknown type is missing from history until the app is updated again. That is accepted as the cost of not guessing a type.
