---
description: Item model, lifecycle stage, decision rules and money arithmetic in :core:domain
paths:
  - "core/domain/src/**/money/**/*"
  - "core/domain/src/**/item/**/*"
  - "core/domain/src/**/lifecycle/**/*"
---

# Items and lifecycle (ADR-0005 – ADR-0009, ADR-0018, ADR-0031)

## Model (`item/`)

- `Item` holds the stored facts only: `status` (`ACTIVE`/`PURCHASED`/`REJECTED`), `coolingEndsAt`, `recheckAt`, … **Never add a stage field** (ADR-0007). `Reflection` and `Decision` are stored next to the item, not inside it.
- Optional reflection answers are nullable/empty: reflection is filled in a bit at a time after capture (ADR-0009).
- `Decision` throws if a purchase lacks `actualPrice` or a field belongs to another type (`actualPrice` on a non-purchase, `earlyReason` off `BUY_EARLY`, `waitUntil` off `WAIT`). That's a programming error, not user input.

## Rules (`lifecycle/`)

- Every rule is a pure function that takes `now: Instant` (or evaluates at `Decision.decidedAt`); nothing reads a clock.
- `stageOf` follows brief §5: the first unmet condition wins (cooled off → reflected → funded). It throws for purchased/rejected items; filter by status first. `isDueForDecision` is false for them instead.
- `NEED` shortcuts all three conditions: cooled off, reflected **and** funded (ADR-0008, ADR-0031), so a need is Ready as soon as it's captured. Needs never use fun money: no allocations, no pool entry on purchase. Until M2 (#23), callers pass the price as `allocated` for wants.
- `applyDecision` is the only way to change an item's status. It returns `DecisionResult.Applied(newItem)` or a typed `Refused`; it never throws for a refused decision. Which buy fits which item: want + Ready → `BUY`; want in any stage → `BUY_EARLY` with a reason (ADR-0006, "any time": the UI only offers it before Ready, but a confirm that lands just after cooling off ends must still succeed); `NEED` → `BUY_NEED` (always Ready). `WAIT` needs a date after the decision (compute "in 7 days" with calendar days in the device zone, ADR-0018, never `+ 86_400` seconds); `REJECT` always works on active items; ended items refuse everything.
- Ending an item (purchase or reject) clears `recheckAt` and `prioritySlot`.
- `Money` arithmetic and comparison throw on mixed currencies and on `Long` overflow. `stageOf`, `isDueForDecision` and `applyDecision` throw up front if `allocated` (or `actualPrice`) isn't in the item's currency, whatever the stage or decision.

## Tests

- Fixtures in `lifecycle/Items.kt` (test sources): one item with fixed instants before/during/after cooling off. Add a test per row when a rule table changes.
