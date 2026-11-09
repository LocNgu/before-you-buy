# ADR-0055: Warmth without a score: "bought as planned", "let go" names what it's kept for, History shows both sides

**Status**: accepted
**Type**: product
**Date**: 2026-11-09

## Context

After a decision the app said only "Got it. It's in 'Bought'." / "Got it. It's in 'Decided against'." (ADR-0040) and History was purely factual. The owner wants people to like using the app; the goal isn't spending less but spending on things people really enjoy. A positive "avoided purchases" money total was considered and not chosen: a total that only grows when you don't buy is a frugality scoreboard, makes every purchase feel like losing points and can be gamed by adding wants only to let them go (`docs/research.md`, "Moral licensing"). The owner decided on 2026-10-26 (and on 2026-10-28 for the unchanged total) that the warmth goes to buying well and choosing what matters (issue #86).

## Decision

- **A warm "bought as planned" line, for a plain Buy only.** After `BuyPath.BUY` (a Ready want) the confirmation says *"You waited 61 days for this. Enjoy it."* N is the calendar days from adding to the purchase date, the number History shows as "Considered for N days" (`consideredForDaysOf`). With N = 0 (a tier that allows no wait) it says *"Enjoy it."* alone. Early buys ("Decide early…") and needs keep the neutral "Got it. It's in 'Bought'.": nothing marks the difference and nothing is shamed. Plain text in the existing snackbar: no animation, confetti, sound or badge.
- **Letting go names what the money is kept for.** After Reject on the decision screen the confirmation repeats the "Don't buy" label's alternative (ADR-0048 `keepFor`): *"Let go. 1,200 € stays for your Camera (#2)."* When the label names no priority: *"Let go. It's in 'Decided against'."* The notification's "Let it go" stays silent (ADR-0042).
- **History shows both sides as counts**, one line above the tabs that follows the period filter: *"You bought 5 things you'd waited for · You let 12 go"*, prefixed *"This month: …"* / *"This year: …"*. "Things you'd waited for" counts plain Buys only; early buys and needs are mentioned nowhere in the line. "Let go" counts every decided-against item. A part at 0 is left out; the line is hidden when both are 0. Counts only, no money. The "You decided against 3,420 € of purchases" total in the "Decided against" tab is unchanged. History only: nothing from it appears on Home (ADR-0043).

## Consequences

- Amends ADR-0012 (plain, warm text is allowed after a plain Buy; animation, confetti, sound, badges, streaks and any savings framing stay out), ADR-0040 (the post-decision messages) and ADR-0041 (the summary line).
- A user can still grow "let go" by adding wants only to reject them. A count without money is a weak incentive; the decided-against total is already plain text (ADR-0041).
- The warm line depends on the item's added date; an item dated back keeps counting calendar days to the purchase date.
- "Was it worth it?" (#37) and insights (#38) remain the real long-term reward loop and are out of scope.
