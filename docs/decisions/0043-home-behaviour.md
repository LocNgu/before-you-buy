# ADR-0043: Home: "To decide" includes items whose wait ended without answers, links on Home instead of a bottom bar, no Settings entry before Settings exists

**Status**: accepted
**Type**: product
**Date**: 2026-10-22

## Context

Home (#21, brief §6.1) answers four questions: what can I decide on, what am I considering, what am I saving for (M2) and how much discretionary money I have (M2). The issue leaves open which items count as "items whose waiting period just ended", whether navigation is a bottom bar or links, what Home offers for Settings before the Settings screen (#26, M3) exists, and what the M2 placeholders say. ADR-0040 said Home should use `isDueForDecision` for "ready to decide". The owner chose these on 2026-10-22.

Alternatives considered:

- **Only `isDueForDecision`** (Ready items and needs): items whose wait ended but whose required answers are missing would appear only as a count, though that is exactly when the "waiting period over" notification sends people to decide (ADR-0042).
- **"Just ended" as a time window** (e.g. the last 7 days): an arbitrary number, and items would silently drop off Home while still undecided.
- **A bottom bar** (Home · Wishlist · History): needs per-tab back stacks and changes how notification taps build the back stack (`backStackFor`), for three destinations that fit on Home.
- **A Settings entry leading to a "coming soon" screen**: a button that does nothing useful.
- **Hiding the M2 sections until M2**: the issue asks for the four questions in order; placeholders keep the layout stable.

## Decision

- **"To decide"**: first the items `isDueForDecision` accepts (Ready, not waiting after "Wait"; needs included), then wants whose cooling-off is over but whose required answers are missing, each part in wishlist order. Hidden when empty. A tap opens the decision screen directly; for an item without its answers it offers "Answer the questions" next to not buying and deciding early (ADR-0040). Back returns to Home. This amends ADR-0040's "Home should use `isDueForDecision`" for the second part only.
- **"Considering"**: the number of active items (waiting ones included), the item whose cooling-off ends next with its countdown, and an "Open wishlist" link. With nothing added yet, one line saying what the wishlist is for.
- **Placeholders** "Saving for" and "Monthly allowance": a heading and one quiet "Coming soon: …" line each, no amounts, nothing to tap. Never "money available" or "you can afford" (ADR-0014).
- **Navigation**: Home stays the root of one back stack. Wishlist opens from "Considering", History from a "History" button in the top bar. System back always leads back to Home. No Settings entry until the Settings screen exists (#26 adds it).
- **Never on Home**: the decided-against total or anything else from history (moral licensing, ADR-0012).
- The sections are derived by `homeOverviewOf` in `:core:domain` from `wishlistOf`, so Home and the wishlist always agree on stages and order.

## Consequences

- A user with many items still missing answers sees all of them under "To decide"; the decision screen offers the questions for each.
- #26 must add the Settings entry to Home (top bar).
- M2 replaces the two placeholders with real content; the section order stays.
