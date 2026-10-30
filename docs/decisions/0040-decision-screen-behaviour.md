# ADR-0040: Decision screen: what's offered per stage, waiting items in their own group, purchase dates, reject note

**Status**: accepted; amended by ADR-0043 (Home's "To decide" also lists waits ended without answers), ADR-0048 ("What else this money could do" is the full opportunity cost) and ADR-0049 (a parked want says it isn't a priority and how much of its wait is done; "Looking again later" is a stage of a priority, not a group)
**Type**: product
**Date**: 2026-10-21

## Context

The decision screen (#18, brief §5 "Decisions", §6.5) offers Buy / Wait / Reject, an early "buy anyway" path with friction (ADR-0006) and a first, basic opportunity cost. The issue leaves open what a want that isn't Ready offers, where an item goes on the wishlist after "Wait", how a purchase date maps onto the stored decision, where the reject note is stored, and what the opportunity cost list contains. The owner chose the first four on 2026-10-21.

Alternatives considered:

- **Not Ready: Wait next to Reject.** An item still cooling off waits anyway; a re-check date before its end means nothing.
- **Waited items in the Cooling off group, or hidden.** Cooling off means a timer is running, which it isn't; hidden items would look lost.
- **A separate purchase date on the decision.** A schema change for a value `decidedAt` already holds: ADR-0032 already orders history by the decision appended last because "a purchase may be dated back".
- **No reject note until a later issue.** The issue asks for it, and it's the user's own learning material.

## Decision

- **What's offered** follows `BuyPath` (`:core:domain` `decision/`): a Ready want and a need (any stage) get **Buy** and **"Don't buy — keep 1,200 €"** as an equal-weight pair (ADR-0028), plus **Wait** below. A want that isn't Ready gets no Buy and no Wait: **"Don't buy — keep …"** as the main button and a quieter **"Decide early…"** text button. Every decision takes two taps from the screen (open the step, confirm), so not buying never takes more taps than buying. Item detail opens the screen with "Decide" once Ready (or for a need), "Decide now" before that.
- **Early purchase** shows the days left (or that the answers are incomplete), the reasons against, and a "Why buy it now?" field; "Buy anyway" stays disabled until it's non-blank. The path is fixed when the step opens: if cooling off ends while typing, it is still recorded as `BUY_EARLY` (ADR-0006: "any time").
- **Purchase date**: defaults to today, may be dated back to the day cooling off ended (Buy) or the day the item was added (need, early). Today is decided now; an earlier day at its last millisecond, so a Buy on the day cooling off ended still counts as Ready.
- **Wait** offers 7, 14 or 30 days or a picked date (from tomorrow); the item is due again at the **start of that day** in the device zone (calendar days, ADR-0018). Until then a Ready item is shown in a last wishlist group **"Looking again later"** with a "Waiting" chip and "Look again on …", on the wishlist and on detail. Items in other stages ignore the re-check date and show what they still need.
- **Reject note**: optional "Why not?", trimmed, blank stored as none, in a new `decisions.note` column (schema 2, `MIGRATION_1_2`, ADR-0019). Only `REJECT` carries a note.
- **After deciding**: Buy and Reject close the decision screen and item detail and say “Got it. It’s in ‘Bought’.” / “Got it. It’s in ‘Decided against’.” — nothing else, no animation (ADR-0012). Wait returns to detail, which shows the date.
- **"What else this money could do"** (basic, M1): the other active items with their prices, priorities first, then the longest wanted, at most 3, then "and N more on your list". The full opportunity cost (months of allowance, delay to priorities) comes with M2.

## Consequences

- `decided_at` of a purchase is the purchase date, not when the button was pressed.
- The first explicit migration ships; from now on every schema change needs one (ADR-0019), tested in `MigrationTest` against the committed schema JSON.
- Notifications (#20) can reuse `waitDecision` and the waiting group; the home dashboard (#21) should use `isDueForDecision` for "ready to decide".
