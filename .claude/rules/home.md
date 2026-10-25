---
description: Home dashboard — sections in :core:domain, screen and view model in :app
paths:
  - "core/domain/src/**/home/**/*"
  - "app/src/main/kotlin/**/feature/home/**/*"
  - "app/src/test/kotlin/**/feature/home/**/*"
---

# Home (brief §6.1, ADR-0012, ADR-0014, ADR-0040, ADR-0043, ADR-0045, ADR-0046, ADR-0047)

## Rules (`:core:domain` `home/`)

- `homeOverviewOf(items, reflections, savings, now, zone)` is derived from `wishlistOf`, never a second set of stage rules. `toDecide`: the Ready group that isn't waiting (exactly `isDueForDecision`, needs included), then the Needs-reflection group. `activeCount`: every active item, waiting ones included. `nextCoolingEnd`: the first entry of the Cooling-off group (soonest end), or null. `priorities`: every priority by rank (`prioritiesIn`, entries carry `savings` with fun money). `savingFor`: only without priorities, up to `SAVING_FOR_SHOWN` (3) active wants with their `SavingsProgress`, the highest fraction set aside first, then oldest; never needs; empty without fun money (ADR-0046, ADR-0047).
- Nothing from history goes into the overview: the decided-against total never sits next to Ready items (moral licensing, ADR-0012).

## Screen and view model (`:app` `feature/home/`)

- `HomeViewModel` recomputes on the clock like `WishlistViewModel` (`untilWishlistChanges`), not only on database changes. `overview == null` is "not loaded": show nothing. `money` is `funMoneyOf(allowance, entries, savings)` (`.claude/rules/pool.md`), null until loaded; both use `observeSavings`.
- `HomeContent` (stateless, screenshots): top bar with the app name and a "History" text button; then "To decide" (only when non-empty; rows with name, stage chip, `StageStatus` unless Ready, price; tap → `onOpenDecision`), "Considering" (count plural or the first-start line, "Next waiting period to end" with name and `CountdownText`, an "Open wishlist" text button), then "Priorities" once there is one (ADR-0047: `PriorityRow` with `PriorityRank` "#1", name, `SavingsProgressBar` or the price without fun money; tap → `onOpenItem`), otherwise "Saving for" (ADR-0046: rows with name and `SavingsProgressBar`, tap → `onOpenItem`; without fun money "Set up fun money to set money aside for your wants."; set up but no wants, one quiet line), then "Fun money" (ADR-0045): not set up → one line and "Set up"; set up → `FunMoneyAmounts` ("Fun money for wants" / "Set aside for wants" / "Not yet set aside"), and "Change or adjust". Both buttons call `onOpenFunMoney`. Never "available" or "spend" (ADR-0014). The quick add FAB and the shared `SnackbarHostState` as on the wishlist.
- Navigation: Home is the back stack's root. The fun money section opens `FunMoney`. "Saving for" rows open `ItemDetail`. "To decide" opens `ItemDecision` directly (for an item without its answers the decision screen offers the questions); after Buy/Reject the snackbar shows on Home. No Settings entry until the Settings screen exists (#26 adds it to the top bar). No bottom bar (ADR-0043).

## Tests

- Rules: `HomeOverviewTest` (JVM). View model: `HomeViewModelTest` (`FakeItemRepository`, `FakePoolRepository`, `MutableClock`). Real app: `HomeFlowTest` (wishlist and history open from Home and back returns; a new want is counted with its countdown; a need is to decide and opens the decision; deciding against from Home). Screenshots: `HomeScreenshotTest` first start (fun money not set up), typical, nothing to decide and priorities (set up), loading × en/de × light/dark.
