---
description: Release-cutting workflow
paths:
  - "CHANGELOG.md"
  - "version.properties"
---

# Release workflow

Triggered when the human asks to cut a release ("do a release", "bump to X.Y.Z", "prepare a release PR").

> **Not yet wired.** There is no versioning scheme or release CI in this repo — those come with the
> app module. Decide then whether the version lives in a `version.properties`-style file, the
> catalog, or `build.gradle.kts`, and update this file. The shape below is what worked before.

1. **Determine the version** — ask if unspecified; semver (features → MINOR, fixes only → PATCH).
2. On a `claude/<kebab>` branch, update:
   - the version source of truth
   - `CHANGELOG.md` — promote `## [Unreleased]` → `## [X.Y.Z] - <today>`, add a fresh empty
     `## [Unreleased]` above it
   - `README.md` — any new features not already listed
   - `AGENTS.md` / `.claude/CLAUDE.md` — any missing conventions or pointers
   - in-app "what's new" content, if the app grows such a screen. Back it with a test asserting the
     newest entry matches `BuildConfig.VERSION_NAME`, so a forgotten entry fails CI instead of shipping.
3. **Commit + push**: `chore: bump version to X.Y.Z, promote changelog, update docs`.
4. **PR #1** — `claude/<branch>` → `develop`, title `chore: release prep for X.Y.Z`.
5. **PR #2** — `develop` → `main`, title `Release X.Y.Z`. Body lists Added/Fixed/Changed from the new
   CHANGELOG section, and notes that PR #1 must merge first.
6. **Human merges both**, in order.

Opening both release PRs is pre-authorized; merging is human-only.
