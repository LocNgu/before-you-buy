# ADR-0002: Local-only data; no account, server, bank connection or analytics

**Status**: accepted
**Type**: product
**Date**: 2026-10-04

## Context

Options were: local only, local plus optional Google Drive backup, account with cloud sync, or bank connection (PSD2). Sync and bank access mean a backend, GDPR obligations, running costs and (for banking) a paid aggregator. Privacy is also a selling point against competitors.

## Decision

All data stays on the device. No account, no server, no analytics or crash-reporting SDKs, no ads, no bank connection. Backup is a user-initiated export/import file plus Android system backup.

## Consequences

- Money amounts in the app are entered by the user (allowance, adjustments); the app never knows real balances.
- Multi-device sync is impossible without a new decision.
- The Play Data safety form can say "no data collected or shared".
