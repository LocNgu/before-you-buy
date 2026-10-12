# ADR-0031: Needs never touch fun money and never need funding

**Status**: accepted
**Type**: product
**Date**: 2026-10-12

## Context

The app is about non-essential spending. ADR-0008 added `NEED` so users still record urgent, real purchases (a broken fridge) in the app instead of leaving it. It skipped cooling off and reflection, but left two things open:

- **Funding.** Ready = cooled off + reflected + funded (ADR-0007) also applied to needs. Once real savings exist (M2), a need nobody saved for would sit in "Saving" and never come up for a decision.
- **The pool.** ADR-0008 let the user pay a need from the discretionary pool ("fun money") if they chose.

Both make a need compete with the things the user saves up for. A necessity eating the budget for fun things feels like a punishment, not a smarter way to avoid overspending. That works against the calm, non-judgemental tone (ADR-0012) and makes users less likely to record needs at all.

Alternatives considered:

- Keep the pool option, default off. Rejected: it's a second way to do the same thing. Something the user wants to pay from fun money is a want, and can be recorded as one.
- A separate budget for needs. Rejected: the app doesn't track essential spending, and a needs budget would turn it into a general budgeting app.

## Decision

- A `NEED` is always funded: like cooled off and reflected, the funded condition is always true for it. A need is Ready as soon as it is captured and never shows "Saving".
- Needs are never paid from the discretionary pool, and can't receive allocations. This replaces ADR-0008's "the user can choose otherwise".
- There is no budget for needs. A need is a record: name, price, date and the need flag. It shows neutrally in history, so later insights can still ask whether needs were really needs.

## Consequences

- ADR-0008 is amended (pool clause). ADR-0007's Ready rule is unchanged; needs simply always meet the funded condition.
- `isFunded` returns true for a `NEED` (`:core:domain`, #11). No behaviour changes in M1, where every item counts as funded anyway.
- M2 (#23): no "pay from fun money" option for needs and no `NEED_PURCHASE_FROM_POOL` entry. `allocate` refuses a need. A `BUY_NEED` leaves the pool untouched. Turning a want with allocations into a need releases them back to unallocated (the user confirms).
- Needs don't appear in "What am I saving for?" or in opportunity cost, which are about fun money.
- If someone wants to pay for a necessity from fun money, they record it as a want.
