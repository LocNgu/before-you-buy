# ADR-0046: Savings per item: the funding switch, release by decision, one purchase entry, "set aside"

**Status**: accepted; amended by ADR-0047 (Home's "Saving for" with priorities)
**Type**: product
**Date**: 2026-10-24

## Context

#23 adds savings per item (earmarks, brief §4.6, ADR-0010): money is set aside for an active want from what the fun money hasn't set aside yet, and Ready becomes cooled off + reflected + **funded** (ADR-0007) for real. Until now every item counted as funded.

That raises questions the issue left open. The owner decided them on 2026-10-23, and more after the PR's three independent reviews (2026-10-24/25):

- **Without fun money**, nothing can be set aside, so no want could ever become Ready and every purchase would go through "Decide early". Many people want only the waiting period.
- **Buying for more than was set aside**, with too little not set aside: the issue says the overshoot is "recorded as adjustment", next to a purchase entry for the price paid.
- **Wording.** The issue's "650 € / 1,600 € saved" clashes with the copy rules (no "saved") and ADR-0045's "set aside".
- **Home's "What am I saving for?"** should show active priorities, but priorities come with #24.
- **More than the price** could be set aside ("220 € of 120 €"). First capped at the price, then allowed again by the owner: some purchases have no fixed amount (a holiday with a minimum budget), and more set aside doesn't hurt.
- **Typed amounts vs shown limits:** "600 € to go" can really be 599,99 €, so typing 600 was refused with "up to 600 €".
- **Rounding:** 1.599,60 € of 1.600 € reads "1.600 € of 1.600 € set aside · 0 € to go" in whole units (ADR-0034), while the item still isn't funded.

Alternatives considered:

- **Always strict funding.** Rejected: the app would be unusable as a plain waiting-period app.
- **Funding required only once fun money is set up, with no switch.** Rejected by the owner, who wants a setting: some people use fun money only to track it.
- **Purchase plus a separate adjustment** for the part below zero. Rejected: the purchase already takes the full price, so the adjustment would count the overshoot twice.
- **Explicit release rows on reject and buy.** Not needed: ended items no longer count (below), so "Let it go" from a notification and every other decision release without extra writes.
- **Only wants with money set aside on Home.** The owner chose all wants by progress.

## Decision

- **The switch "Wants need their price set aside"** (German: „Wünsche brauchen ihren Preis zurückgelegt") is on the fun money screen, stored with the allowance, on by default.
  - On: a want is Ready only once its price is set aside, **as shown**: in whole units, rounded like every amount (ADR-0034). 1.599,60 € of 1.600 € reads "1.600 € of 1.600 €" and counts as funded, so the screen never shows the full price set aside on an item that isn't Ready. "To go" is the difference of the two amounts as shown.
  - Off: the waiting period and the answers are enough, and savings still show.
  - Without fun money set up, funding is never required. A need is always funded (ADR-0044).
- **Only active wants hold money.** `committed` is the sum of the allocations of active wants. Rejecting an item returns its money to what isn't set aside yet; buying spends it. Allocations are append-only: + sets aside, − takes back.
- **Setting aside** is limited to what isn't set aside yet, not by the price: more than the price is fine and reads e.g. "1.800 € of 1.600 € set aside"; buying returns what isn't spent. It's refused for needs and ended items, and "Set aside" is disabled when nothing is left. An amount that reads the same as the limit in whole units moves exactly the limit ("600" for 599,99 € sets aside 599,99 €; "601" is too much), for set aside and take back alike, checked again in the repository's transaction. It can start on day 1, while the item cools off. **Taking back** is limited to what the item holds. Both are checked against the stored data in one transaction.
- **Buying a want while fun money is set up** writes one `Purchase` ledger entry for the actual price, with the decision, in one transaction.
  - The item's savings are spent first. Paying less returns the rest; paying more takes the difference from what isn't set aside.
  - If that leaves less than nothing **as shown** (−1 € or less in whole units), the decision screen asks "Go below zero?" first. A few cents below zero, which reads "0 €", is recorded without asking (owner's decision after the second review); the balance stays exact. The purchase is the only record; there is no extra adjustment.
  - A need's purchase never touches the fun money (ADR-0044). Without fun money no entry is written, so setting it up later doesn't count old purchases.
- **A want that becomes a need** (item detail's "Mark as need" or edit's switch) gives back what was set aside, after the dialog says so. A take-back row is written with the edit, so the money doesn't return if the item becomes a want again.
- **Wording:** "650 € of 1,600 € set aside · 950 € to go" / „650 € von 1.600 € zurückgelegt · noch 950 €“. The buttons are "Set aside" / "Take back" („Zurücklegen“ / „Zurücknehmen“), equal in weight. Never "saved" or "available".
- **Home's "Saving for"** shows up to 3 active wants, including those with nothing set aside yet: priorities first (#24), then the most set aside relative to the price, then the oldest. Each row has a progress bar and opens the item. Without fun money it says how to set it up.
- **The sheet** has quick chips: 50 and 100 in whole units (each fills in its amount), those that fit, and "The rest", what's still to go of the price (at most the limit; none once covered). For taking back, "All". Too much reads "You can set aside up to …" / "You can take back up to …", never "Only …".

## Consequences

- ADR-0007's Ready rule is unchanged: the switch, like a need (ADR-0044), decides when the funded condition is always met.
- Fun money hasn't shipped yet (only in `[Unreleased]`), so turning the switch on by default changes no released behaviour. Existing wants become "Saving" once fun money is set up, until their price is set aside or the switch is off.
- An item can hold more than its price (set aside on purpose, or after a price drop). It reads e.g. "220 € of 120 € set aside" with a full bar; buying returns what isn't spent.
- Funded "as shown" means an item can be less than one whole unit short of its price (up to 0,99 €: 1.599,50 € set aside for 1.600,49 €, both shown as 1.600 €) and still be Ready; a price below 0,50 € is funded with nothing set aside. Buying spends the price actually paid, so the fun money stays exact.
- Once funded as shown, "The rest" is gone; more can still be set aside by amount. Buying a few cents short may leave a few cents below zero without asking (above).
- "The rest" is exact (what's still to go), while "… to go" is the difference of the shown amounts, so they can differ by 1 € on one screen (950 € vs 949 €). Accepted: both round like every amount (ADR-0034).
- ADR-0034 is amended: funded, the purchase question and typed savings amounts compare whole units as shown; every other rule (cooling-off tiers, the ledger, sums) stays exact.
- The decision screen's "Go below zero?" uses whole units like everywhere else (ADR-0034).
- Opportunity cost (#25) and priorities (#24) build on `Savings`; Home already orders by priority slot.
- A currency change relabels allocations with everything else (ADR-0033).
