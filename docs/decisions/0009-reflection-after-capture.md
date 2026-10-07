# ADR-0009: Reflection after capture, required before Ready, includes reasons against

**Status**: accepted
**Type**: product
**Date**: 2026-10-04

## Context

Capture must take about 10 seconds or the user buys instead. Research shows listing reasons for **and against** a purchase reduces the urge to buy (`docs/research.md` §1).

## Decision

- Capture asks only for name and price.
- Reflection is asked afterwards (nudge after about 2 days, or from item detail).
- Required for Ready: why you want it, how often you'll use it, and at least one reason against. Other questions are optional.
- Answers are shown again on the decision screen.

## Consequences

- Fast capture is protected; an unreflected item simply can't become Ready.
