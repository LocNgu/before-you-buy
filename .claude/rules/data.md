---
description: Room schema, DAOs, repositories and the settings DataStore in :core:data
paths:
  - "core/data/**/*"
  - "core/domain/src/**/item/ItemRepository.kt"
  - "core/domain/src/**/settings/**/*"
---

# Persistence (`:core:data`, ADR-0007, ADR-0018, ADR-0019, ADR-0032)

## Layout

- `db/`: entities, DAOs, `BeforeYouBuyDatabase`. `item/`: mappers + `RoomItemRepository`. `settings/`: `DataStoreSettingsRepository` + `CoolingOffRulesJson`. `di/DataModule`: Hilt bindings (singletons; one DataStore per file and process).
- Repository **interfaces live in `:core:domain`** (`item/ItemRepository`, `settings/SettingsRepository`); implementations and everything Room/DataStore are `internal` here. Only `DataModule` is public.

## Schema (version 1)

- Tables: `items`, `reflections` (1:1, PK `item_id`), `decisions` (append-only). Both children reference `items(id)` with `ON DELETE CASCADE`. M2 adds `allocations` and `pool_entries` the same way.
- **Never a stage column** (ADR-0007); `DaoTest` checks every table.
- Money: `*_minor` Long + currency text. Instants: epoch millis (sub-millisecond precision is dropped). Dates: ISO-8601 text. Enums: their name, read with a fallback in the mappers (ADR-0019, ADR-0032). `reasons_against`: a JSON array of strings.
- Schema JSON is exported to `core/data/schemas/` by the Room Gradle plugin on compile. **Commit it with every version change.** No destructive fallback; from the first public release on, each version change needs a `Migration` and a migration test (ADR-0019).

## Repositories

- `ItemRepository.update` never changes the status and returns false if the stored status differs (raced with a decision) or the item is gone. `recordDecision` appends the decision and stores the item in one `withTransaction`, only while the stored item is `ACTIVE`; otherwise it writes nothing and returns false. Status changes go only through `applyDecision` + `recordDecision`.
- History: the ending decision is the last appended (max id), ordered by its `decided_at`, newest first.
- Settings store only values the user set; defaults are computed on read (currency from the system locale's region, else EUR; tiers = `CoolingOffRules.defaults(currency)`). Stored tiers apply only in their own currency. Unreadable values read as defaults; `setCoolingOffRules` throws for rules not in the current currency.

## Tests

- Room: Robolectric + `Room.inMemoryDatabaseBuilder` via `db/DatabaseTest`. Raw SQL for odd states goes through `execSql`, which refreshes the invalidation tracker (otherwise observed queries don't re-emit).
- Atomicity: a trigger that aborts the item update; the decision must not be stored either.
- DataStore: plain JVM test with a `TemporaryFolder` file and `backgroundScope`. Only **one** DataStore per file may be active; a "restart" cancels and joins the first one's scope.
- Fixtures in `item/Fixtures.kt` use millisecond-precision instants, so round trips compare equal.
