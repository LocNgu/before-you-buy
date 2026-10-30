# ADR-0049: Only priorities wait: the waiting period runs only while a want holds a priority slot, and only priorities can be Ready

**Status**: accepted; supersedes ADR-0007
**Type**: product
**Date**: 2026-10-26

## Context

ADR-0005 starts every want's waiting period when it is added, and ADR-0007 lets all wants wait and save in parallel, with Ready = cooled off + reflected + funded and no role for priorities (ADR-0011). So a user can add a dozen wants, forget them, and later find them all past their wait. With enough fun money set aside, or with "Wants need their price set aside" switched off (ADR-0046), they could all be bought on the same day. Nothing makes the user choose an item over the others before its wait starts.

ADR-0011's context ("A wishlist without limits becomes an endless collection") meant to address this, but its decision limits only the priority slots. The wishlist itself has no limit.

The owner's original intent (the "3 active purchases" rule): an unlimited wishlist, but only a few active items; nothing is bought from the rest; a new want has to beat a current priority; the timer starts when an item becomes active; only active items get money. The owner decided on 2026-10-26 (#82, option B).

Alternatives considered:

- **A: a hard cap on the wishlist.** Rejected: it would block fast capture from the share sheet (ADR-0015) and push wants into other apps.
- **Everyone waits, but only priorities can be Ready.** Rejected: the timer would run out before the user has chosen anything, so nothing is decided at the moment the wait starts.
- **Losing a slot resets the wait.** Rejected: it would feel like a punishment (ADR-0012).
- **Slots per size band (small / medium / large).** Parked for later (#85). Switching to bands later is about one feature PR; switching back would be harder.

## Decision

"**Parked**" is the internal term for an active want that holds no priority slot. The screen calls these items simply "Wishlist".

- **The waiting period runs only while a want holds a slot.** It starts when the want gets a slot: directly while one is free, otherwise by winning the head-to-head (ADR-0011, ADR-0047). A parked want has no timer running.
- **A new want is always parked**, even when a slot is free. Its wait starts only through "Make it a priority".
- **Losing a slot pauses the wait. It never resets it.** The time already waited is kept and the wait continues the next time the item gets a slot. This holds when the item loses a head-to-head, when "Remove from priorities" is used, and when the number of slots is reduced.
- **The app keeps how long a want has waited**, in calendar days (ADR-0018), instead of a fixed end date: the time it spent in a slot. The wait is complete once that reaches the required period. A priority shows its end date; a parked want has none. How a part of a day is counted when a slot is lost is for the domain PR; it must never credit time that hasn't passed.
- **The required period follows the rules as before.** It is `period(price)` under the tiers; a price edit only extends it (ADR-0005); a tier change gives every active want whose wait isn't complete the period of its current price under the new tiers, longer or shorter (ADR-0031). What changes is what it is measured against: the time already waited, which stays. A complete wait stays complete.
- **Only a priority can be Ready.** Ready for a want = holds a slot + waited its full period + reflected + funded. Needs stay Ready at once (ADR-0008, ADR-0044) and still can't hold a slot (ADR-0047). The stage stays derived and never stored (ADR-0007's rule, carried over). The stage table gains **Parked** (an active want without a slot), checked first: it wins over every other stage, so a parked want is never Ready, however long it has waited. Promoting a want that has already waited its period makes it Ready at once, as soon as the answers (and the money, where required) are in.
- **A parked want can be reflected on but not saved for.** The questions can be answered at any time. Money can be set aside only while the item holds a slot (amends ADR-0046), so fun money goes to items the user chose. Money already set aside on an item that loses its slot stays with it: nothing is taken back silently, "Take back" still works, and no new money can be set aside until it holds a slot again.
- **Existing items:** current priorities keep their slots and their end dates. Every other active want is parked, so it isn't Ready any more, and the time it has waited so far (since it was added) counts toward its wait, so promoting it later loses nothing. Money already set aside stays.
- **A need turned into a want** is parked and keeps the time it had waited as a want (none, if it was captured as a need). This amends ADR-0031's "recomputes its end from `addedAt`".
- **Buying a parked item.** Item detail and the decision screen say "Not one of your priorities" and offer **"Make it a priority"**: a free slot is taken at once, otherwise the head-to-head opens. **"Decide early…"** is the only way to buy a parked want. It stays possible (ADR-0006: don't prevent spending) and is logged as early. Instead of "days left" it says that the item isn't one of the priorities and how much of its waiting period is done (not started / N of M days / done). The reasons against and the "Why buy it now?" field stay. Plain Buy and Wait are never offered (ADR-0040). Reject / "Let it go" stays as easy as Buy on every item, parked or not (ADR-0012).
- **No rank number for parked items.** Ranks exist only for priorities (ADR-0011).
- **Adding a want.** While typing the price in quick add, a quiet line under the field shows the tier: "Waiting period: 90 days, once it's a priority" (wants only; a need keeps "Needs skip the waiting period"). After saving in the app, the snackbar says "Added to your wishlist." with a **"Make it a priority"** action; this replaces "Added. You can decide in N days." (ADR-0035). After share capture the toast says "Added to your wishlist." and has no action: a toast over another app can't have one (ADR-0036).
- **Wishlist layout:** **Priorities** (by rank, each with its stage: Cooling off / Needs reflection / Saving / Ready / Looking again later), then **Needs** (if any), then **Wishlist** (every parked want). The stage groups for non-priorities go away: no parked want can be Ready and none is counting down. The "Looking again later" group (ADR-0040) becomes a stage of a priority.
- **No notifications for parked items.** The "waiting period over" notification (ADR-0042) fires only for priorities, and so does the reflection nudge. Parked items are left alone on purpose: a want the user doesn't care enough about to promote is the one this filters out. ADR-0014's limit of one notification a day stands.
- **Home** (ADR-0043, ADR-0047): "To decide" and "next waiting period ends" count only priorities and needs. "Considering" still counts every active item. "Saving for" lists the priorities only; its fallback to wants by progress (ADR-0046) goes, because only priorities can be set aside for.
- **Slots stay N for any price:** 1–5, default 3 (ADR-0011, ADR-0047).

## Consequences

- A user with twelve wants and three slots has nine parked, none waiting, none notified. To buy one of them they promote it (winning the head-to-head if the slots are full) and wait, or use "Decide early…", which is logged.
- The wait now means something: it runs for items the user chose, and only those can become Ready. The cost is that a want the user does want, but hasn't promoted, never becomes Ready by itself.
- Priorities can be juggled: an item that loses its slot keeps what it waited. At most N items wait at once, so juggling can't make many waits run in parallel.
- ADR-0007 is superseded; its remaining rules are restated above (stage derived and never stored, reflection and saving next to the wait). ADR-0005 (fixed end when added), ADR-0011 (a slot also starts the wait and makes Ready possible), ADR-0031 (tier changes against the time waited; need to want), ADR-0046 (setting aside needs a slot) and ADR-0035, ADR-0036, ADR-0040, ADR-0042, ADR-0043, ADR-0047 (the clauses named above) are amended.
- The brief (§4.1, §4.2, §4.6, §4.7, §5, §6.1–§6.3, §6.5, §8) and the lifecycle diagram follow this ADR in the same PR. The code follows in later PRs (domain and Room with an explicit migration, ADR-0019; UI; Home and notifications), so until they land `main` describes rules the app doesn't implement yet, and a release in between would ship a half-way state.
- Out of scope: a hard cap on the wishlist, slots per size band (#85), reminders about parked items, expiring or auto-rejecting old parked items, ranking parked items.
