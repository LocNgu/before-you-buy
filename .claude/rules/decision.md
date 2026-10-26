---
description: Decision screen — buy paths, dates and decisions in :core:domain, the screen and view model in :app
paths:
  - "core/domain/src/**/decision/**/*"
  - "app/src/main/kotlin/**/feature/decision/**/*"
  - "app/src/test/kotlin/**/feature/decision/**/*"
---

# Decision screen (brief §5 "Decisions", §6.5, §7, ADR-0006, ADR-0008, ADR-0012, ADR-0028, ADR-0040, ADR-0048)

## Rules (`:core:domain` `decision/`)

- `buyPathOf(item, stage)`: `BUY_NEED` for a need (any stage), `BUY` for a Ready want, else `EARLY`. `BuyPath.canWait` is false for `EARLY` (it's waiting anyway). `BuyPath.type` is the `DecisionType` a purchase records.
- `decisionOverviewOf(item, reflection, active, savings, funMoney, now, zone)`: stage, buy path, days left, wanted-for days, reflected, `recheckOn` (`recheckOnOf`, shared with the wishlist and detail), and `opportunityCost` (below). `funding` is what `applyDecision` checks; `savings` is shown in the header ("amount saved", brief §6.5) for wants while fun money is set up.
- `DecisionForm` holds the inputs as typed; `decisionFormOf(item, today, locale)` prefills the **exact** price (`formatForInput`), today, and the first `WAIT_DAYS` option (7, 14, 30).
- `purchaseDates(item, path, today, zone)`: from the cooling-off end date (Buy) or the added date (need, early) to today. `checkPurchase` = price parses + early reason non-blank + date in range. `purchaseDecision` decides a today purchase at `now`, an earlier day at its last millisecond (so a dated-back Buy is still Ready); it throws for an invalid form.
- `waitDecision`: `waitUntil` = start of the chosen day in the zone (calendar days, ADR-0018), at least `earliestWaitDate` (tomorrow). `rejectDecision`: note trimmed, blank → null.
- Opportunity cost (`OpportunityCost.kt`, ADR-0048): `opportunityCostOf(item, active, savings, funMoney)` → at most one `OpportunityCostLine` per kind, in this order: `DelaysPriority` (priorities other than the item funded in rank order from `unallocated` + one monthly amount a month; buying vs not buying differs by the price; the highest-ranked priority moved by ≥ 1 whole month; a priority holding its price is skipped), `ShareOfGoal` (price ÷ the highest-ranked other priority's price, % half up, 0 = "less than 1 %", none above 100 %), `MonthsOfAllowance` (0 = less than a month, else half up, `exact` for a whole multiple), `SmallerItemsCombined` (cheapest other active items priced > 0 whose sum ≤ price, ≥ 2, `total`, `all` = every other active item). Fun money lines only for a want with fun money set up and a monthly amount > 0; a free item gets nothing; amounts relabeled to the item's currency. `keepFor()` is the top line if it's `AboutPriority`, else null. `OPPORTUNITY_COST_DETAIL_LINES` (2) for item detail. All arithmetic in `BigInteger`.
- Every decision still goes through `applyDecision` (lifecycle) and `recordDecision` (compare-and-set). No second set of rules.

## Screen and view model (`:app` `feature/decision/`)

- `DecisionViewModel` (assisted id, ADR-0037) recomputes the overview on the clock like item detail (`untilWishlistChanges` of the one item). It waits for the fun money (`funMoney.filterNotNull()`), so the reject label doesn't change its wording right after opening. Locale comes from the UI (`setLocale`). Until the first edit the form is derived from the item; after it, typing is kept. `buyPath` is fixed when the BUY step opens, so an early purchase stays `BUY_EARLY` (ADR-0006). A want's purchase that would leave less than nothing not set aside, as shown (−1 € or less), asks "Go below zero?" first (`purchaseNeedsConfirmation`, `confirmBelowZero`; ADR-0046); the purchase is spent from fun money by `recordDecision` itself. A refused or raced decision writes nothing, says "changed in the meantime" and returns to the overview. Once decided, the state stays as it was (no "gone" flash) and `outcome` emits `Bought`, `Rejected` or `Waiting(date)`.
- `DecisionContent` is stateless (screenshots) and renders one `DecisionStep`: `OVERVIEW` (header → your reasons → "What else this money could do" (`OpportunityCostSection`, every line via `opportunityCostText`, hidden without lines) → the question → actions), `BUY` (early: days left, missing answers, or what isn't set aside yet, reasons against, "Why buy it now?"; then price paid, date, confirm), `WAIT` (radio rows + "Pick a date"), `REJECT` (optional "Why not?"). Steps are part of the screen, not dialogs, so screenshots see them. Back (arrow or system) from a step returns to the overview.
- Actions: Ready/need → `ButtonPair(Buy, "Don't buy — keep <price>")` ("… for your <name> (#k)" when `keepFor()` names a priority, `keepLabel`) + an outlined Wait; not Ready → full-width "Don't buy — keep …" + a "Decide early…" text button. Never make Buy louder (ADR-0028). Not buying never takes more taps than buying (two from the overview).
- Navigation: `ItemDecision(itemId)`; item detail's "Decide" (Ready or need, not waiting, `PrimaryButton`) / "Decide now" (outlined) opens it. Bought/Rejected pop the decision screen and item detail, then show the neutral snackbar on the shared `SnackbarHostState`; Waiting pops only the decision screen. "Answer the questions" opens `ItemReflection`.

## Tests

- Rules: `DecidingTest`, `OpportunityCostTest` (JVM). View model: `DecisionViewModelTest` (`FakeItemRepository.recordDecision`, `decisions`, `conflictOnNextDecision`; `MutableClock`). Real app: `DecisionFlowTest` (early path needs a reason; Reject taps ≤ Buy taps; a cooling-off want has no Buy and no Wait). Screenshots: `DecisionScreenshotTest` ready (2 lines), readyNoOpportunityCost (0), readyWithFunMoney (4), early, early buy, need, buy, wait, reject, gone × en/de.
