# ADR-0041: History: one period filter, "Want it again?" creates a fresh want without answers, deleting only on purpose

**Status**: accepted
**Type**: product
**Date**: 2026-10-21

## Context

History (#19, brief §6.6) has two tabs, "Bought" and "Decided against", with a plain total on "Decided against" (ADR-0012). The issue leaves open where History is opened from before the Home dashboard exists (#21), what "Want it again?" copies into the new item, what it does for a need, how the optional period filter works, and what deleting removes. The owner chose these on 2026-10-21.

Alternatives considered:

- **"Want it again?" copies the reflection answers too**: the new item would be Ready the moment its wait ends, on the strength of reasons written before the user decided against it. Thinking it over again is the point of a new cooling-off period.
- **"Want it again?" keeps a need a need**: no wait at all, which contradicts "new cooling-off from now" in the issue. The user can still mark it as a need afterwards, which asks first (ADR-0008).
- **"Want it again?" opens a prefilled quick add**: lets the price be adjusted first, but loses the photo, category and notes, and adds a form to what should be one confirmed tap. The price can be edited on the new item.
- **A filter only for "Decided against"**, or none: the issue asks for one; one shared filter is simpler to read than two.
- **A separate History entry in the wishlist's top bar**: Home is the app's hub; the dashboard (#21) decides the final place.

## Decision

- **Opened from Home** ("History" under "Wishlist") until the dashboard (#21) places it.
- **Two tabs**, "Bought" first. **Bought**: what was paid, the date, the wishlist price only where it differs from what was paid, "Considered for N days" (calendar days from adding to buying, ADR-0018; "Bought on the day it was added" for 0), and "Bought early" / "Need" flags. **Decided against**: price, date and the optional "Why not?". Both newest first; tapping opens a read-only detail with the user's reasons as they were.
- **The total** sits as plain text at the top of "Decided against": "You decided against 3,420 € of purchases", the exact sum rounded once (ADR-0034). The MVP has one currency, so every amount must be in it; a total over two currencies throws instead of being shown (ADR-0033). While a currency change is being written and the history and the currency are read apart, the screen waits for the matching read.
- **One period filter for both tabs**: All (default), This month, This year: the calendar month or year of the decision date in the device zone, recomputed at midnight. The total follows the filter ("This month you decided against …").
- **"Want it again?"** (decided-against detail, after a confirm) adds a **new want** with the old item's name, price, link, photo, category, notes and a target date that's still ahead. It cools off from now under the current tiers, has no reflection answers, and is always a want, even if the old item was a need. The old entry stays in "Decided against" unchanged, and the new item's detail opens.
- **Delete** is in the detail's overflow menu and asks first. It removes the item with its answers and decisions (and so from the total); the photo file goes with the next sweep if nothing else uses it (ADR-0038). It is compare-and-set like every item write (ADR-0032).

## Consequences

- An item wanted again shares its photo file with the history entry; deleting either keeps the file while the other still references it.
- A user can still grow the "Decided against" total with throwaway wishes (research §1, "Moral licensing"). The total is plain text and never shown next to anything Ready (ADR-0012), which keeps the incentive small.
- "Was it worth it?" ratings and insights (Later) will need their own fields on the purchase; nothing here stores them.
