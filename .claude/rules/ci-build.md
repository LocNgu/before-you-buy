---
description: GitHub Actions CI, the cloud build environment, Android SDK install and the SessionStart hook
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

## Diagnosing a failed run

1. **Annotations first.** The hard-constraint checks (`checkNoInternetPermission`, `checkStringParity`) print a GitHub error annotation with the full message. Read the check runs of the PR's head commit (`pull_request_read` → `get_check_runs`, then `get_check_run` for the failed one). setup-gradle's job summary names the failed Gradle task.
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
