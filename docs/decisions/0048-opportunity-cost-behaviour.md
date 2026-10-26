# ADR-0048: Opportunity cost: four line kinds, ranked by relevance; needs get only price comparisons; delay by rank-order funding; "Don't buy" names a priority only from the top line

**Status**: accepted
**Type**: product
**Date**: 2026-10-26

## Context

Issue #25 replaces the decision screen's basic "What else this money could do" list (ADR-0040: the other items with their prices) with the full opportunity cost of brief §7: months of fun money, the share of another priority, the delay to a priority, and the smallest items combined. Item detail gets a compact version. Research §1 ("Opportunity cost neglect") is the reason: describing the alternative concretely reduces impulse purchases.

The issue leaves open what a need shows, what "Don't buy — keep …" says when the top line names no priority, how "ranked by relevance" orders the lines, and how the delay is estimated. The owner chose these on 2026-10-26.

Alternatives considered:

- **Needs: no lines, or all four.** No lines hides useful context when a need is expensive. All four would describe fun money a need never uses (ADR-0044).
- **Always a concrete "Don't buy" label** ("keep 1,200 € — 4 months of fun money"): longer buttons and more wording for little gain. The research effect comes from naming the alternative, which a priority does best.
- **Months of fun money first.** The reject label would then rarely name a priority.
- **Delay as a simple ratio** ((price − not yet set aside) ÷ monthly amount) on the highest-ranked unfunded priority. It ignores what the priorities above it still need.

## Decision

- **Lines** (`opportunityCostOf`, `:core:domain` `decision/`), at most one of each kind, in this order:
  1. **Delays priority**: the priorities (other than the item) are funded in rank order from what isn't set aside yet plus one monthly amount a month. Buying spends the price (its savings first). Not buying returns its savings. The two cases differ by the full price. The line names the highest-ranked priority whose funding moves by at least one whole monthly amount, "by about N months". A priority that already holds its price is never delayed.
  2. **Share of goal**: the price as a percentage of the highest-ranked other priority's price, rounded half up; under 0.5 % reads "less than 1 %". Only up to 100 %: an item pricier than that priority isn't "300 % of" it, and "Don't buy" doesn't keep its price for a cheaper goal.
  3. **Months of fun money**: price ÷ monthly amount. Below one month reads "less than a month". Otherwise it is rounded half up, reading "N months" when the price is an exact multiple and "about N months" when it isn't.
  4. **Smaller items combined**: the most of the cheapest other active items (wants and needs, priced above zero) whose sum is at most the price, at least two, shown with their total ("Your 3 cheapest wishlist items come to 1,148 € together"; "All N other items…" when they are every other item on the list).
- **No invented numbers**: no line without its inputs. Without fun money or with a monthly amount of 0, there are no months and no delay. Without another priority there is no share or delay. With fewer than two fitting items there is no items line. A free item gets no lines.
- **Needs** get only the price comparisons (share and items), never the fun money lines (ADR-0044).
- **"Don't buy — keep 1,200 € for your Camera (#2)"** when the top line is about a priority (delay or share). Otherwise the label stays "Don't buy — keep 1,200 €".
- **Item detail** shows the first two lines under the same heading, after the savings section. The decision screen shows them all. With no lines, neither shows the section.
- Wording stays neutral and factual: no "afford", "spend" or "available" (ADR-0014, ADR-0045).

## Consequences

- ADR-0040's basic list ("the other active items with their prices … and N more") is replaced; ADR-0040 is amended for that clause.
- The decision screen and item detail wait for the fun money to load, so the label doesn't change its wording a moment after the screen opens.
- The delay counts whole accruals and ignores the accrual date and future set-asides by hand; it is an estimate ("about").
- "Cost in hours worked" stays out of scope (Later).
