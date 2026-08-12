---
description: Android toolchain traps and CI design, for when the app module and build files land
paths:
  - "**/*.gradle.kts"
  - "gradle.properties"
  - "gradle/**/*"
  - "version.properties"
  - ".github/**/*"
  - "config/**/*"
  - "scripts/**/*"
---

# Build & CI

**There is no Gradle project in this repo yet**, so nothing here describes current state. It is a
list of traps and design notes from a prior Android project, to spend when the app module is
scaffolded. Do not treat any version below as this project's pinned version — check what the
Android Studio template generates and what Play currently requires, then decide.

## Toolchain traps

- **Kotlin and KSP move together.** KSP2 uses Kotlin-aligned versioning (KSP `2.3.10` = Kotlin
  `2.3.10`). A Kotlin release is not adoptable until KSP ships its match — this is why a grouped
  "bump Kotlin + KSP" bot PR can sit permanently red when only one side has a release.
- **AGP 9 compiles Kotlin itself.** The standalone `org.jetbrains.kotlin.android` plugin must NOT be
  applied — AGP 9 errors if it is present. Compose/serialization/KSP plugins stay.
- `kotlinOptions { jvmTarget }` does not exist under AGP 9. Use top-level
  `kotlin { compilerOptions { jvmTarget.set(JVM_17) } }`.
- **The wrapper distribution and any CI `gradle-version` pin must match** — `setup-gradle` overrides
  the wrapper, so a drift means local and CI compile with different Gradle.
- `android.onlyEnableUnitTestForTheTestedBuildType=false` restores pre-AGP-9 behaviour so
  `testReleaseUnitTest` exists. Only add it if CI actually runs release unit tests: it is global, so
  `./gradlew test` then runs both suites and doubles that task's cost.
- **Pin every dependency to an exact version.** If a version catalog is adopted, pin there instead.
- **Anything reached by reflection needs a keep rule** once R8 is on (`isMinifyEnabled`): WorkManager
  workers, Room DAOs. Stripping only shows up in release builds — a class of bug debug never sees.

## Static analysis

If Detekt is adopted: `buildUponDefaultConfig`, formatting `maxLineLength` 120, and scope
`FunctionNaming` + `MagicNumber` away from `**/ui/**`, `**/test/**`, `**/androidTest/**` — Composables
are intentionally PascalCase and Compose dp/sp literals aren't real magic numbers, but both rules
still catch genuine problems elsewhere. Do not enable both `style.MaxLineLength` and
`formatting.MaximumLineLength`; each over-length line is then flagged twice. Freeze existing debt in
a baseline only when consciously accepting it; run `autoCorrect` locally, never in CI.

## CI design that worked

A `test` job (static analysis + unit tests + lint) gating a `build` job (debug APK) and a `release`
job; instrumented tests behind a path filter with a concurrency group cancelling stacked runs; push
to `main` cutting a signed-APK release. Traps found the hard way:

- **The `secrets` context is not allowed in a step `if:` conditional.** It's a workflow validation
  error that fails the entire run at startup with zero jobs. Map the secret to `env` and check it in
  the shell instead.
- **Dependabot and fork PRs receive no secrets**, so a naive `base64 --decode` of an absent keystore
  secret writes a 0-byte file that `keytool` rejects. Guard on the empty string and fall back to
  AGP's auto-generated debug key.
- `fetch-depth: 0` matters if `versionCode` is derived from `git rev-list --count` — a shallow clone
  silently produces a wrong, lower version code.

## Dependency bots

Dependabot's `gradle` ecosystem **does not update the Gradle wrapper**, so wrapper bumps stay manual
even though the wrapper is part of the toolchain coupling above. Its grouping is also best-effort:
it batches whatever is available in a given run, so a group can still open a PR containing one
stdlib-coupled artifact without its siblings, which red-CIs. Renovate handles both (it has a
`gradle-wrapper` manager and first-class version-catalog support) at the cost of installing a
third-party app. See the open decision in `AGENTS.md`.

## Cloud / in-session builds

Enablement is environment config, not repo config: allowlist `dl.google.com`, set
`ANDROID_HOME=/opt/android-sdk`, run `scripts/cloud-setup.sh` as the setup script. It installs the
SDK and seeds the wrapper distribution from the pre-installed Gradle (the wrapper's own download is
proxy-blocked). **Only works if the pre-installed Gradle matches the major version AGP needs.** CI is
unaffected and remains the authoritative gate; instrumented tests still need CI's emulator.
