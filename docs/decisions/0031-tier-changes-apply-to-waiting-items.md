# ADR-0031: Changed cooling-off tiers also apply to items still cooling off

**Status**: accepted
**Type**: product
**Date**: 2026-10-13

## Context

ADR-0005 applied changed tier settings to new items only, so that lowering the tiers couldn't release existing items early. The owner reconsidered this while reviewing #12 (PR #60). A user who has captured many items and then finds the waits too long in general would have to wait out every existing item, or buy each one early with friction (ADR-0006). That is a likely reason to abandon the app.

Alternatives considered:

- **Keep "new items only"** (ADR-0005): safest against gaming, but leaves the user above stuck.
- **Extend only:** longer tiers apply to existing items, shorter ones don't. That keeps the anti-gaming rule, but doesn't help the user above.
- **Both directions, all active wants:** also puts items whose wait has already ended back into cooling off when the tiers get longer. That is surprising, and it takes away an item the user may be about to decide on.

## Decision

- When the user changes the tiers, every active want that is **still cooling off** gets `addedAt + period(current price)` under the new tiers. The new end can be later or earlier than before; if it has already passed, the item has cooled off.
- Items whose cooling-off has ended, needs, and purchased or rejected items keep their end.
- Turning a need back into a want recomputes its end the same way, even if that is sooner than an end it had as a want before (#12).
- Price edits are unchanged: they can only extend the end (ADR-0005).

This amends ADR-0005's clause "Changed tier settings apply to new items only".

## Consequences

- A user can shorten every wait at once by lowering the tiers. The app accepts this to keep users who find the defaults too long.
- A tier change recomputes from the current price. For an item whose price was lowered after capture, the end protected by ADR-0005 can therefore shorten, down to what its current price calls for.
- Settings (M3) applies `coolingEndAfterRulesChange` to all items and stores the results with the new tiers in one transaction.
