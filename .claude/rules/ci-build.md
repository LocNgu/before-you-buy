---
description: Cloud build environment, Android SDK install and the SessionStart hook
paths:
  - "scripts/**/*"
  - ".claude/settings.json"
  - "**/*.gradle.kts"
  - "gradle/**/*"
  - ".github/**/*"
---

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
