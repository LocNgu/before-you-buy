---
description: Release-cutting workflow (version bump, changelog promotion, two release PRs)
paths:
  - "version.properties"
  - "CHANGELOG.md"
---

# Release Workflow

Triggered when the human asks to cut a release ("do a release", "bump to X.Y.Z", "prepare a release PR").

1. **Determine the version** — ask if unspecified; semver (new features → MINOR, fixes only → PATCH).
2. **Update these files** on a `claude/<kebab>` branch:
   - `version.properties` — bump `MINOR`/`PATCH` (or `MAJOR`)
   - `CHANGELOG.md` — promote `## [Unreleased]` → `## [X.Y.Z] - <today>`, add a fresh empty `## [Unreleased]` above it
   - `README.md` — add any new features not already listed
   - `.claude/CLAUDE.md` — add any missing conventions/pointers
   - any in-app "what's new" content, if the app has such a screen (add it to this list when it exists —
     back it with a test asserting the newest entry matches `BuildConfig.VERSION_NAME`, so a forgotten
     entry fails CI instead of shipping)
3. **Commit + push** to the feature branch: `chore: bump version to X.Y.Z, promote changelog, update docs`.
4. **Create PR #1** — `claude/<branch>` → `develop`, title `chore: release prep for X.Y.Z` (docs/version-only).
5. **Create PR #2** — `develop` → `main`, title `Release X.Y.Z`. Body lists all Added/Fixed/Changed from the new
   CHANGELOG section; note PR #1 must merge first.
6. **Human merges both** (in order). CI builds the signed release APK on merge to `main`.

No DB migration or new tests needed for a docs-only release-prep PR. Opening both release PRs is pre-authorized;
merging is human-only.
