---
description: Build toolchain, Detekt, CI job graph, and cloud/session build setup
paths:
  - "**/*.gradle.kts"
  - "gradle.properties"
  - "gradle/**/*"
  - "version.properties"
  - ".github/**/*"
  - "config/detekt/**/*"
  - "scripts/**/*"
---

# CI / Build rules

## Toolchain (AGP 9.3.1 / Gradle 9.7.0 / Kotlin plugins 2.4.10 / KSP 2.3.11)
- Compose BOM 2026.06.01 · compileSdk 37 · targetSdk 35 · minSdk 26.
- **Kotlin + KSP move together** — KSP2 uses Kotlin-aligned versioning (KSP `2.3.10` = Kotlin `2.3.10`).
  A Kotlin version is not adoptable until KSP ships a matching release (which is why a grouped
  Kotlin-2.4 + KSP-2.3 Dependabot PR can never go green).
- **AGP 9 provides Kotlin compilation itself** — the standalone `org.jetbrains.kotlin.android` plugin is NOT
  applied and AGP 9 errors if it is present. Do not add it. The Compose/serialization/KSP plugins stay.
- `kotlinOptions { jvmTarget }` does not exist under AGP 9 — use top-level
  `kotlin { compilerOptions { jvmTarget.set(JVM_17) } }`.
- `gradle-wrapper.properties` distribution **and** the `gradle-version` pins in `android.yml` must match
  (`setup-gradle` overrides the wrapper).
- `android.onlyEnableUnitTestForTheTestedBuildType=false` in `gradle.properties` restores pre-AGP-9
  behaviour so `testReleaseUnitTest` exists for the release job. It's global, so `./gradlew test` runs both
  the debug and release suites.
- **Pin every dependency to an exact version.** No `libs.versions.toml`; versions are inlined in
  `app/build.gradle.kts` and the Compose BOM aligns the Compose artifacts.

## Detekt
- `Run Detekt` in the `test` job fails PRs on new violations. Config: `config/detekt/detekt.yml`
  (`buildUponDefaultConfig`; formatting `maxLineLength` 120). `FunctionNaming` + `MagicNumber` are active but
  `excludes: ['**/ui/**','**/test/**','**/androidTest/**']` (skip `@Composable`/test naming + Compose dp/sp literals).
- Frozen smells belong in `config/detekt/baseline.xml` — generate with `./gradlew detektBaseline` ONLY when
  intentionally accepting debt. `./gradlew detekt` locally; `autoCorrect = true` auto-fixes formatting
  (CI never auto-corrects).

## CI job graph
`test` (Detekt + unit tests + lintDebug) gates both `build` (debug APK) and `release`; release also runs
`testReleaseUnitTest` + `lintRelease`. Instrumented tests run on PRs via path filter; a concurrency group
cancels stacked runs. Push to `main` auto-creates a signed-APK GitHub Release (`--target SHA` anchors the tag).

Secrets the workflow expects (set them in repo settings before the first release build):
`DEBUG_KEYSTORE_BASE64` (optional — AGP falls back to its auto-generated debug key),
`RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.

## Release build
Set `isMinifyEnabled = true` and `isShrinkResources = true` on the release build type. Anything reached by
reflection (WorkManager workers, Room DAOs) needs a keep rule in `proguard-rules.pro` or it gets
stripped/renamed in the release APK only — a class of bug debug builds never show.

## Cloud / in-session builds
Enablement is environment config, not repo: allowlist `dl.google.com`, set `ANDROID_HOME=/opt/android-sdk`,
run `scripts/cloud-setup.sh` as the setup script. It installs the SDK and seeds the wrapper dist from the
pre-installed Gradle. **Only works if the pre-installed Gradle is 9.x** (AGP 9 needs it); an image still on
Gradle 8.x fails locally. CI is unaffected (`setup-gradle` downloads the pinned version) and remains the
authoritative gate. Instrumented tests still need CI's emulator.
