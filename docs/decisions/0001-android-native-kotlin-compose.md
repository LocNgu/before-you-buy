# ADR-0001: Android only, native Kotlin + Jetpack Compose

**Status**: accepted
**Type**: technical
**Date**: 2026-10-04

## Context

The app targets Android. Options: native Kotlin/Compose, Compose Multiplatform or Flutter (for a possible iOS version). An iOS version is not planned. Code is written mostly by Claude sessions, so the best-documented, most conventional stack is the safest.

## Decision

Android only, native Kotlin with Jetpack Compose and Material 3.

## Consequences

- Best access to Android integrations the app depends on (share sheet, notifications, widgets, per-app language).
- An iOS version would need a rewrite of the UI layer. Keeping all rules in a pure Kotlin module (ADR-0017) keeps that option partly open.
