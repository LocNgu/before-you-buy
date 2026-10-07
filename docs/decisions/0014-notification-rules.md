# ADR-0014: Max one notification a day; "Still want it / Let it go"; never money or deals

**Status**: accepted
**Type**: product
**Date**: 2026-10-04

## Context

In the *one sec* study the explicit option to dismiss was the most effective element. Notification fatigue is a main reason apps get abandoned. The app must never behave like a shop.

## Decision

- At most one notification per day; several due items are grouped.
- Cooling-off-ended notifications carry two actions: **Still want it** and **Let it go** (records a rejection without opening the app).
- One reflection nudge per item.
- Never notify about available money, deals, prices or suggestions.

## Consequences

- Fewer re-engagement touchpoints than typical apps; accepted.
