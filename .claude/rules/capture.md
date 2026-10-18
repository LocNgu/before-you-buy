---
description: Quick add (capture) — form rules in :core:domain and the sheet + view model in :app
paths:
  - "core/domain/src/**/capture/**/*"
  - "app/src/main/kotlin/**/feature/capture/**/*"
  - "app/src/test/kotlin/**/feature/capture/**/*"
  - "app/src/main/kotlin/**/feature/home/**/*"
---

# Capture (brief §6.2, ADR-0009, ADR-0015, ADR-0035)

## Rules (`:core:domain` `capture/`)

- `CaptureForm` holds the fields as typed. `checkCapture(form, currency, locale)` says whether the name is missing and how the price reads (`parseMoney`, so both `1.234,56` and `1,234.56` work, ADR-0027). `capturedItem(form, policy, locale, addedAt, zone)` builds the new item: trimmed text, blank optional fields → null, `ItemId.NEW`, cooling-off end from `initialCoolingEnd` (a `NEED` ends at `addedAt`). It throws for an invalid form.
- `coolingOffDays(item, zone)` is what the snackbar says: calendar days from `addedAt` to `coolingEndsAt` (0 for a need).
- **No reflection questions in capture** (ADR-0009). "More details" is only category, notes, target date.

## Sheet and view model (`:app` `feature/capture/`)

- `QuickAddViewModel` (Hilt, `hiltViewModel()` from `androidx.hilt:hilt-lifecycle-viewmodel-compose`) owns the sheet: `open(prefill)` starts a fresh form, `dismiss()` discards it, `save()` stores via `ItemRepository.add` and emits `QuickAddSaved` on `saved`. The form stays in place while the sheet slides away and is reset on the next `open`.
- Errors show once a field has held text (typed or prefilled); Save is disabled until valid; "Done" on the price keyboard saves a valid form (ADR-0035).
- The view model needs the UI's locale to read prices: `QuickAddHost` passes `currentLocale()` via `setLocale`. Until then nothing can be saved.
- The fields (`NameField`, `PriceField`, `LinkField`, `NeedToggle`, `DetailFields`) and `ItemFormActions` live in `ItemFields.kt` and are shared with item edit (`.claude/rules/wishlist.md`). Change them there, for both.
- A screen offers quick add with `QuickAddButton` (FAB) and an `onAdd` that calls `QuickAddViewModel.open()`. `QuickAddHost(viewModel, snackbarHostState)` is composed **once**, in `AppNavigation`, with the `SnackbarHostState` the screens share: `saved` is a channel, and each event reaches one collector.
- Share capture (#15) opens it with `QuickAddPrefill(name, price, link)`; prefill the price with `Money.formatForInput(locale)`, never `format`.
- Snackbar: "Added. You can decide in N days." (plural) or "Added as a need." Never money, deals or urgency (ADR-0014).

## Tests

- Rules: `CaptureTest` (JVM). View model: `QuickAddViewModelTest` with `testing/Fakes.kt` (`FakeItemRepository`, `FakePolicyRepository`) and `Dispatchers.setMain`.
- `QuickAddFlowTest` drives the real `MainActivity` (Hilt + Room under Robolectric) and counts taps: Add → name → Next → price → Save.
- Screenshots: `QuickAddScreenshotTest` draws `QuickAddForm` (not the `ModalBottomSheet`, a separate window the capture misses) per state × en/de.
