---
description: Item model, lifecycle stage, decision rules, cooling-off rules and money arithmetic in :core:domain
paths:
  - "core/domain/src/**/money/**/*"
  - "core/domain/src/**/coolingoff/**/*"
  - "core/domain/src/**/item/**/*"
  - "core/domain/src/**/lifecycle/**/*"
---

# Items, lifecycle and cooling-off (ADR-0005 – ADR-0009, ADR-0018, ADR-0031, ADR-0049, ADR-0050)

## Model (`item/`)

- `Item` holds the stored facts only: `status` (`ACTIVE`/`PURCHASED`/`REJECTED`), the wait (`coolingOffDays`, `waitedDays`, `waitingSince`, `coolingEndsAt`), `recheckAt`, … **Never add a stage field** (ADR-0007). `waitingSince` and `coolingEndsAt` are null together, exactly while the wait doesn't run; `Item` throws otherwise. `Reflection` and `Decision` are stored next to the item, not inside it.
- `ItemId.NEW` is the id of an item not stored yet; `ItemRepository.add` assigns the real one. Repository interfaces live here, implementations in `:core:data` (`.claude/rules/data.md`).
- Optional reflection answers are nullable/empty: reflection is filled in a bit at a time after capture (ADR-0009).
- `Decision` throws if a purchase lacks `actualPrice` or a field belongs to another type (`actualPrice` on a non-purchase, `earlyReason` off `BUY_EARLY`, `waitUntil` off `WAIT`, `note` off `REJECT`). That's a programming error, not user input.

## Rules (`lifecycle/`)

- Every rule is a pure function that takes `now: Instant` (or evaluates at `Decision.decidedAt`); nothing reads a clock.
- `stageOf` follows brief §5: `PARKED` (`Item.isParked`: an active want without a slot) wins over everything (ADR-0049), then the first unmet condition wins (cooled off → reflected → funded). A parked want is never Ready, so `isDueForDecision` and a plain `BUY` refuse it; `BUY_EARLY` works. `isCooledOff` = need, or `waitedDays ≥ coolingOffDays`, or the running end has passed; a parked want can be cooled off and still parked. It throws for purchased/rejected items; filter by status first. `isDueForDecision` is false for them instead.
- `NEED` shortcuts cooled off, reflected **and** funded (ADR-0008, ADR-0044): a need never uses fun money. Stage functions take a `Funding(allocated, required)` from `Savings.fundingOf` (`pool/Savings.kt`); `required` is false without fun money or with its switch off (ADR-0046).
- `applyDecision` is the only way to change an item's status. It returns `DecisionResult.Applied(newItem)` or a typed `Refused`; it never throws for a refused decision. Which buy fits which item: want + Ready → `BUY`; want in any stage → `BUY_EARLY` with a reason (ADR-0006, "any time": the UI only offers it before Ready, but a confirm that lands just after cooling off ends must still succeed); `NEED` → `BUY_NEED` in any stage. `WAIT` needs a date after the decision (compute "in 7 days" with calendar days in the device zone, ADR-0018, never `+ 86_400` seconds); `REJECT` always works on active items; ended items refuse everything.
- Ending an item (purchase or reject) clears `recheckAt` and `prioritySlot`.
- `Money` arithmetic and comparison throw on mixed currencies and on `Long` overflow. The app has one currency (`settings/Policy`); `Money.relabeled(currency)` keeps the number and rescales minor units, rounding half up (ADR-0033). It's only for the currency change, never a conversion. `Iterable<Money>.total(currency)` sums exactly; display rounding (ADR-0034) happens in `format`, and the only rules that use it are the savings ones (`Money.shownWholeUnits` via `coversPrice`, `purchaseNeedsConfirmation`, `withinShown`; ADR-0046). `stageOf`, `isDueForDecision` and `applyDecision` throw up front if `Funding.allocated` (or `actualPrice`) isn't in the item's currency, whatever the stage or decision.

## Cooling-off (`coolingoff/`, brief §4.2)

- `CoolingOffRules` can only exist valid: `CoolingOffRules.from(tiers)` returns `Valid` or `Invalid` with every `CoolingOffRulesError` (by tier index, for Settings); `defaults(currency)` scales `DEFAULT_COOLING_OFF_TIERS` (major units, the one source of the defaults) to the currency's minor units. Thresholds in mixed currencies throw.
- Periods are whole days (`Period.ofDays`); months or years are rejected because their length varies. Add them with `ZonedDateTime` in an explicit `ZoneId` (calendar days across DST), never as seconds.
- The wait runs only while a want holds a slot (ADR-0049, ADR-0050). Capture sets `coolingOffDays = coolingOffDaysFor(price, rules)` and nothing else: a new want is parked. `startWait(item, at, zone)` (it got a slot) sets `waitingSince = at` and the end `coolingOffDays − waitedDays` calendar days later, or `at` when it has waited enough; `pauseWait(item, at, zone)` (it lost the slot) adds the whole calendar days of the running stretch to `waitedDays` (`wholeDaysBetween`: local date-times, a part of a day is dropped) and clears start and end. Never reset `waitedDays`. `daysWaited(item, now, zone)` is both together.
- A price edit only extends the days: `waitAfterPriceEdit` takes the max with the current ones and moves a running end (ADR-0005). Only two things may shorten them (ADR-0031): `changeKind` from `NEED` to want (the days of the current price; the time it waited as a want is kept) and a tier change.
- When the tiers change, apply `waitAfterRulesChange` to every item (ADR-0031): active wants whose wait isn't complete get the days of their current price, longer or shorter, against the time already waited. Complete waits, needs and ended items keep theirs.
- Every cooling-off function throws up front if a price isn't in the rules' currency or is negative, whatever the item's kind.
- `CoolingOffRules.relabeled(currency)` relabels thresholds; tiers that round to the same threshold collapse into the pricier one, so the result is valid.
- `changeKind` is the only way to change kind. Want → `NEED` sets `markedAsNeedAt`, clears `prioritySlot` (only wants are priorities, ADR-0047) and pauses the wait; `NEED` → want clears `markedAsNeedAt` and is parked.

## Tests

- Fixtures in `lifecycle/Items.kt` (test sources): `item()` is a priority (slot 1) whose 30-day wait runs from `ADDED` to `COOLING_ENDS`, with fixed instants before/during/after; `item(NEED)` holds no slot and doesn't wait; `parked(waitedDays)` is the want without a slot. Add a test per row when a rule table changes.
