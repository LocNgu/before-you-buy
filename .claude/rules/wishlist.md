---
description: Wishlist, item detail and edit — rules in :core:domain, screens and view models in :app
paths:
  - "core/domain/src/**/wishlist/**/*"
  - "app/src/main/kotlin/**/feature/wishlist/**/*"
  - "app/src/test/kotlin/**/feature/wishlist/**/*"
  - "app/src/main/kotlin/**/navigation/**/*"
---

# Wishlist and item detail (brief §6.3, ADR-0005, ADR-0007, ADR-0013, ADR-0018, ADR-0036)

## Rules (`:core:domain` `wishlist/`)

- `wishlistOf(items, reflections, now, zone)` groups active items by `stageOf` in `WISHLIST_STAGE_ORDER` (Ready, Needs reflection, Saving, Cooling off), leaves empty groups out and sorts each group by `coolingEndsAt`, then `addedAt`, then id. Until savings exist (M2) an item's allocation is its own price.
- `daysLeft(item, now, zone)`: calendar dates until the end (ADR-0018), at least 1 until the end instant has passed, 0 once cooled off.
- `untilWishlistChanges(items, now, zone)`: time to the next cooling-off end or midnight, whichever is first. Detail uses it with its one item.
- `itemDetailOf(item, reflection, now, zone)`: stage, days left, `wantedForDays` (calendar days since `addedAt`), `reflected`, and `linkProminent` = cooled off. While cooling off the link is only in the overflow menu (ADR-0013).
- `webLink(link)`: what "Open link" opens. `http`/`https` as is, no scheme → `https://`, any other scheme (`intent:`, `file:`, `javascript:`) → null: copy only.
- Edit reuses quick add's `CaptureForm` and `checkCapture`. `editFormOf(item, locale)` prefills the **exact** price (`formatForInput`). `editedItem(item, form, policy, locale, at, zone)` applies the price first (`coolingEndAfterPriceEdit`: only extends), then the kind (`changeKind`). `waitChange(current, edited, now)` is what the form says under the price: `Extended`, `Starts` (need → want) or `None`.

## Screens and view models (`:app` `feature/wishlist/`)

- `WishlistViewModel` and `ItemDetailViewModel` recompute on the clock, not only on database changes: after each `untilWishlistChanges` they read `clock.instant()` again. Test time with `MutableClock` + `advanceTimeBy`.
- `groups == null` / `detail == null` means "not loaded yet": show nothing, never the empty state. An item ended or deleted meanwhile is `gone` on detail and edit.
- Each screen has a stateless `…Content` used for screenshots. Row: name, price, stage chip, countdown / "Cooling-off over" / "Ready to decide"; tapping opens detail.
- Detail order: header (name, price, stage, countdown, "Wanted for N days", need line) → **reasons** (answers given so far, then a prompt card while the required ones are missing) → details → actions. "Open link" as a text button only once cooled off; copy/open always in the overflow menu. "Mark as need" asks first. No image yet (next slice of #16); the reflection (#17) and decision (#18) entry points aren't wired yet.
- Per-item view models get the id via assisted injection (`hiltViewModel<VM, VM.Factory> { it.create(id) }`); every navigation entry has its own ViewModel store (ADR-0036). Writes go through `ItemRepository.update(current, edited)` (compare-and-set): on `false` say the item changed meanwhile, never retry silently.
- Edit: fields from `feature/capture/ItemFields.kt` (shared with quick add), all shown. Save closes the screen; back discards. The form is filled once from the first load; later item changes don't overwrite typing, and saving applies the form to the newest item.
- Quick add is hosted once, in `AppNavigation`, outside the entries: `saved` events reach one collector and two entries can compose during a transition. Screens get `onAdd` and the shared `SnackbarHostState`.

## Tests

- Rules: `WishlistTest`, `ItemDetailTest`, `ItemEditTest` (JVM). View models: `WishlistViewModelTest`, `ItemDetailViewModelTest`, `ItemEditViewModelTest` (fake repository with compare-and-set `update` and `conflictOnNextUpdate`, `MutableClock`). Real app: `WishlistFlowTest`, `ItemDetailFlowTest`. Screenshots: `WishlistScreenshotTest`, `ItemDetailScreenshotTest`, `ItemEditScreenshotTest` per state × en/de.
