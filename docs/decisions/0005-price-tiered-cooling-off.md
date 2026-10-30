# ADR-0005: Price-tiered cooling-off; price edits only extend it

**Status**: accepted; amended by ADR-0031 (tier changes apply to items still cooling off) and ADR-0049 (the wait runs only while the want holds a priority slot, so there is no fixed end date from when it is added)
**Type**: product
**Date**: 2026-10-04

## Context

Research shows delays of about a day or more reduce the urge to buy, while very short delays fail if the person keeps browsing (`docs/research.md` §1). Competitors use fixed 24 h – 7 d delays. More expensive purchases deserve longer reflection.

## Decision

- Default tiers (configurable): < 100 → 7 days, 100–< 500 → 30 days, 500–< 1,000 → 60 days, ≥ 1,000 → 90 days.
- The end date is fixed when the item is added (`addedAt + period(price)`).
- Editing the price can only extend the end date, never shorten it.
- Changed tier settings apply to new items only.
- Periods are calendar days in the device time zone.

## Consequences

- Entering a low price first and correcting it later doesn't shorten the wait.
- Users who lower tier settings don't release existing items early.
