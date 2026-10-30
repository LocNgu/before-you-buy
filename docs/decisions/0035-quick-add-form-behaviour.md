# ADR-0035: Quick add: errors after typing, Done saves, closing discards

**Status**: accepted; amended by ADR-0049 (the snackbar after saving a want says "Added to your wishlist." and offers "Make it a priority"; the price field shows the waiting period)
**Type**: product
**Date**: 2026-10-17

## Context

Quick add (#14, brief §6.2) must capture a want in about ten seconds; manual entry is the main reason finance apps lose users (research §1 "Retention"). The issue fixes the fields, "Save disabled until valid" and inline validation, but not when errors appear, whether the keyboard can save, or what happens to a half-typed item when the sheet is closed.

Alternatives considered:

- Errors on an empty form from the start: shouts at the user before they've typed anything. Errors only after pressing Save: impossible, since Save is disabled until the form is valid.
- Keeping a draft when the sheet is swiped away: protects against an accidental swipe, but the next "Add" (or a share from another app, #15) would open with an old, unrelated item in it.

## Decision

- **Errors appear under a field once it has held text** (typed or prefilled): "Enter a name" after clearing the name, "Enter an amount like 12,50" for a price that doesn't read. An untouched field shows nothing; Save stays disabled until name and price are valid.
- **"Done" on the price keyboard saves a valid form**, so the shortest path is Add → name → Next → price → Done. The Save button at the top of the sheet does the same and stays visible above the keyboard.
- **Closing the sheet without saving discards what was typed.** Every open starts empty, or with the share sheet's values.
- After saving, a snackbar says how long the item waits, in calendar days: "Added. You can decide in 30 days." A need says "Added as a need." It never mentions money (ADR-0014).
- No reflection questions in capture (ADR-0009). "More details" holds only category, notes and target date.

## Consequences

- A user who swipes the sheet away by accident retypes the item (about ten seconds).
- Every screen that offers "Add" shares one quick-add view model for the activity, so a rotation or language change keeps the open sheet and its text.
- The share sheet (#15) opens the same sheet with `QuickAddPrefill`; a prefilled price that doesn't read shows its error at once.
