---
description: GitHub Actions CI, the release workflow, the cloud build environment, Android SDK install and the SessionStart hook
paths:
  - "scripts/**/*"
  - ".claude/settings.json"
  - "**/*.gradle.kts"
  - "gradle/**/*"
  - ".github/**/*"
---

# GitHub Actions CI

`.github/workflows/ci.yml` runs on every pull request and on pushes to `main`. A newer push to the same PR cancels the older run (concurrency group); runs on `main` are never cancelled, because the docs-only filter only sees the latest push.

## Jobs

- **Detect changes** (`changes`): diffs the PR's base (or the push's previous commit) against the tested commit. If every changed file is under `docs/` or is a top-level `*.md` (renames count as delete + add), it outputs `android=false`; anything else, workflows included, gives `android=true`. Without a usable base (new branch, force push) it runs the Android job.
- **Verify and build release** (`android`, skipped for docs-only changes; a skipped job counts as passed): JDK 21 (Temurin), `android-actions/setup-android` (cmdline-tools and licences; AGP downloads the platform and build-tools itself), `gradle/actions/setup-gradle` (cache, wrapper validation; only `main` writes the cache), then `./gradlew verify` and `./gradlew assembleRelease` (unsigned, so R8 runs). On failure it uploads the artifact `reports-<attempt>`: `**/build/reports/`, `**/build/test-results/`, `app/build/outputs/roborazzi/` (screenshot diffs) and `app/build/outputs/logs/` (manifest merger report).
- CI runs `verify`, never its own list of tasks. A new check goes into `verify` (root `build.gradle.kts`) and CI picks it up.
- Actions are well-known ones pinned to a major version (`@v7`). Bump majors deliberately, one PR, after reading the release notes.
- `ci.yml` has `contents: read` and never sees a secret. PR builds stay unsigned; don't add signing to it.

# Release workflow (ADR-0027)

`.github/workflows/release.yml` runs on a pushed tag `v*`, never on branches. Owner steps (key, environment, tagging, installing): `docs/releasing.md`. All `run:` steps use `bash` (`-eo pipefail`).

**Job `build`** (`environment: release`, `contents: read`, checkout with `fetch-depth: 0` and `persist-credentials: false`):

1. **Tag is on main**: `git merge-base --is-ancestor` against `origin/main`.
2. **Signing secrets are set**: `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`, environment secrets of `release` (which admits only `v*` tags). Any missing fails the run before building.
3. Same setup as `ci.yml` (bump both together), but setup-gradle is `cache-read-only`.
4. `:app:releaseNotes -Papp.versionTag=<tag>`: validates the tag and writes the `CHANGELOG.md` section to `app/build/release/notes.md` (fails if missing or empty), before the long build.
5. `verify`, without any signing variables.
6. **Assemble signed release**: decodes the keystore to `$RUNNER_TEMP` (deleted on exit), sets `RELEASE_KEYSTORE_PATH`, runs `:app:assembleRelease -Papp.versionTag=<tag> --no-configuration-cache` (the plugin refuses to sign with the configuration cache on).
7. **Check signature and version**: requires `app-release.apk` (an unsigned build is `app-release-unsigned.apk`), `apksigner verify` from the newest stable build-tools, rejects `CN=Android Debug`, checks `versionCode`/`versionName` with `aapt2 dump badging` against its own computation from the tag, prints the public certificate digest, and puts `before-you-buy-<version>.apk` + `.sha256` and `notes.md` into the artifact `release`.

**Job `publish`** (`contents: write`, no checkout, no build code): downloads `release` and runs `gh release create <tag> out/apk/* --notes-file out/notes.md --verify-tag`.

Rules:

- Secrets go only into the steps that need them (`env:` per step), never into the job or workflow `env`. Never `echo` them, never `set -x` in those steps.
- `contents: write` exists only in the `publish` job. Never give it to a job that runs Gradle or third-party build actions.
- A re-run after the release exists fails at `gh release create`: delete the release (not the tag) first, or cut a new patch version.

