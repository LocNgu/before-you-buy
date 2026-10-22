---
description: Home dashboard — sections in :core:domain, screen and view model in :app
paths:
  - "core/domain/src/**/home/**/*"
  - "app/src/main/kotlin/**/feature/home/**/*"
  - "app/src/test/kotlin/**/feature/home/**/*"
---

# Home (brief §6.1, ADR-0012, ADR-0014, ADR-0040, ADR-0043)

## Rules (`:core:domain` `home/`)

- `homeOverviewOf(items, reflections, now, zone)` is derived from `wishlistOf`, never a second set of stage rules. `toDecide`: the Ready group that isn't waiting (exactly `isDueForDecision`, needs included), then the Needs-reflection group. `activeCount`: every active item, waiting ones included. `nextCoolingEnd`: the first entry of the Cooling-off group (soonest end), or null.
- Nothing from history goes into the overview: the decided-against total never sits next to Ready items (moral licensing, ADR-0012).

## Screen and view model (`:app` `feature/home/`)

- `HomeViewModel` recomputes on the clock like `WishlistViewModel` (`untilWishlistChanges`), not only on database changes. `overview == null` is "not loaded": show nothing.
- `HomeContent` (stateless, screenshots): top bar with the app name and a "History" text button; then "To decide" (only when non-empty; rows with name, stage chip, `StageStatus` unless Ready, price; tap → `onOpenDecision`), "Considering" (count plural or the first-start line, "Next waiting period to end" with name and `CountdownText`, an "Open wishlist" text button), then the M2 placeholders "Saving for" / "Monthly allowance" as a heading and one quiet line, no amounts, not tappable (ADR-0014). The quick add FAB and the shared `SnackbarHostState` as on the wishlist.
- Navigation: Home is the back stack's root. "To decide" opens `ItemDecision` directly (for an item without its answers the decision screen offers the questions); after Buy/Reject the snackbar shows on Home. No Settings entry until the Settings screen exists (#26 adds it to the top bar). No bottom bar (ADR-0043).

## Tests

- Rules: `HomeOverviewTest` (JVM). View model: `HomeViewModelTest` (`FakeItemRepository`, `MutableClock`). Real app: `HomeFlowTest` (wishlist and history open from Home and back returns; a new want is counted with its countdown; a need is to decide and opens the decision; deciding against from Home). Screenshots: `HomeScreenshotTest` first start, typical, nothing to decide, loading × en/de × light/dark.
