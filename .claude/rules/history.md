---
description: History — "Bought" and "Decided against", totals, periods and "Want it again?" in :core:domain, screens and view models in :app
paths:
  - "core/domain/src/**/history/**/*"
  - "app/src/main/kotlin/**/feature/history/**/*"
  - "app/src/test/kotlin/**/feature/history/**/*"
---

# History (brief §6.6, ADR-0012, ADR-0018, ADR-0033, ADR-0034, ADR-0041)

## Rules (`:core:domain` `history/`)

- Input is `ItemRepository.observeHistory(status)`: ended items with the decision appended last, newest `decided_at` first. `boughtOf` / `decidedAgainstOf(entries, currency, period, now, zone)` keep that order, leave out entries whose status and ending decision don't fit (defensive reads, ADR-0032) and **throw for any amount not in `currency`** (the app's one currency, ADR-0033). Callers check `allInCurrency` first and wait for a matching read (a currency change relabels items and policy in one transaction, but they're observed apart).
- `BoughtEntry`: `paid` (actual price), `wishlistPrice` only if it differs (exact comparison), `boughtOn` (the decision date: purchases may be dated back, ADR-0040), `consideredForDays` (calendar days from `addedAt`, never negative), `early` (`BUY_EARLY`), `need` (`BUY_NEED`). `DecidedAgainstEntry`: `price` (the item's), `decidedOn`, `note`.
- `DecidedAgainst.total` is the exact sum of the listed prices (`total(currency)`), zero when empty; display rounds once (ADR-0034).
- `HistoryPeriod` (`THIS_MONTH`, `THIS_YEAR`, `ALL`) compares calendar month/year of the decision date in the zone with today's. `untilHistoryChanges` is the time to the next midnight.
- `historyDetailOf(item, decisions, reflection, zone)`: the entry by the **last** decision, or null for an active item or one whose ending decision doesn't fit.
- `wantedAgain(item, policy, now, zone)`: rejected items only. A new `WANT` (`ItemId.NEW`) with name, price, link, photo, category, notes and a still-future target date, `addedAt = now`, `coolingOffDays` from the tiers, parked like a new capture (ADR-0049). Never copies answers, kind, `recheckAt`, priority or `markedAsNeedAt`.

## Screens and view models (`:app` `feature/history/`)

- `HistoryViewModel`: tab and period live in the view model (`selectTab`, `selectPeriod`); both lists are computed for the period, recomputed at midnight (clock loop, like the wishlist). `bought == null` / `decidedAgainst == null` is "not loaded": show nothing.
- `HistoryContent` (stateless): tabs "Bought" · "Decided against", then the period chips (All, This month, This year), then the list. The total is the first row of "Decided against", plain `bodyLarge` text, only when the period has entries; empty periods show `EmptyState` (the body only for All). No badges, colours, icons or celebration on the total (ADR-0012), and never put it on a surface that shows Ready items.
- Rows: name, then date, "Considered for N days", "Wishlist price: …" and the "Bought early" / "Need" chips (bought) or date and note, two lines at most (decided against); the amount on the right. Tapping opens `HistoryItem(itemId)`.
- `HistoryItemViewModel` (assisted id, ADR-0037) combines item, decisions and reflection. `wantAgain()` (decided against only) adds `wantedAgain(...)` and emits `WantedAgain(id)`; navigation replaces the history entry with the new item's detail. `delete()` uses the compare-and-set `ItemRepository.delete`; on `false` it says the item changed meanwhile. While deleting, the state keeps the last detail, so the screen never flashes "gone" before it closes. `busy` disables both actions while one is written.
- Detail: header (name, amount, the same lines as the row), the early reason or "Why not", **"Your reasons at the time"** (`answersOf` from item detail), details, then "Want it again?" (outlined, confirm dialog). "Delete from history" only in the overflow menu, always with a confirm.
- Opened from the "History" button in Home's top bar (ADR-0043).

## Tests

- Rules: `HistoryTest` (JVM). View models: `HistoryViewModelTest`, `HistoryItemViewModelTest` (`FakeItemRepository.observeHistory`, `delete`, `deleted`, `conflictOnNextDelete`; `MutableClock`). Real app: `HistoryFlowTest` (total and note, want again keeps the entry, delete asks first). Screenshots: `HistoryScreenshotTest` both tabs, a filtered period, empty states, loading, bought/decided-against detail, gone × en/de. Repository: `RoomItemRepositoryTest` (delete cascades and is compare-and-set; history keeps the reject note).
