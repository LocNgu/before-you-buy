# ADR-0023: The dependency licence rule covers what ships; test and build tools are exempt

**Status**: accepted
**Type**: technical
**Date**: 2026-10-10

## Context

ADR-0004 allows only Apache-2.0, MIT and BSD dependencies. The rule exists so the distributed app carries no copyleft obligations and commercial use stays open to the owner.

The scaffold (#8) showed that the rule can't hold for tooling. JUnit 4 is EPL-1.0. ADR-0017 picks it, and Robolectric and Roborazzi require it, so there is no permissive alternative. Robolectric also pulls in `javax.annotation-api` (CDDL + GPLv2 with classpath exception), and AGP and Gradle plugins bring EPL/EDL/LGPL artifacts. None of this code ends up in anything we distribute: Gradle downloads it to run tests and builds, and it is not in the APK, the AAB or the repository.

EPL-1.0 and similar weak-copyleft licences only impose duties on whoever distributes that code. Code that merely calls it (our tests) is a separate work and stays under our licence.

## Decision

- **Shipped code: Apache-2.0, MIT or BSD only.** This covers every dependency that can end up in a distributed build: the release runtime classpath, and debug-only dependencies, because debug APKs can be shared.
- **Test-only dependencies, Gradle plugins and build tooling may use any OSI-approved licence**, provided they are never bundled into a distributed APK or AAB and their source is not copied into the repository.
- A PR that adds a dependency states its licence and which classpath it is on.

## Consequences

- JUnit 4, Robolectric and the current build tooling are allowed. What users receive is unchanged: our code under PolyForm Noncommercial (ADR-0004), libraries under Apache-2.0 and BSD-3.
- A licence check, if one is added, only needs to cover the release runtime classpath and debug-only dependencies.
- Apache-2.0 and BSD still require shipping their licence texts and notices with the app. A third-party licences screen or file is needed before the first Play release.
- Amends ADR-0004 (dependency licences clause).
