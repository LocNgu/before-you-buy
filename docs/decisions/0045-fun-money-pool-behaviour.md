# ADR-0045: Fun money: accrual from the next accrual day, an append-only ledger, calm labels, setup from Home

**Status**: accepted
**Type**: product
**Date**: 2026-10-23

## Context

#22 builds the discretionary pool of ADR-0010 (brief §4.5). The issue left open when the first monthly amount arrives, how the three amounts are labelled (the brief's "available / committed / unallocated" sits close to the "money available" ADR-0014 rules out), what the feature is called, and where it is set up: the issue asks for a Settings section, but the Settings screen is #26 (M3) and ADR-0043 gives Home no Settings entry until it exists. The owner chose the options below on 2026-10-23.

Alternatives considered:

- **Credit the current month at setup.** Setting up 300 € on 23 Oct would add October's 300 € at once, on top of the starting balance, which already says what the user has today.
- **The brief's labels** ("300 € available · 200 € committed · 100 € unallocated"): "available" reads as money to spend.
- **A minimal Settings screen now** with Home's top-bar entry: pulls part of #26 forward and amends ADR-0043 for one section.
- **A stored "last accrued month"** next to the allowance: a second source of truth that can disagree with the ledger.

## Decision

- **Name:** "Fun money" in English; "Spaßgeld" in German, the literal equivalent, chosen by the owner over "Taschengeld" and "Spielgeld" (toy money), "Wunschgeld", "Wunschtopf" and "Monatsbetrag".
- **Setup** is its own screen, opened from Home's "Fun money" section ("Set up", then "Change or adjust"). #26 links to the same screen; Home still gets no Settings entry until then. The section replaces ADR-0043's placeholder and is the only tappable part of it.
- **First credit:** the first accrual day on or after the setup date. Setting up on the accrual day itself credits that day; otherwise the next accrual day is the first (23 Oct with the 1st → 1 Nov). The starting balance covers the time before. The form shows the date ("First added on 1 Nov 2026").
- **Accrual days** are 1–28, so every month has one; nothing is shifted to month ends. A stored value outside that range is clamped to it on read.
- **Catching up:** every month due since the last credited one is credited at once, at app start, whenever the app comes to the front, and in the daily worker, oldest first, each at most once. "Today" is the device's calendar date (ADR-0018).
- **Changes** to the amount or day first credit every month already due under the old settings, then apply from the next accrual on. The first credited month stays, so moving the day before the first credit neither skips nor adds a month. Later, an earlier day can make the current month due right away; a later one waits for it. The screen says "A new monthly amount or day applies from the next monthly amount on."
- **Ledger:** entries for the starting amount, each month's accrual, adjustments (± with an optional note) and purchases (#23). Accruals, adjustments and purchases are append-only. The last credited month is derived from the ledger; a unique month index makes a second credit impossible, whoever writes it.
- **The starting amount stays editable** on the same screen, like the monthly amount and day: it's a setting, not an event, and replacing it keeps its original date and changes the balance at once. The owner: the app is not a budget planner, so correcting a number shouldn't need bookkeeping. Lowering it never asks, even if it leaves less than nothing not yet set aside (owner's decision after review): it's a correction of a setting, not a take-out.
- **Labels:** "Fun money for wants", "Set aside for wants", "Not yet set aside" (German: "Spaßgeld für Wünsche", "Für Wünsche zurückgelegt", "Noch nicht zurückgelegt"). These replace the issue's "available for wants"; nothing says "available" or "spend".
- **Adjustments:** "Add" and "Take out", equal buttons. Taking out more than is not yet set aside asks first ("Go below zero?"); adding never asks. A confirmed take-out may leave the balance below zero.

## Consequences

- An app left open on screen across midnight of the accrual day shows the old balance until it next comes to the front or the daily worker runs.
- #23 passes the real committed amount where #22 passes zero; the labels and screen stay.
- A currency change relabels the monthly amount and every ledger entry with the items (ADR-0033).
- Pausing the allowance isn't possible yet; an amount of 0 has the same effect.
