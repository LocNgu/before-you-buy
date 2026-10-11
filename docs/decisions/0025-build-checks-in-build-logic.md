# ADR-0025: Project-specific build checks are tested Gradle plugins in `build-logic`

**Status**: accepted
**Type**: technical
**Date**: 2026-10-10

## Context

ADR-0003 and ADR-0016 need checks that fail the build: no `INTERNET` in the merged release manifest, and the same strings in `values/` and `values-de/`. They must run locally and in CI through the one `verify` task. Alternatives considered:

- **Shell or Python steps in the workflow:** don't run locally, so `verify` and CI would differ.
- **Inline tasks in the build scripts:** no tests, and lambdas in build scripts break the configuration cache easily.
- **`buildSrc`:** works, but any change to it invalidates every project's build-script classpath, and its tests no longer run by default since Gradle 8.
- **Relying on Android Lint** (`MissingTranslation`): covers German strings only partly (not German-only or `translatable="false"` duplicates in one clear message), and nothing in Lint inspects the merged release manifest for a forbidden permission.

## Decision

- Checks specific to this project live in `build-logic/`, an included build (`pluginManagement { includeBuild("build-logic") }`), as small Gradle plugins with typed tasks (lazy properties, configuration-cache safe).
- The logic is plain functions with JUnit tests; `build-logic`'s `:test` is part of `verify`.
- `build-logic` compiles against AGP's public `gradle-api` only (`compileOnly`); the app build provides AGP at runtime.
- A failing check prints a message that names the ADR, the offending file and the fix, and also emits a GitHub Actions error annotation when it runs in CI.

## Consequences

- New hard constraints follow the same pattern: plugin + tested logic + a line in `verify`.
- `build-logic` is not an Android module: it has no Lint, but Spotless covers it.
- It shares the version catalog (`gradle/libs.versions.toml`) with the main build, so AGP versions can't drift apart.
