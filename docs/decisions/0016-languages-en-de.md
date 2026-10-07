# ADR-0016: English + German from day one

**Status**: accepted
**Type**: product
**Date**: 2026-10-04

## Context

The owner's primary market is German-speaking; English widens reach.

## Decision

Every user-facing string exists in `values/` (English) and `values-de/` (German). CI checks both files have the same keys. Per-app language is supported. EUR is the default currency; one currency per installation.

## Consequences

- Every UI change touches two string files.
