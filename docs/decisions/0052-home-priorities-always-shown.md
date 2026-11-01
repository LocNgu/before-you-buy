# ADR-0052: Home: the Priorities section is always shown, and "Saving for" goes

**Status**: accepted
**Type**: product
**Date**: 2026-11-01

## Context

ADR-0049 decided that Home's "Saving for" lists the priorities only, and that its fallback (up to three wants by how much is set aside, ADR-0046) goes, because only priorities can have money set aside. Until now Home showed "Priorities" once there was one (ADR-0047) and otherwise "Saving for" with that fallback, a hint to set up fun money, or "No wants to set money aside for yet." With the fallback gone, the section without priorities could only ever be empty, so the Home PR of #82 had to say what it shows then. The owner chose on 2026-11-01.

Alternatives considered:

- **Keep "Saving for" while there are no priorities**, with new lines ("Make a want a priority to set money aside for it." / "Set up fun money to set money aside for your priorities."). Rejected: the same section would change its heading once the first priority arrives, and the fun money hint repeats what the "Fun money" section below already offers.
- **"Priorities" with a second line about money** while fun money is set up ("… to start its waiting period and set money aside for it."). Rejected: one more string for something the "Fun money" section and item detail already say.

## Decision

- **Home always shows "Priorities"**, with or without priorities, as the wishlist does (ADR-0051). With priorities it lists them by rank as before (ADR-0047): rank, name, and what is set aside, or the price without fun money.
- **Without priorities** it shows one quiet line, the wishlist's: "Make a want a priority to start its waiting period." The same line whether fun money is set up or not.
- **"Saving for" goes**, with its lines "Set up fun money to set money aside for your wants." and "No wants to set money aside for yet."
- **A parked want is never listed**, not even one that still holds money from before it lost its slot (ADR-0049). That money still counts under "Set aside for wants" in the "Fun money" section and shows on the item.

## Consequences

- Home and the wishlist use the same heading and the same empty line, so a new user sees on both screens that a want has to become a priority to start waiting.
- Home answers "What am I saving for?" (brief §6.1) with the priorities alone; there is no separate list of wants by progress any more.
- Money left on a parked want is visible only on the wishlist and item detail, not on Home. Accepted: ADR-0049 keeps it there until it's taken back, and Home is about what the user chose.
- Amends ADR-0043 (the "Saving for" section), ADR-0046 (Home's "Saving for") and ADR-0047 ("Without priorities it stays as ADR-0046 says").
