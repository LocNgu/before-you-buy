# ADR-0044: Needs never touch fun money and never need funding

**Status**: accepted
**Type**: product
**Date**: 2026-10-23

## Context

The app is about non-essential spending. ADR-0008 added `NEED` so users still record urgent, real purchases (a broken fridge) in the app instead of leaving it. It skipped cooling off and reflection, but left two things open (discussed in #23, open in brief §12):

- **Funding.** Ready = cooled off + reflected + funded (ADR-0007) also applied to needs. Once real savings exist (#23), a need nobody saved for would sit in "Saving" and never come up for a decision.
- **The pool.** ADR-0008 let the user pay a need from the discretionary pool ("fun money") if they chose.

Both make a need compete with the things the user saves up for. The owner: a necessity eating the budget for fun things feels like a punishment, not a smarter way to avoid overspending. That works against the calm, non-judgemental tone (ADR-0012) and makes users less likely to record needs at all.

Alternatives considered:

- **Keep the pool option, default off.** Rejected: it's a second way to do the same thing. Something the user wants to pay from fun money is a want, and can be recorded as one.
- **A separate budget for needs.** Rejected: the app doesn't track essential spending, and a needs budget would turn it into a general budgeting app.

The owner accepted the proposal from #23 on 2026-10-23, when M2 started (#22 defines the pool's entry types). The draft written in #59 (commit 72a6ec4) is the starting point.

## Decision

- A `NEED` is always funded: like cooled off and reflected, the funded condition is always true for it. A need is Ready as soon as it is captured and never shows "Saving".
- Needs are never paid from the discretionary pool and can't receive allocations. This replaces ADR-0008's "the user can choose otherwise". There is no "Pay from fun money" option and no `NEED_PURCHASE_FROM_POOL` ledger entry.
- There is no budget for needs. A need is a record: name, price, date and the need flag. It shows neutrally in history, so later insights can still ask whether needs were really needs.

## Consequences

- ADR-0008 is amended (pool clause). ADR-0007's Ready rule is unchanged; needs simply always meet the funded condition.
- `isFunded` returns true for a `NEED` (`:core:domain`). No visible change yet: until #23 every item counts as funded.
- #23: `allocate` refuses a need; a `BUY_NEED` leaves the pool untouched. Turning a want with allocations into a need releases them back to unallocated (the user confirms). #23's issue text ("Pay from fun money", `NEED_PURCHASE_FROM_POOL`) is out of date on this point.
- Needs don't appear in "What am I saving for?" or in opportunity cost (#25), which are about fun money.
- If someone wants to pay for a necessity from fun money, they record it as a want.
