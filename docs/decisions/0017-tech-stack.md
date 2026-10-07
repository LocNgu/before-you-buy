# ADR-0017: Tech stack and module layout

**Status**: accepted
**Type**: technical
**Date**: 2026-10-05

## Context

The owner asked for industry-standard choices. Alternatives considered: manual DI (application-held singletons, no annotation processing) instead of Hilt, and Detekt instead of Spotless/ktlint. Google's reference architecture (Now in Android) uses Hilt and Spotless/ktlint, so those were chosen.

## Decision

- DI: **Hilt**. Persistence: **Room** (KSP) + **DataStore**. Navigation: **Navigation 3**. Background: **WorkManager**. Serialization: kotlinx.serialization.
- Formatting/lint: **Spotless + ktlint** and **Android Lint**.
- Tests: JUnit 4, coroutines-test, Turbine, Robolectric, **Roborazzi** screenshot tests (lets cloud sessions check UI without an emulator).
- Gradle version catalog.
- Modules: `:app`, `:core:domain` (pure Kotlin/JVM, all business rules), `:core:data`.

## Consequences

- More annotation processing than manual DI; standard documentation applies.
- Business rules are fast JVM unit tests with no Android dependencies.
