# ADR-0007: Waiting and saving in parallel; Ready = cooled + reflected + funded; stage derived

**Status**: accepted
**Type**: product
**Date**: 2026-10-04

## Context

The original lifecycle was linear (Idea → Cooling off → Considered → Saving → Ready → Purchased/Rejected). That makes a wanted item wait twice: first for the timer, then for savings.

## Decision

- Cooling off, reflection and saving happen in parallel. An item is **Ready** when it is cooled off **and** reflected **and** funded.
- The displayed stage (Cooling off / Needs reflection / Saving / Ready) is **derived** from those conditions and never stored. Only the status (`ACTIVE`, `PURCHASED`, `REJECTED`) and the underlying facts are stored.

## Consequences

- No stage column to migrate or keep in sync; stage logic lives in one pure domain function.
- An item can jump straight from Cooling off to Ready when its timer ends.
