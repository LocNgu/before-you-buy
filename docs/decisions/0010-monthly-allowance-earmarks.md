# ADR-0010: Discretionary money = auto-accruing monthly allowance; savings are earmarks

**Status**: accepted
**Type**: product
**Date**: 2026-10-04

## Context

Without a bank connection (ADR-0002) all money is entered manually. Options: a monthly allowance that accrues automatically, a manually updated pot balance, or goal deposits only. Manual upkeep is a main reason finance apps get abandoned.

## Decision

- The user sets a monthly allowance and accrual day; the pool grows automatically each month. Optional starting balance and manual adjustments.
- Money can be allocated to active items (earmarks). `unallocated = pool − committed`.
- Reject releases an item's allocations; buy consumes them.
- The app never moves or tracks real money; amounts are labels the user assigns.

## Consequences

- Near-zero upkeep after setup.
- The pool can drift from reality; adjustments exist for that.
