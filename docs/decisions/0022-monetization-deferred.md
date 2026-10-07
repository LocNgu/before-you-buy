# ADR-0022: Free for now; monetization decided later

**Status**: accepted
**Type**: product
**Date**: 2026-10-04

## Context

Options: free + one-time Pro unlock, paid upfront, tip jar, subscription. Monetizing makes the owner an EU DSA "trader" (public address and phone on the listing) and likely requires a German business registration.

## Decision

The first release is free with no monetization. The options are kept in issue #41.

## Consequences

- No billing code in the MVP. When monetization is chosen, a superseding ADR records it, including the check that the billing library doesn't add `INTERNET` (ADR-0003).
