# ADR-0051: Screens for parked wants: "Make it a priority" everywhere, the Priorities section always shown, wait progress instead of a stage, no "Set aside" buttons without a slot

**Status**: accepted
**Type**: product
**Date**: 2026-11-01

## Context

ADR-0049 parks every new want on the wishlist and lets only priorities wait, be saved for and become Ready. It fixed the main wording ("Not one of your priorities", "Make it a priority", "Added to your wishlist.", "Waiting period: 90 days, once it's a priority") and the wishlist layout (Priorities, Needs, Wishlist). The UI PR of #82 had to settle what it left open. The owner chose on 2026-11-01:

- **The label.** ADR-0047 named the action "Make priority" (detail, row menu). ADR-0049 says "Make it a priority" for the snackbar and the parked screens. Two labels for one action would read as two actions.
- **The money form of a parked want.** ADR-0049 refuses new money without a slot but keeps "Take back". The alternatives were a disabled "Set aside" button next to "Take back", or hiding the section. A disabled button doesn't say why; hiding it hides money the want still holds.
- **The Priorities section without priorities.** Until now it appeared only once there was a priority. With every new want parked, a new user would see only "Wishlist" and no hint that a want has to become a priority to start waiting.

The rest are smaller defaults the PR needed.

## Decision

- **"Make it a priority" everywhere** („Zur Priorität machen“ in German, unchanged): the wishlist row menu, item detail, the decision screen and the snackbar after adding a want. Amends ADR-0047's label.
- **The Priorities section is always shown**, also with no priorities: "Priorities", "0 of 3 slots used", "Change", and a quiet line "Make a want a priority to start its waiting period." while it's empty.
- **Wishlist sections** (ADR-0049): the priorities by rank, each with its stage chip and status; a priority after "Wait" keeps its rank, with the chip "Looking again later" and "Look again on …" (the chip "Waiting" goes). Then "Needs", oldest first. Then "Wishlist": every parked want, oldest first, with no chip and no rank, a ⋮ menu with "Make it a priority", and how much of its wait is done.
- **How much of a wait is done**, shown for a parked want on its row, on item detail, on the decision screen and in "Decide early…": "Waiting period not started (30 days)" while it hasn't waited a whole day, "12 of 30 days waited", "Waiting period done". Whole calendar days, as the wait counts them (ADR-0050).
- **Item detail and the decision screen of a parked want** say "Not one of your priorities", how much of the wait is done, and offer "Make it a priority" as an outlined button in the header, where a priority shows its stage and countdown. On detail it moves there from the actions at the bottom; "Remove from priorities" stays there. A free slot is taken at once (the snackbar says so); full slots open the head-to-head. On the decision screen nothing else changes: "Don't buy — keep …" and "Decide early…", never Buy or Wait (ADR-0040, ADR-0049).
- **"Set aside" on a parked want**: the line "You can set money aside once it's a priority." instead of the two buttons. While money is still set aside on it, the progress bar and a single "Take back" stay. Without money set aside, there is no prompt to set up fun money either.
- **After adding a want in the app**, the snackbar "Added to your wishlist." carries "Make it a priority" and shows for the short duration like every other message (accessibility settings lengthen it), never indefinitely: messages queue, so a longer one would hold back the next. After a share, the toast says the same without an action (ADR-0036). A need keeps "Added as a need."
- **Quick add's price hint** shows the tier's days for a want whose price reads, updating as it's typed; nothing while the price doesn't read. A need keeps "Needs skip the waiting period" under its switch.
- **Edit** says "Waiting period: 60 days, once it's a priority" under the price when saving would make a parked want wait longer: a higher price, or a need turned back into a want. A priority keeps "Waiting period extended to …".
- **The head-to-head summary** says "Bike moved back to the wishlist; its wait is paused".

## Consequences

- One label, and every place that offers it behaves the same (`makePriority`: a free slot, else the head-to-head).
- The empty Priorities section takes a few lines even for a user who never uses priorities. That's on purpose: without a priority nothing waits or becomes Ready.
- A parked want shows no countdown anywhere; how much of its wait is done doesn't change while it's parked, so the screen doesn't suggest that something is running.
- Home's "Saving for" still falls back to wants by progress (ADR-0046) until the Home PR of #82 removes it, as ADR-0049 decided.
