# ADR-0047: Priorities: wants only, "Remove from priorities", slots stored with the policy, Home's "Saving for" becomes the priorities

**Status**: accepted; amended by ADR-0049 (the wishlist is Priorities, Needs, Wishlist; a slot starts the wait)
**Type**: product
**Date**: 2026-10-25

## Context

#24 adds priorities (brief §4.7, ADR-0011): 1–5 slots (default 3); with a free slot a want is promoted directly, with full slots it has to win a head-to-head, starting from the lowest rank. The issue left some questions open. The owner decided them on 2026-10-25:

- **Where the slot count is changed.** The Settings screen is #26, which "uses the demotion confirm from #24".
- **Home.** Brief §6.1 has "Considering" = priorities + count and "Saving for" = priority goals with progress. ADR-0046's "Saving for" already put priorities first, so with three priorities Home would show the same three items twice.
- **Leaving the priorities** without buying or rejecting.
- **Needs.** The owner's first thought was that needs become the top priority. Needs skip the wait, the questions and the savings (ADR-0008, ADR-0044): one at #1 would push a want out without a comparison, against ADR-0011, sit there only until it's bought, and have no progress to show. Home's "To decide" already lists a need as soon as it's added.
- **Where the slot count is stored.** The issue says `SettingsRepository`, which doesn't exist. Reducing the slots rewrites items.

Alternatives considered:

- **Slot count only in #26**: the domain and dialog would be built now but unreachable until then. Rejected: the owner wants a small control now, as #22 did with the fun money screen.
- **A separate Priorities section on Home, or the priorities inside "Considering"**, with "Saving for" unchanged. Rejected: the same items twice.
- **Needs take #1 automatically**, or a separate needs list. Rejected (above).
- **Slot count in DataStore** (preferences). Rejected: a reduction must demote items in the same transaction, which Room gives with the items (as for the tiers, ADR-0033).

## Decision

- **Only active wants hold a slot.** "Make priority" isn't offered for needs. A priority marked as a need (item detail or edit) leaves the priorities; the dialog says so first. The ones below move up.
- **Ranks have no gaps.** Buying, rejecting or marking a priority as a need, and "Remove from priorities", move the ones below up, in the same transaction as the write (`compactedSlots`). "Wait" keeps the rank. Ranks change otherwise only through the head-to-head (no drag-to-reorder, ADR-0011).
- **"Remove from priorities"** on item detail, without a confirmation: nothing is lost, and the want stays on the wishlist.
- **"Make priority"** on item detail and in a wishlist row's ⋮ menu. With a free slot the want takes the lowest free rank at once (snackbar "Bike is now priority #3"); with all slots taken it opens the head-to-head. That's the only way into full slots: a new want never takes one without winning at least one comparison.
- **The head-to-head** ("Earn its place") compares the challenger with one priority per screen, from the lowest rank up, until it loses or beats #1. The cards show the rank, the name, then the reasons (why, how often, reasons against; "No answer yet" when missing), then the price and, with fun money, what's set aside. No photo (ADR-0013). The two answers are equal buttons named after the items (ADR-0012). The result is stored once, at the end, compare-and-set against the priorities as they were when it opened; back at any point changes nothing. A summary lists every change: "Camera stays #1", "3D printer is now #2", "Bike moved back to the wishlist", or "3D printer stays on the wishlist".
- **The slot count** is stored with the policy (`policy.priority_slots`, Room, ADR-0033), default 3, read back clamped to 1–5. Fewer slots than priorities send the lowest-ranked back to the wishlist in the same transaction. The dialog (wishlist, Priorities section, "Change") lists them before Save, so Save is the confirmation. #26's Settings reuses the dialog.
- **The wishlist** shows the priorities by rank at the top ("#1 Road bike"), with "2 of 3 slots used" and "Change", and not again in their stage group.
- **Home's "Saving for"** becomes "Priorities" once there is one: every priority by rank with its progress bar, or its price without fun money. Without priorities it stays as ADR-0046 says (up to three wants by progress; the "priorities first" ordering there is replaced by this).

## Consequences

- ADR-0046's Home rule is amended: with priorities, Home shows them instead of "Saving for".
- ADR-0011 stands as is: comparisons from the lowest rank, at least one comparison for full slots, ranks only through head-to-head (removal and slot reduction only drop items, they never reorder).
- An edit open while another priority ends finds the item's rank changed and says it changed meanwhile, as for any other change (ADR-0032); saving again applies the form.
- A stored slot on a need or an ended item (no write produces one) isn't a priority and is cleared by the next compaction.
- The decision screen's "Keep 1,200 € for your Camera (#2)" stays with #25.
