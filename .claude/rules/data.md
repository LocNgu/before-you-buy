---
description: Room schema, DAOs, repositories and the settings DataStore in :core:data
paths:
  - "core/data/**/*"
  - "core/domain/src/**/item/ItemRepository.kt"
  - "core/domain/src/**/settings/**/*"
---

# Persistence (`:core:data`, ADR-0007, ADR-0018, ADR-0019, ADR-0032)

## Layout

- `db/`: entities, DAOs, `BeforeYouBuyDatabase` (+ `withPolicyRow`). `item/`: mappers + `RoomItemRepository`. `notifications/`: `RoomNotificationLogRepository`. `pool/`: `RoomPoolRepository` + its mappers (`.claude/rules/pool.md`). `image/`: `FileItemImageStore` (photos in `filesDir/images`, ADR-0038). `settings/`: `RoomPolicyRepository`, `DataStorePreferencesRepository`, `CoolingOffRulesJson`. `di/DataModule`: Hilt bindings (singletons: one database, one DataStore per file and process).
- Repository **interfaces live in `:core:domain`** (`item/ItemRepository`, `item/ItemImageStore`, `notifications/NotificationLogRepository`, `pool/PoolRepository`, `settings/PolicyRepository`, `settings/PreferencesRepository`); everything here is `internal`, including `DataModule` (Hilt handles internal modules across modules).

## Schema (version 4)

- Tables: `policy` (one row, id 1: currency + tiers JSON or null for defaults, ADR-0033), `items`, `reflections` (1:1, PK `item_id`), `decisions` (append-only; `note` added in version 2), `item_notifications` (1:1, PK `item_id`, added in version 3: when each notification kind was sent, `.claude/rules/notifications.md`). The children reference `items(id)` with `ON DELETE CASCADE`. Version 4 adds `allowance` (one row, id 1, absent until set up) and `pool_entries` (the append-only ledger; `amount_minor` is the signed change; `accrual_month` is unique, so a month can't be credited twice; `item_id` is `ON DELETE SET NULL`, a purchase stays when its history entry is deleted). #23 adds `allocations`.
- The policy row is created on every open with `INSERT OR IGNORE` (`withPolicyRow`): the currency is fixed on first start from the region and never follows it afterwards. Build every database (tests too) with `withPolicyRow`.
- **Never a stage column** (ADR-0007); `DaoTest` checks every table.
- Money: `*_minor` Long + currency text. Instants: epoch millis (sub-millisecond precision is dropped). Dates: ISO-8601 text. Enums: their name, read with a fallback in the mappers (ADR-0019, ADR-0032). `reasons_against`: a JSON array of strings.
- Schema JSON is exported to `core/data/schemas/` by the Room Gradle plugin on compile. **Commit it with every version change.** No destructive fallback; each version change needs a `Migration` (add it to `MIGRATIONS` in `BeforeYouBuyDatabase.kt`) and a test in `db/MigrationTest`, which builds the old version from its committed JSON, fills rows and opens it with the app's Room setup (Room validates the result) (ADR-0019).

## Repositories

- Item writes are compare-and-set (`writeIfUnchanged`): `update(current, edited)` and `recordDecision(current, decision, result)` write only if the stored **row** still equals `current.toEntity()`, in one `withTransaction`; otherwise they write nothing and return false. `update` never changes id or status; `recordDecision` needs an active `current` and appends the decision + stores `result` atomically. Status changes go only through `applyDecision` + `recordDecision`. `add` takes active items only. `setReflection` returns false for a missing item. An item read with a fallback (unknown enum, unreadable date) never matches its row, so it is read-only (ADR-0032).
- Photos (ADR-0038): `ItemImageStore.import` decodes, turns upright (EXIF), scales to ≤ `MAX_IMAGE_SIZE_PX` and writes a new JPEG `<uuid>.jpg` (via a `.partial` file and rename), so no metadata or location survives; it returns null for anything unreadable, never throws for it. `fileFor` accepts only names of that form. `deleteUnreferenced(referenced, writtenBefore)` uses `unreferencedImages` (domain) on file mtimes; `ItemRepository.imageRefs()` is every item's ref, ended items included. Never delete a photo when an item changes.
- `observeReflections()` is one query over `reflections` (id → answers), so the wishlist derives every stage without a flow per item.
- History: the ending decision is the last appended (max id), ordered by its `decided_at`, newest first. The query names every `decisions` column with the `decision_` prefix (`HistoryRow`); a column added to `DecisionEntity` must be added there too, or it reads as null (the reject `note` once did).
- `delete(current)` removes an ended item (active throws) compare-and-set, like `update`; reflection and decisions go by `ON DELETE CASCADE`, the photo by the next sweep.
- Every stored amount is in the policy currency: `add`/`update` require it. `PolicyRepository.changeCoolingOffRules(rules, changedAt, zone)` writes the tiers and `coolingEndAfterRulesChange` for active items in one transaction (targeted `setCoolingEnd`, so unreadable columns survive). `changeCurrency` relabels item prices, actual prices (`DecisionDao.setActualPrice`, the one decision write), tiers, the monthly allowance and every pool entry (`PoolDao.setEntryAmount`, the one ledger update) in one transaction; null tiers stay null. Currencies must pass `isUsableCurrency`.
- Preferences (DataStore) store only values the user set; defaults are computed on read. Create the DataStore with `DataStorePreferencesRepository.createDataStore` (corruption handler: replace with empty). The flow uses `retryWhen`, not `catch`, so it survives a failed read, and emits nothing until a read succeeds (never defaults in place of the user's values).

## Tests

- Room: Robolectric + `Room.inMemoryDatabaseBuilder` via `db/DatabaseTest`. Raw SQL for odd states goes through `execSql`, which refreshes the invalidation tracker (otherwise observed queries don't re-emit).
- Atomicity: a trigger that aborts the item update; the decision must not be stored either. Races: concurrent `recordDecision`s on `Dispatchers.IO`, repeated over many items; exactly one per item may succeed.
- DataStore: plain JVM test with a `TemporaryFolder` file and `backgroundScope`. Only **one** DataStore per file may be active; a "restart" cancels and joins the first one's scope. A failed read is simulated with a delegating `DataStore` whose `data` throws once.
- Photos: `FileItemImageStore` with Robolectric `@GraphicsMode(NATIVE)` and a `TemporaryFolder`; sources are `file:` URIs, EXIF set with `android.media.ExifInterface`.
- Suspending `require`s: `testing/assertThrowsIllegalArgument`.
- Fixtures in `item/Fixtures.kt` use millisecond-precision instants, so round trips compare equal.
