---
description: Quick add and share capture — form and share-text rules in :core:domain, the sheet, view model and share activity in :app
paths:
  - "core/domain/src/**/capture/**/*"
  - "app/src/main/kotlin/**/feature/capture/**/*"
  - "app/src/test/kotlin/**/feature/capture/**/*"
  - "app/src/main/kotlin/**/feature/home/**/*"
---

# Capture (brief §6.2, ADR-0009, ADR-0015, ADR-0035, ADR-0036)

## Rules (`:core:domain` `capture/`)

- `CaptureForm` holds the fields as typed. `checkCapture(form, currency, locale)` says whether the name is missing and how the price reads (`parseMoney`, so both `1.234,56` and `1,234.56` work, ADR-0027). `capturedItem(form, policy, locale, addedAt, zone)` builds the new item: trimmed text, blank optional fields → null, `ItemId.NEW`, cooling-off end from `initialCoolingEnd` (a `NEED` ends at `addedAt`). It throws for an invalid form.
- `capturedItem(form, policy, locale, addedAt)` gives a **parked** want: `coolingOffDays` from the tiers, no slot, no running wait (ADR-0049). `capturedWaitDays(item)` is what the snackbar says: the days it waits once it's a priority (0 for a need). The UI PR replaces that snackbar with "Added to your wishlist.".
- **No reflection questions in capture** (ADR-0009). "More details" is only category, notes, target date.
- `parseSharedText(text, subject)` (`SharedText.kt`) reads a share offline into `SharedItemDraft(name, url)`: first `http(s)` link (text, else subject) without tracking parameters, name = the rest without links, boilerplate and the shop's name, else from the subject; `shop` is the readable host for the fallback name. It never throws. A new shop or phrase: add it to the lists **and** a fixture in `SharedTextTest`. Never read a price from the text (ADR-0036).

## Sheet and view model (`:app` `feature/capture/`)

- `QuickAddViewModel` (Hilt, `hiltViewModel()` from `androidx.hilt:hilt-lifecycle-viewmodel-compose`) owns the sheet: `open(prefill)` starts a fresh form, `dismiss()` discards it, `save()` stores via `ItemRepository.add` and emits `QuickAddSaved` on `saved`. The form stays in place while the sheet slides away and is reset on the next `open`.
- Errors show once a field has held text (typed or prefilled); Save is disabled until valid; "Done" on the price keyboard saves a valid form (ADR-0035).
- The view model needs the UI's locale to read prices: `QuickAddHost` passes `currentLocale()` via `setLocale`. Until then nothing can be saved.
- The fields (`NameField`, `PriceField`, `LinkField`, `NeedToggle`, `DetailFields`) and `ItemFormActions` live in `ItemFields.kt` and are shared with item edit (`.claude/rules/wishlist.md`). Change them there, for both.
- A screen offers quick add with `QuickAddButton` (FAB) and an `onAdd` that calls `QuickAddViewModel.open()`. `QuickAddHost(viewModel, snackbarHostState)` is composed **once**, in `AppNavigation`, with the `SnackbarHostState` the screens share: `saved` is a channel, and each event reaches one collector.
- Prefill with `QuickAddPrefill(name, price, link)`; prefill a price with `Money.formatForInput(locale)`, never `format`. With a name but no price the cursor starts in the price (`QuickAddUiState.startField`), else in the name.
- Share capture (ADR-0036): `ShareReceiverActivity` (translucent theme, empty `taskAffinity`, `excludeFromRecents`, `ACTION_SEND` `text/plain`) builds the prefill with `sharedPrefill(intent, resources)` (fallback name `share_fallback_name`), opens its own view model with `openOnce` (so a rotation keeps the form and a recreate after closing just finishes), and uses `QuickAddHost(viewModel, onSaved, onClosed)`: a toast for the saved message, `finish()` once the sheet has slid away.
- Snackbar: "Added. You can decide in N days." (plural) or "Added as a need." Never money, deals or urgency (ADR-0014).

## Tests

- Rules: `CaptureTest` (JVM). View model: `QuickAddViewModelTest` with `testing/Fakes.kt` (`FakeItemRepository`, `FakePolicyRepository`) and `Dispatchers.setMain`.
- `QuickAddFlowTest` drives the real `MainActivity` (Hilt + Room under Robolectric) and counts taps: Add → name → Next → price → Save.
- Share parsing: `SharedTextTest` (JVM), fixtures per app plus edge cases. Share activity: `ShareCaptureTest` launches `ShareReceiverActivity` with an `ACTION_SEND` intent via `ActivityScenario` and `createEmptyComposeRule()` (a fresh application, i.e. cold start); returning to the sharing app is only checkable on a device.
- Screenshots: `QuickAddScreenshotTest` draws `QuickAddForm` (not the `ModalBottomSheet`, a separate window the capture misses) per state × en/de.
