# ADR-0055: The fun money's third number is "Not yet assigned"

**Status**: accepted
**Type**: product
**Date**: 2026-10-29

## Context

ADR-0045 labelled the pool's third number (`pool − committed`) "Not yet set aside" / "Noch nicht zurückgelegt". It can be misread as a shortfall ("still missing"). It is money in the fun money that is tied to no want yet. A shortfall exists only per item ("950 € to go").

## Decision

- English "Not yet assigned", German "Noch nicht verteilt", wherever the label means the pool's unassigned money: Home, the fun money screen, the savings sheet and both "Go below zero?" dialogs.
- Unchanged: "Fun money for wants", "Set aside for wants", the "Set aside" action, per-item progress, and `decision_early_saving` ("… isn't set aside yet"), which does mean the item's missing amount.
- ADR-0014's principle holds: never "available" or "spend".

## Consequences

Amends the label clause of ADR-0045. Decided by the owner (issue #88).
