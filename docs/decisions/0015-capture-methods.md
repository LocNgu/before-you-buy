# ADR-0015: Capture via share sheet + quick add; widget later; no app interception

**Status**: accepted
**Type**: product
**Date**: 2026-10-04

## Context

The impulse happens in a shop app or browser. Options: share sheet, manual quick add, home-screen widget, or intercepting shopping apps (like *one sec*). Interception is effective in research but needs sensitive permissions (accessibility/usage access) with Play policy risk.

## Decision

- MVP: share-to-app (offline parsing) and manual quick add.
- Home-screen widget after the first release.
- No interception of other apps.

## Consequences

- The app relies on the user choosing to share/add an item at the moment of impulse.
