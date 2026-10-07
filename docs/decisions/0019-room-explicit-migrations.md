# ADR-0019: Room — explicit migrations only, hard crash if missing, schemas committed

**Status**: accepted
**Type**: technical
**Date**: 2026-10-05

## Context

`fallbackToDestructiveMigration()` silently deletes user data when a migration is missing. With local-only data (ADR-0002) there is no server copy to recover from.

## Decision

- Never use destructive fallback. A missing migration crashes on start.
- Room schema export is on; every schema version's JSON is committed.
- From the first public release on, every schema change ships an explicit `Migration` and a migration test.
- Enums are stored as strings and read defensively (unknown value → a fallback), so old rows and backups stay readable.

## Consequences

- Schema changes cost a little more work; user data is never silently lost.
