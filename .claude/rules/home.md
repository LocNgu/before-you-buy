---
description: Home dashboard — sections in :core:domain, screen and view model in :app
paths:
  - "core/domain/src/**/home/**/*"
  - "app/src/main/kotlin/**/feature/home/**/*"
  - "app/src/test/kotlin/**/feature/home/**/*"
---

# Home (brief §6.1, ADR-0012, ADR-0014, ADR-0040, ADR-0043, ADR-0045, ADR-0046, ADR-0047, ADR-0049, ADR-0052)

## Rules (`:core:domain` `home/`)

- `homeOverviewOf(items, reflections, savings, now, zone)` is derived from `wishlistOf`, never a second set of stage rules. `toDecide`: of the priorities and needs not waiting after "Wait", the Ready ones (exactly `isDueForDecision`), then the Needs-reflection ones, each soonest cooling-off end first (a need by `addedAt`); a parked want is never in it (ADR-0049). `activeCount`: every active item, waiting and parked ones included (`Wishlist.entries`). `nextCoolingEnd`: the cooling-off priority whose wait ends first (only priorities wait), or null. `priorities`: `Wishlist.priorities` (entries carry `savings` with fun money), the whole answer to "What am I saving for?": only priorities get money, so a parked want is never listed, not even one still holding money from before it lost its slot (ADR-0049, ADR-0052). There is no list of wants by progress any more.
- Nothing from history goes into the overview: the decided-against total never sits next to Ready items (moral licensing, ADR-0012).

## Screen and view model (`:app` `feature/home/`)

- `HomeViewModel` recomputes on the clock like `WishlistViewModel` (`untilWishlistChanges`), not only on database changes. `overview == null` is "not loaded": show nothing. `money` is `funMoneyOf(allowance, entries, savings)` (`.claude/rules/pool.md`), null until loaded; both use `observeSavings`.
- `HomeContent` (stateless, screenshots): top bar with the app name and a "History" text button; then "To decide" (only when non-empty; rows with name, stage chip, `StageStatus` unless Ready, price; tap → `onOpenDecision`), "Considering" (count plural or the first-start line, "Next waiting period to end" with name and `CountdownText`, an "Open wishlist" text button), then "Priorities", always shown (ADR-0052): `PriorityRow` with `PriorityRank` "#1", name, `SavingsProgressBar` or the price without fun money; tap → `onOpenItem` (ADR-0047); without priorities one quiet line, the wishlist's `priorities_empty_hint`, whether fun money is set up or not. No "Saving for" heading. Then "Fun money" (ADR-0045): not set up → one line and "Set up"; set up → `FunMoneyAmounts` ("Fun money for wants" / "Set aside for wants" / "Not yet set aside"), and "Change or adjust". Both buttons call `onOpenFunMoney`. Never "available" or "spend" (ADR-0014). The quick add FAB and the shared `SnackbarHostState` as on the wishlist.
- Navigation: Home is the back stack's root. The fun money section opens `FunMoney`. Priority rows open `ItemDetail`. "To decide" opens `ItemDecision` directly (for an item without its answers the decision screen offers the questions); after Buy/Reject the snackbar shows on Home. No Settings entry until the Settings screen exists (#26 adds it to the top bar). No bottom bar (ADR-0043).

## Tests

- Rules: `HomeOverviewTest` (JVM). View model: `HomeViewModelTest` (`FakeItemRepository`, `FakePoolRepository`, `MutableClock`). Real app: `HomeFlowTest` (wishlist and history open from Home and back returns; a new want is counted with its countdown; a need is to decide and opens the decision; deciding against from Home). Screenshots: `HomeScreenshotTest` first start (fun money not set up), typical, nothing to decide, priorities (fun money not set up), no priorities (set up, a parked want holding money), loading × en/de × light/dark.
