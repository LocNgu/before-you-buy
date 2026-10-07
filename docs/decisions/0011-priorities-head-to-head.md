# ADR-0011: 1–5 priority slots (default 3); new items win a slot head-to-head

**Status**: accepted
**Type**: product
**Date**: 2026-10-04

## Context

A wishlist without limits becomes an endless collection. Options for competing: head-to-head comparison, manually choosing what to demote, or a score. Side-by-side (joint) evaluation leads to more considered choices than evaluating each option alone; scores get gamed.

## Decision

- Configurable 1–5 priority slots, default 3.
- With a free slot, an item can be promoted directly.
- With full slots, the new item is compared head-to-head with current priorities ("Which would you rather have?"), starting from the lowest rank. The loser moves back to the wishlist.
- Ranks change only through head-to-head (no drag-to-reorder).

## Consequences

- A new want can never take a slot without at least one comparison.
