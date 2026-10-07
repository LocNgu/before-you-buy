# ADR-0006: Soft lock — early purchase allowed with friction, always logged

**Status**: accepted
**Type**: product
**Date**: 2026-10-04

## Context

Options: hard lock until the timer ends, soft lock with friction, or per-item choice. A hard lock risks users abandoning the app and buying anyway; the app's philosophy is "don't prevent buying, make sure it's worth it".

## Decision

A user can "decide early" on an item that isn't Ready. This requires typing a reason, seeing the reasons against and the remaining days again, and confirming. The purchase is recorded as an early purchase and shown as such in history and stats.

## Consequences

- The app stays usable for every purchase, at the cost of a weaker commitment device.
- Early purchases become visible data for the user's own learning (later "Was it worth it?" comparisons).
