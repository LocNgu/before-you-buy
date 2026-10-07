# ADR-0021: Cloud builds — SDK in the environment setup script, project config in a SessionStart hook

**Status**: accepted
**Type**: technical
**Date**: 2026-10-05

## Context

Claude cloud sessions start without the Android SDK. Claude Code's docs recommend an **environment setup script** for provisioning toolchains (it runs before Claude starts and its result is cached as a filesystem snapshot for about 7 days, if it finishes within about 5 minutes) and a **SessionStart hook** for per-project setup that should also work locally. An alternative is to work around a blocked Gradle wrapper download by copying the image's pre-installed Gradle into the wrapper cache; that breaks when the image's Gradle major version differs from the wrapper's (the current image ships Gradle 8; AGP 9 needs Gradle 9).

## Decision

- The Android SDK is installed by a script kept in the repo (`scripts/install-android-sdk.sh`) and called from the environment's **setup script**, so it is cached.
- A fast, idempotent **SessionStart hook** (cloud sessions only, via `CLAUDE_CODE_REMOTE`) writes `local.properties` and verifies the SDK is present, printing a clear message if not.
- The Gradle wrapper downloads its distribution normally. The environment allowlists `dl.google.com`, `downloads.gradle.org` and `release-assets.githubusercontent.com` instead of seeding the wrapper by copying another Gradle.

## Consequences

- Faster session start after the first one; no version-coupling hack.
- The owner must configure the environment once (network allowlist + setup script), issue #7.
