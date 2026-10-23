---
description: Item model, lifecycle stage, decision rules, cooling-off rules and money arithmetic in :core:domain
paths:
  - "core/domain/src/**/money/**/*"
  - "core/domain/src/**/coolingoff/**/*"
  - "core/domain/src/**/item/**/*"
  - "core/domain/src/**/lifecycle/**/*"
---

# Items, lifecycle and cooling-off (ADR-0005 – ADR-0009, ADR-0018, ADR-0031)

## Model (`item/`)

- `Item` holds the stored facts only: `status` (`ACTIVE`/`PURCHASED`/`REJECTED`), `coolingEndsAt`, `recheckAt`, … **Never add a stage field** (ADR-0007). `Reflection` and `Decision` are stored next to the item, not inside it.
- `ItemId.NEW` is the id of an item not stored yet; `ItemRepository.add` assigns the real one. Repository interfaces live here, implementations in `:core:data` (`.claude/rules/data.md`).
- Optional reflection answers are nullable/empty: reflection is filled in a bit at a time after capture (ADR-0009).
- `Decision` throws if a purchase lacks `actualPrice` or a field belongs to another type (`actualPrice` on a non-purchase, `earlyReason` off `BUY_EARLY`, `waitUntil` off `WAIT`, `note` off `REJECT`). That's a programming error, not user input.

## Rules (`lifecycle/`)

- Every rule is a pure function that takes `now: Instant` (or evaluates at `Decision.decidedAt`); nothing reads a clock.
- `stageOf` follows brief §5: the first unmet condition wins (cooled off → reflected → funded). It throws for purchased/rejected items; filter by status first. `isDueForDecision` is false for them instead.
- `NEED` shortcuts cooled off, reflected **and** funded (ADR-0008, ADR-0044): a need never uses fun money. Until #23, callers pass the price as `allocated`.
- `applyDecision` is the only way to change an item's status. It returns `DecisionResult.Applied(newItem)` or a typed `Refused`; it never throws for a refused decision. Which buy fits which item: want + Ready → `BUY`; want in any stage → `BUY_EARLY` with a reason (ADR-0006, "any time": the UI only offers it before Ready, but a confirm that lands just after cooling off ends must still succeed); `NEED` → `BUY_NEED` in any stage. `WAIT` needs a date after the decision (compute "in 7 days" with calendar days in the device zone, ADR-0018, never `+ 86_400` seconds); `REJECT` always works on active items; ended items refuse everything.
- Ending an item (purchase or reject) clears `recheckAt` and `prioritySlot`.
- `Money` arithmetic and comparison throw on mixed currencies and on `Long` overflow. The app has one currency (`settings/Policy`); `Money.relabeled(currency)` keeps the number and rescales minor units, rounding half up (ADR-0033). It's only for the currency change, never a conversion. `Iterable<Money>.total(currency)` sums exactly; display rounding (ADR-0034) happens only in `format`. `stageOf`, `isDueForDecision` and `applyDecision` throw up front if `allocated` (or `actualPrice`) isn't in the item's currency, whatever the stage or decision.

## Cooling-off (`coolingoff/`, brief §4.2)

- `CoolingOffRules` can only exist valid: `CoolingOffRules.from(tiers)` returns `Valid` or `Invalid` with every `CoolingOffRulesError` (by tier index, for Settings); `defaults(currency)` scales `DEFAULT_COOLING_OFF_TIERS` (major units, the one source of the defaults) to the currency's minor units. Thresholds in mixed currencies throw.
- Periods are whole days (`Period.ofDays`); months or years are rejected because their length varies. Add them with `ZonedDateTime` in an explicit `ZoneId` (calendar days across DST), never as seconds.
- The cooling-off end is set at capture by `initialCoolingEnd` (a `NEED` ends at `addedAt`). A price edit only extends it: `coolingEndAfterPriceEdit` takes the max with the current end (ADR-0005). Only two things may shorten it (ADR-0031): `changeKind` from `NEED` to want, which recomputes it from `addedAt` like a new want, and a tier change.
- When the tiers change, apply `coolingEndAfterRulesChange` to every item (ADR-0031): it recomputes the end of active wants that are still cooling off, longer or shorter, from `addedAt` and the current price. Items whose wait has ended, needs and ended items keep their end.
- Every cooling-off function throws up front if a price isn't in the rules' currency or is negative, whatever the item's kind.
- `CoolingOffRules.relabeled(currency)` relabels thresholds; tiers that round to the same threshold collapse into the pricier one, so the result is valid.
- `changeKind` is the only way to change kind. Want → `NEED` sets `markedAsNeedAt` and keeps `coolingEndsAt`; `NEED` → want clears `markedAsNeedAt`.

## Tests

- Fixtures in `lifecycle/Items.kt` (test sources): one item with fixed instants before/during/after cooling off. Add a test per row when a rule table changes.
