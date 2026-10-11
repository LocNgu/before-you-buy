# ADR-0026: Calm fixed theme; no dynamic colour, no urgency colours, identical decision buttons

**Status**: accepted
**Type**: product
**Date**: 2026-10-10

## Context

The app should feel calm and deliberate, the opposite of shop UX (product brief §2; issue #10). Material 3 offers dynamic colour (Android 12+), which takes its palette from the wallpaper. It can turn red or orange, and screenshot tests would no longer show what users see. Decision surfaces must not favour buying (ADR-0012: Reject as easy as Buy).

## Decision

- One fixed palette in light and dark: Material 3 "tonal spot" (2021 spec) generated from the muted teal seed `#4F7A74`. **No dynamic colour.** The window background in `res/values*/colors.xml` matches the Compose background.
- Red/orange are never used for countdowns, prices or amounts. `error` is only for input errors.
- `PrimaryButton` and `SecondaryButton` look identical (same tonal fill, shape, size, type); only the label differs. `ButtonPair` gives both the same width and height.
- Platform font (no bundled fonts), lighter headline weights, slightly rounder shapes.

## Consequences

- Consistent look, deterministic screenshots; users' wallpaper colours don't apply.
- Changing the palette means regenerating every role from a new seed, not editing single colours.
- The decision screen (Buy / Wait / Reject) gets equal-weight buttons for free; it can't make Buy louder without superseding this ADR.
