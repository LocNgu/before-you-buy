# ADR-0033: Currency and tiers live with the items; changing the currency relabels every amount

**Status**: accepted
**Type**: product
**Date**: 2026-10-13

## Context

ADR-0031 has Settings store the new cooling-off tiers and the recomputed item ends "in one transaction". Issue #13 put the currency and tiers in DataStore and the items in Room, and no transaction spans the two. A crash between the writes would leave waiting items on their old ends, with nothing to repair them (raised in review of PR #61; second opinion from Codex).

The tiers and every stored amount are in the app's currency. With no conversion (no internet, ADR-0003), a currency change either leaves old items in the old currency (mixing currencies, which `Money` refuses, so a later price edit would throw) or has to rewrite them.

Alternatives considered:

- **Tiers stay in DataStore, two-step write with a "pending" marker repaired on start-up:** works, but adds repair logic and edge cases (an item that cooled off between the crash and the repair).
- **Accept the gap:** a silent inconsistency in the app's central rule.
- **Allow a currency change only while no item is active:** avoids mixed currencies, but blocks the realistic cases (wrong currency picked at setup, a move abroad) once the user has items.
- **Store every amount at a fixed scale (×1000) so a relabel is a rename:** stored values could be amounts the currency can't show (12.34 ¥), and it would undo ADR-0018/ADR-0027.

## Decision

- **The currency and the tiers are the `Policy`, stored in a single-row `policy` table in Room**, next to the items. Notification preferences and the onboarding flag stay in DataStore (they don't touch items), exposed as a separate `PreferencesRepository`, not merged into one settings object.
- **A tier change is one Room transaction:** the new tiers plus `coolingEndAfterRulesChange` for every active item, evaluated at one captured `changedAt`. This is how ADR-0031's "one transaction" is met.
- **The currency is fixed on first start** from the device region (else EUR) and never follows the region afterwards. Only an explicit change moves it.
- **Changing the currency relabels every stored amount in one transaction:** item prices, actual prices in history and the tiers keep their numbers ("100 €" becomes "100 $"). Minor units are rescaled when the currencies have different minor-unit digits (12,34 € → 12 ¥, rounded half up). Tiers whose thresholds round to the same value collapse into the pricier one. Default tiers stay the defaults. Cooling-off ends don't change. A change is always allowed.
- **Every stored amount is in the app's currency.** `ItemRepository.add` and `update` refuse anything else.

## Consequences

- Items and policy can't disagree. Export (M3) reads one consistent database snapshot.
- After a change of currency, old prices show the new symbol with the same number. For an app without exchange rates that is the expected meaning: "the app now counts in $".
- History totals (#19) never mix currencies.
- The one write to decision rows (`setActualPrice`) is the currency relabel; what was decided never changes (ADR-0032).
- Overrides issue #13's "currency and cooling-off rules in DataStore".
