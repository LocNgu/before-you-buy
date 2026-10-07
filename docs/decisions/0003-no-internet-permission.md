# ADR-0003: No INTERNET permission, enforced by CI

**Status**: accepted
**Type**: technical
**Date**: 2026-10-04

## Context

Shared links could be enriched online (title, price, image via Open Graph), but that needs the `INTERNET` permission, is unreliable on shops like Amazon, and would weaken the strongest privacy claim. Link text from the share sheet can be parsed offline.

## Decision

The app ships without `android.permission.INTERNET`. A CI check fails the build if the merged release manifest contains it. Shared text is parsed offline.

## Consequences

- Store listing can say "this app cannot send your data anywhere".
- No link previews (tracked as an opt-in idea that needs a superseding ADR, issue #39).
- Every new dependency must be checked for permissions it merges into the manifest (e.g. billing libraries).
