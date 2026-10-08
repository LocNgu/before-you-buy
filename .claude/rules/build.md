---
description: Gradle build, modules, version catalog and the verify task
paths:
  - "**/*.gradle.kts"
  - "lint.xml"
  - "gradle/**/*"
  - "gradle.properties"
  - ".editorconfig"
  - "**/AndroidManifest.xml"
  - "**/src/test/**/*"
---

# Build (ADR-0017)

## Modules

- `:core:domain` is plain Kotlin/JVM (`kotlin.jvm` + `com.android.lint`). **No Android or AndroidX dependencies**, ever. Business rules and their JVM tests live here.
- `:core:data` is an Android library (Room, DataStore, kotlinx.serialization). It depends on `:core:domain`, never on `:app`.
- `:app` depends on both. Nothing depends on `:app`.
- AGP 9 has **built-in Kotlin**: don't apply `org.jetbrains.kotlin.android` (it fails the build). Apply only `kotlin.compose` / `kotlin.serialization` / `ksp` as needed.
- New module: add it to `settings.gradle.kts`, give it `lint { abortOnError = true; warningsAsErrors = true }`, and add its `lint` and unit-test tasks to `verify` in the root `build.gradle.kts`.

## Version catalog (`gradle/libs.versions.toml`)

- Every version lives in `[versions]`, including SDK levels and `jvmTarget`. Build files never hard-code a version.
- Use latest **stable** releases only (no alpha/beta/rc). A new dependency must be Apache-2.0, MIT or BSD (ADR-0004); write the licence as a comment if it's not Apache-2.0. Check what permissions it merges into the release manifest (ADR-0003).
- Alias names are kebab-case (`androidx-room-runtime`), giving `libs.androidx.room.runtime`. Compose libraries come from the BOM and have no version.
- Changing `compileSdk`: also bump `DEFAULT_COMPILE_SDK` in `scripts/install-android-sdk.sh`, then run that script (see `ci-build.md`).

## Identity

- `applicationId` and the namespace root are the `app.id` property in `gradle.properties`, the only place they're written. Module namespaces append to it (`.core.data`). Kotlin packages are `dev.placeholder.beforeyoubuy…` until issue #33.

## Verify task

- `./gradlew verify` (root `build.gradle.kts`): `spotlessCheck`, every module's `lint`, the debug unit tests and `:app:verifyRoborazziDebug`. Issue #9 adds the no-INTERNET and string-parity checks there.
- Lint treats warnings as errors. The version-update checks (`GradleDependency`, `NewerVersionAvailable`, `AndroidGradlePluginVersion`) are off for every module in the root `lint.xml`, so the result doesn't depend on the network or the date. Fix a lint finding rather than suppressing it; if you must suppress, use `@Suppress`/`tools:ignore` at the narrowest scope with a reason.
- Formatting: Spotless + ktlint, `android_studio` style (`.editorconfig`). `./gradlew spotlessApply` fixes it.

## Tests

- JVM tests for `:core:domain`; Robolectric + Compose UI tests in `:app` under `src/test`. No instrumented tests.
- Robolectric runs with `TestApplication` (`src/test/resources/robolectric.properties`). Its `onAppStart()` is a no-op, so start-up work never races tests. All app-start work goes into `BeforeYouBuyApplication.onAppStart()`.
- WorkManager's start-up initializer is removed in the manifest; it initializes on first use from `BeforeYouBuyApplication.workManagerConfiguration` (where Hilt's worker factory goes later). Don't re-add the initializer.
- Inject `java.time.Clock`; production gets `SystemZoneClock`, which reads the device zone on every call. Tests pass `Clock.fixed(...)` or a fake.
- Compose tests use the `androidx.compose.ui.test.junit4.v2` rules (`createComposeRule`, `createAndroidComposeRule`).
- Screenshots: Roborazzi; goldens are committed in `app/src/test/screenshots/` (the plugin's `outputDir`). Call `captureRoboImage()` without a path so names follow `<package>.<Class>.<test>.png`. Record with `./gradlew :app:recordRoborazziDebug`, then look at the PNGs before committing. `verify` compares; diffs land in `app/build/outputs/roborazzi/`.

## Gotchas

- **Robolectric on SDK 37 + JDK 21** needs `--add-opens=java.base/jdk.internal.access=ALL-UNNAMED` (set in `app/build.gradle.kts`); without it every test fails with "Failed to interact with raw FileDescriptor internals".
- **Espresso** pulled in by Compose UI tests (3.5.x) calls `InputManager.getInstance()`, which API 37 removed. `espresso-core` is pinned to 3.7.0 as a test dependency; don't drop it.
- **Maven Central rate-limits** (HTTP 429) on a cold cache. A first build may fail with "Could not GET … 429"; re-run it, optionally with `--max-workers=2`.
- AGP downloads its default build-tools (36.0.0 for AGP 9.4) on first build if the install script installed a different one. That works (dl.google.com is allowlisted) but isn't in the cached snapshot.
- Configuration cache is on. A plugin that breaks it shows "Configuration cache problems"; fix or report it, don't turn the cache off globally.
