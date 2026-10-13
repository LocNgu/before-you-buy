# ADR-0032: Storage layout — reasons against as JSON, append-only decisions, compare-and-set writes, settings read defensively

**Status**: accepted
**Type**: technical
**Date**: 2026-10-13

## Context

Issue #13 creates the first Room schema and the settings store. Some choices shape every later schema change and export (M3):

- `reasons_against` (a list per reflection) could be a child table or a JSON column.
- Decisions are history. Saving a decision changes the item too, and a notification action ("Let it go", "Wait") can race with the app: a decision or edit computed from an item read earlier must not overwrite a newer change.
- Settings hold values that depend on each other (the cooling-off tiers are in the app's currency) and must never stop the app from starting.

## Decision

- **Reasons against are a JSON array of strings** in `reflections.reasons_against`. They're always read and written together with the rest of the reflection, nothing queries single reasons, and one row per reflection keeps the upsert a single statement. A child table would need delete-and-reinsert in a transaction on every save. If insights (Later) ever need to query reasons, that's a migration to a child table.
- **Decisions are append-only.** The DAO has no update or delete. Rows go only with their item (`ON DELETE CASCADE`), when the user deletes it explicitly.
- **Item writes are compare-and-set.** `update(current, edited)` and `recordDecision(current, decision, result)` take the item as the caller read it and write only if the stored row still equals it, inside one transaction; otherwise they write nothing and return false. So an item can't be ended twice (notification and app at the same moment), a decision checked against an old price or cooling-off end isn't recorded, and an edit doesn't undo a "Wait". Comparing stored rows (not mapped items) also keeps a status written by a newer version from being overwritten. A version column was the alternative; comparing the row needs no extra field in the domain model.
- **`recordDecision` appends the decision and stores the new item state in one transaction**: both or neither. **`update` never changes the status.**
- **The ending decision of a history entry is the last one appended** (highest id), not the latest `decided_at`: a purchase may be dated back before an earlier "Wait".
- **Instants are epoch millis**, so read-back instants are truncated to milliseconds. **Dates are ISO-8601 text.**
- **Unknown stored values fall back** (ADR-0019): kind → `WANT`, status → `ACTIVE`, reflection choices → unanswered, decision source → `APP`. A decision of an unknown type is skipped, because no other type has the same fields; its history entry is hidden until the app knows the type.
- **Settings store only what the user set.** Defaults are computed on read. The currency defaults to the device region's (system locale, not the per-app language), else EUR, until the first setting is saved: every write also stores the currency in effect, so saved tiers never lose their currency to a region change. Usable currencies are ISO 4217 codes with fixed minor units (`isUsableCurrency`: no XXX, test codes or metals). Stored tiers apply only in their currency; otherwise, or if unreadable or invalid, the defaults in the current currency apply. An unreadable value reads as its default, a corrupt file is replaced by an empty one, and a failed read shows defaults while the flow retries.

## Consequences

- One reflection row per item; export (M3) can copy the JSON as is.
- Callers keep the item they read and pass it as `current`; when `update`/`recordDecision` return false they reload and show the current state.
- A corrupt settings file loses the user's settings (back to defaults) instead of crashing every start.
- Changing the currency without new tiers resets the effective tiers to the defaults in the new currency. Settings (M3) sets the currency first, then tiers in it, and applies ADR-0031 to waiting items.
- After a downgrade, an item whose ending decision has an unknown type is missing from history until the app is updated again. That is accepted as the cost of not guessing a type.