## Diagnosing a failed run

1. **Annotations first.** The hard-constraint checks (`checkNoInternetPermission`, `checkStringParity`), `releaseNotes` and the release workflow's own checks print a GitHub error annotation with the full message. Read the check runs of the PR's head commit (`pull_request_read` → `get_check_runs`, then `get_check_run` for the failed one). setup-gradle's job summary names the failed Gradle task.
2. **Then the log, saved, never inline.** `get_job_logs` with `run_id` + `failed_only` and a modest `tail_lines` (about 200). If the result is saved to a file, `grep -nE 'FAILURE:|What went wrong|FAILED|e: |error:' <file>` and read only the lines around the hits. Never pull the full log into the conversation.
3. Reproduce locally with the failed task alone (`./gradlew :app:checkNoInternetPermission`, `--tests "…"`), fix, then run `verify` before pushing.
4. Screenshot diffs: the `*_compare.png` files in the uploaded artifact. Don't re-record goldens to make CI green without looking at why they differ.

# Cloud build environment (ADR-0021)

## Pieces

- **`scripts/install-android-sdk.sh`**: installs cmdline-tools, `platform-tools`, the platform for `compileSdk` and the newest stable `build-tools` of that major into `/opt/android-sdk`. Idempotent (about 3 s when everything is present, about 15 s fresh). Always exits 0 and prints warnings instead of failing.
- **Environment setup script** (cloud environment settings, owner-managed): `curl -fsSL https://raw.githubusercontent.com/LocNgu/before-you-buy/main/scripts/install-android-sdk.sh | bash || true`. Its result is cached as a snapshot.
- **`scripts/session-start.sh`** (SessionStart hook in `.claude/settings.json`): cloud only (`CLAUDE_CODE_REMOTE=true`). It writes `local.properties` (git-ignored) and exports `ANDROID_HOME` and `PATH` through `CLAUDE_ENV_FILE`. If the SDK is missing it prints one line telling you to run the install script. It must stay fast (milliseconds): no downloads.

## Network allowlist

Beyond the default package managers: `dl.google.com` (SDK), `downloads.gradle.org` and `release-assets.githubusercontent.com` (the Gradle wrapper distribution; `services.gradle.org` redirects there). `maven.google.com` and Maven Central are reachable by default.

## Gotchas

- **compileSdk detection:** the script reads `compileSdk` from `gradle/libs.versions.toml`, `app/build.gradle.kts` or `build.gradle.kts`. Piped through `curl | bash` there is no checkout, so it uses `DEFAULT_COMPILE_SDK` from the top of the script. **When compileSdk changes, bump that default too.** `ANDROID_COMPILE_SDK=<n>` overrides both.
- **API 37+ platform ids have a minor version** (`platforms;android-37.0`, not `-37`). The script prefers the bare id and otherwise picks the lowest stable minor.
- **cmdline-tools is pinned to build 15859902 on purpose.** Build 16111833 (cmdline-tools 23) turns `sdkmanager` into a deprecated wrapper around the new `android` CLI. That wrapper calls `play.google.com` (blocked) and reports failures for installs that succeeded. Re-test before bumping. Bump when a new `compileSdk` can't be found, because older builds don't know newer packages.
- **Install success is checked on disk, not by exit code.** `yes | sdkmanager` under `pipefail` exits non-zero even on success.
- Don't seed the Gradle wrapper by copying the image's pre-installed Gradle (Gradle 8): AGP 9 needs Gradle 9, and the wrapper downloads it itself.

## Related

Gradle, modules, versions and the verify task: `.claude/rules/build.md`.

## Diagnosing

1. `curl -sSI https://dl.google.com/android/repository/repository2-3.xml`: anything other than 200 means the network allowlist is wrong.
2. `scripts/install-android-sdk.sh`: read its `[android-sdk]` lines.
3. `ls /opt/android-sdk/platforms /opt/android-sdk/build-tools` and `cat local.properties`.
4. Proxy errors: `curl -sS "$HTTPS_PROXY/__agentproxy/status"`.
