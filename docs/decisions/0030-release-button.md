# ADR-0030: Releases start from a "Run workflow" button that tags after a successful build

**Status**: accepted
**Type**: technical
**Date**: 2026-10-12

## Context

ADR-0029 started a release only by pushing a tag `v*`. The owner tags on GitHub's web page or asks Claude, not on the command line. The web page has no way to create just a tag: the release form creates the release and the tag together, so the first release (`v0.1.0`) failed at publish because the release already existed. Claude's GitHub tools can't create tags or releases, but can start a workflow run. Alternatives considered:

- **Web-page releases only** (attach to the existing release, #57): works, but the release is visible without an APK while it builds, and a failed build leaves a published, empty release.
- **Claude pushes the tag with `git`:** cloud sessions' git access may be limited to the session's branch, and a tag pushed by mistake is public.

## Decision

Amends ADR-0029's trigger clause ("Releases only for version tags `v*` on `main`"); everything else in ADR-0029 stands.

- `release.yml` also runs on `workflow_dispatch` with an input `version` (`X.Y.Z`), only from `main`. It fails if the tag `vX.Y.Z` already exists.
- It runs the same checks and signed build. Only after they pass does the publish job create the release and the tag, at the commit that was built. A failed run publishes nothing.
- This button is the recommended way to release, for the owner (Actions → Release → Run workflow) and for Claude (`run_workflow`). Pushing a tag and publishing a release on the web page keep working.
- The `release` environment admits branch `main` as well as tags `v*`, because a button run starts on `main`.

## Consequences

- Releases are still tags on `main` (ADR-0020); only who creates the tag changes.
- Any job on `main` that declares `environment: release` can read the signing secrets. Only the owner merges to `main`, and only `release.yml` declares that environment.
- A tag created by the workflow's token doesn't trigger the tag-push path, so a button run never runs twice.
