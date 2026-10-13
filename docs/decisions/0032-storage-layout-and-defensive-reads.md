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
- **`recordDecision` appends the decision and stores the new item state in one transaction**: both or neither. **`update` never changes the status.** `setReflection` writes only if the item exists and returns false otherwise (e.g. it was deleted while the reflection screen was open).
- **The ending decision of a history entry is the last one appended** (highest id), not the latest `decided_at`: a purchase may be dated back before an earlier "Wait".
- **Instants are epoch millis**, so read-back instants are truncated to milliseconds. **Dates are ISO-8601 text.**
- **Unknown enum names fall back** (ADR-0019): kind → `WANT`, reflection choices → unanswered, decision source → `APP`. An unknown status reads as `ACTIVE` in `observeById`, but the lists filter on the stored text, so such an item is in neither the active list nor history. A decision of an unknown type is skipped, because no other type has the same fields; its history entry is hidden until the app knows the type. An item read with a fallback (unknown kind or status, unreadable target date) never equals its stored row, so compare-and-set writes refuse it: it is read-only until the app understands it, and is never overwritten with a guess.
- **Only enum names and dates are read defensively.** Other values (currency codes, amounts, a purchase's actual price) were validated by the domain model before this app wrote them, so the mappers don't catch per row. An impossible row fails the query that reads it rather than being hidden.
- **Settings store only what the user set.** Defaults are computed on read. The currency defaults to the device region's (system locale, not the per-app language), else EUR, until the first setting is saved: every write also stores the currency in effect, so saved tiers never lose their currency to a region change. Usable currencies are ISO 4217 codes with fixed minor units (`isUsableCurrency`: no XXX, test codes or metals). Stored tiers apply only in their currency; otherwise, or if unreadable or invalid, the defaults in the current currency apply. An unreadable value reads as its default and a corrupt file is replaced by an empty one. A failed read is retried without emitting anything: showing defaults would show onboarding to a returning user, and a save from that screen would overwrite their currency.

## Consequences

- One reflection row per item; export (M3) can copy the JSON as is.
- Callers keep the item they read and pass it as `current`; when `update`/`recordDecision` return false they reload and show the current state.
- A corrupt settings file loses the user's settings (back to defaults) instead of crashing every start.
- **Open (owner):** ADR-0031 says Settings stores the recomputed cooling-off ends "with the new tiers in one transaction". The tiers live in DataStore and the items in Room, so that can't be one transaction. How a tier change (and a currency change, which resets the effective tiers) reaches waiting items is to be decided before Settings (M3) changes tiers.
- Changing the currency without new tiers resets the effective tiers to the defaults in the new currency. Settings (M3) sets the currency first, then tiers in it, and applies ADR-0031 to waiting items.
- After a downgrade, an item whose ending decision has an unknown type is missing from history until the app is updated again. That is accepted as the cost of not guessing a type.
