# ADR-0018: Money as Long minor units; time from an injected Clock, calendar-day arithmetic

**Status**: accepted
**Type**: technical
**Date**: 2026-10-05

## Context

Floating-point money produces rounding errors. Time-dependent rules (cooling-off, accrual, notifications) must be testable, and adding fixed milliseconds per day breaks across daylight-saving changes.

## Decision

- Money is a value type over `Long` minor units plus a currency code. Never `Double`/`Float`.
- Domain functions take `now`/`Clock`/`ZoneId` as parameters; nothing calls `Instant.now()` directly.
- Day arithmetic uses calendar days (`ZonedDateTime`/`LocalDate`), never `+ 86_400_000`.
- Any UI state that depends on "today" (countdowns) gets a day-change signal as an input so it updates at midnight.

## Consequences

- Time-based behaviour is fully testable with a fake clock.
