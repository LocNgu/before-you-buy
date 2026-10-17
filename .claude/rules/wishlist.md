---
description: Wishlist grouping and sorting in :core:domain, wishlist screen and view model in :app
paths:
  - "core/domain/src/**/wishlist/**/*"
  - "app/src/main/kotlin/**/feature/wishlist/**/*"
  - "app/src/test/kotlin/**/feature/wishlist/**/*"
  - "app/src/main/kotlin/**/navigation/**/*"
---

# Wishlist (brief §6.3, ADR-0007, ADR-0013, ADR-0018)

## Rules (`:core:domain` `wishlist/`)

- `wishlistOf(items, reflections, now, zone)` groups active items by `stageOf` in `WISHLIST_STAGE_ORDER` (Ready, Needs reflection, Saving, Cooling off), leaves empty groups out and sorts each group by `coolingEndsAt`, then `addedAt`, then id. Until savings exist (M2) an item's allocation is its own price.
- `daysLeft(item, now, zone)`: calendar dates until the end (ADR-0018), at least 1 until the end instant has passed, 0 once cooled off.
- `untilWishlistChanges(items, now, zone)`: time to the next cooling-off end or midnight, whichever is first.

## Screen and view model (`:app` `feature/wishlist/`)

- `WishlistViewModel` combines `observeActive()` and `observeReflections()` and recomputes on the clock, not only on database changes: after each `untilWishlistChanges` it reads `clock.instant()` again. Test time with `MutableClock` + `advanceTimeBy`.
- `groups == null` means "not loaded yet": show nothing, never the empty state. `WishlistContent` is the stateless screen used for screenshots.
- Row: name, price, stage chip, then a countdown while cooling off, "Cooling-off over" for Needs reflection, "Ready to decide" for Ready. No thumbnail, no link yet (image slice of #16).
- Quick add is hosted once, in `AppNavigation`, not per screen: `saved` events reach one collector and two entries can compose during a transition. Screens get `onAdd` and the shared `SnackbarHostState`. Don't add a per-entry ViewModel store decorator without revisiting this.

## Tests

- Rules: `WishlistTest` (JVM). View model: `WishlistViewModelTest` (fake repository, `MutableClock`). `WishlistFlowTest` drives the real app. Screenshots: `WishlistScreenshotTest` per state × en/de.
