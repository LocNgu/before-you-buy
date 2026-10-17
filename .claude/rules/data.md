---
description: Room schema, DAOs, repositories and the settings DataStore in :core:data
paths:
  - "core/data/**/*"
  - "core/domain/src/**/item/ItemRepository.kt"
  - "core/domain/src/**/settings/**/*"
---

# Persistence (`:core:data`, ADR-0007, ADR-0018, ADR-0019, ADR-0032)

## Layout

- `db/`: entities, DAOs, `BeforeYouBuyDatabase` (+ `withPolicyRow`). `item/`: mappers + `RoomItemRepository`. `settings/`: `RoomPolicyRepository`, `DataStorePreferencesRepository`, `CoolingOffRulesJson`. `di/DataModule`: Hilt bindings (singletons: one database, one DataStore per file and process).
- Repository **interfaces live in `:core:domain`** (`item/ItemRepository`, `settings/PolicyRepository`, `settings/PreferencesRepository`); everything here is `internal`, including `DataModule` (Hilt handles internal modules across modules).

## Schema (version 1)

- Tables: `policy` (one row, id 1: currency + tiers JSON or null for defaults, ADR-0033), `items`, `reflections` (1:1, PK `item_id`), `decisions` (append-only). Both children reference `items(id)` with `ON DELETE CASCADE`. M2 adds `allocations` and `pool_entries` the same way.
- The policy row is created on every open with `INSERT OR IGNORE` (`withPolicyRow`): the currency is fixed on first start from the region and never follows it afterwards. Build every database (tests too) with `withPolicyRow`.
- **Never a stage column** (ADR-0007); `DaoTest` checks every table.
- Money: `*_minor` Long + currency text. Instants: epoch millis (sub-millisecond precision is dropped). Dates: ISO-8601 text. Enums: their name, read with a fallback in the mappers (ADR-0019, ADR-0032). `reasons_against`: a JSON array of strings.
- Schema JSON is exported to `core/data/schemas/` by the Room Gradle plugin on compile. **Commit it with every version change.** No destructive fallback; from the first public release on, each version change needs a `Migration` and a migration test (ADR-0019).

## Repositories

- Item writes are compare-and-set (`writeIfUnchanged`): `update(current, edited)` and `recordDecision(current, decision, result)` write only if the stored **row** still equals `current.toEntity()`, in one `withTransaction`; otherwise they write nothing and return false. `update` never changes id or status; `recordDecision` needs an active `current` and appends the decision + stores `result` atomically. Status changes go only through `applyDecision` + `recordDecision`. `add` takes active items only. `setReflection` returns false for a missing item. An item read with a fallback (unknown enum, unreadable date) never matches its row, so it is read-only (ADR-0032).
- `observeReflections()` is one query over `reflections` (id → answers), so the wishlist derives every stage without a flow per item.
- History: the ending decision is the last appended (max id), ordered by its `decided_at`, newest first.
- Every stored amount is in the policy currency: `add`/`update` require it. `PolicyRepository.changeCoolingOffRules(rules, changedAt, zone)` writes the tiers and `coolingEndAfterRulesChange` for active items in one transaction (targeted `setCoolingEnd`, so unreadable columns survive). `changeCurrency` relabels item prices, actual prices (`DecisionDao.setActualPrice`, the one decision write) and tiers in one transaction; null tiers stay null. Currencies must pass `isUsableCurrency`.
- Preferences (DataStore) store only values the user set; defaults are computed on read. Create the DataStore with `DataStorePreferencesRepository.createDataStore` (corruption handler: replace with empty). The flow uses `retryWhen`, not `catch`, so it survives a failed read, and emits nothing until a read succeeds (never defaults in place of the user's values).

## Tests

- Room: Robolectric + `Room.inMemoryDatabaseBuilder` via `db/DatabaseTest`. Raw SQL for odd states goes through `execSql`, which refreshes the invalidation tracker (otherwise observed queries don't re-emit).
- Atomicity: a trigger that aborts the item update; the decision must not be stored either. Races: concurrent `recordDecision`s on `Dispatchers.IO`, repeated over many items; exactly one per item may succeed.
- DataStore: plain JVM test with a `TemporaryFolder` file and `backgroundScope`. Only **one** DataStore per file may be active; a "restart" cancels and joins the first one's scope. A failed read is simulated with a delegating `DataStore` whose `data` throws once.
- Suspending `require`s: `testing/assertThrowsIllegalArgument`.
- Fixtures in `item/Fixtures.kt` use millisecond-precision instants, so round trips compare equal.
