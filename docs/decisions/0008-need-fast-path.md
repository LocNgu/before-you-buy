# ADR-0008: NEED items skip cooling-off and reflection, still logged

**Status**: accepted
**Type**: product
**Date**: 2026-10-04

## Context

Urgent replacements (a broken fridge) can't wait 30 days. Without a path for them, users stop recording "real" purchases in the app.

## Decision

An item can be marked `NEED`. Needs skip the cooling-off and reflection requirements and go straight to the decision screen. They are recorded as needs in history. By default a need is not paid from the discretionary pool; the user can choose otherwise.

## Consequences

- The app stays useful for all purchases.
- Later insights can show whether "needs" were really needs.
