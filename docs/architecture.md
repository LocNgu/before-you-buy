# Architecture

Status: scaffolded (#8): modules, build, DI, navigation and tests exist. Domain rules (#11, #12) and persistence (#13) exist; screens don't yet. Update this file when reality differs. Decisions behind it: ADR-0001, ADR-0003, ADR-0017–ADR-0021. Build conventions: `.claude/rules/build.md`.

## Verify task — the one pre-push gate

```bash
./gradlew verify
```

`verify` is an aggregate Gradle task defined in the root `build.gradle.kts`. It runs exactly what CI runs: formatting check (`spotlessCheck`), Android Lint for every module (warnings are errors), unit tests (incl. Robolectric), Roborazzi screenshot verification, the hard-constraint checks below (`:app:checkNoInternetPermission`, `checkStringParity`) and the tests of `build-logic`. CI (`.github/workflows/ci.yml`) runs it plus `assembleRelease`. **This is the only place the command is written down** — CI, `CLAUDE.md` and the PR template refer to it. While iterating, run narrower tasks (`:core:domain:test`, `--tests "…"`) with `-q`; they never replace `verify` before a push.

## Stack (ADR-0017)

| Concern | Choice |
|---|---|
| Language / UI | Kotlin (K2, via AGP 9's built-in Kotlin), Jetpack Compose, Material 3 |
| Navigation | Navigation 3 |
| DI | Hilt |
| Persistence | Room (KSP) + DataStore Preferences |
| Background | WorkManager |
| Serialization | kotlinx.serialization (export/import) |
| Time | `java.time` + injected `Clock` (ADR-0018) |
| Build | Gradle 9 (wrapper) + AGP 9, Kotlin DSL, version catalog `gradle/libs.versions.toml` |
| Format/lint | Spotless + ktlint, Android Lint |
| Tests | JUnit 4, kotlinx-coroutines-test, Turbine, Robolectric, Roborazzi |
| SDK levels | minSdk 26, targetSdk/compileSdk 37 (Play requires at least target 36 since Aug 2026; current AndroidX needs compileSdk 37) |
| Bytecode | Java/Kotlin target 17; builds run on JDK 21 |

Dependencies: Apache-2.0/MIT/BSD only in what ships (test and build tools exempt, ADR-0023); no analytics, crash-reporting or ads SDKs (ADR-0002, ADR-0004).

## Modules

```
:app            Activities, Compose screens (package-by-feature), ViewModels, Hilt wiring, workers, notifications
:core:domain    Pure Kotlin/JVM: models, lifecycle stage, cooling-off, money math, allowance, opportunity cost, share-text parsing
:core:data      Room, DAOs, DataStore, repositories (+ their Hilt module), export/import
```

- **All business rules live in `:core:domain`** with JVM unit tests. ViewModels orchestrate; Composables render.
- The lifecycle **stage is derived, never stored** (ADR-0007).
- Repository interfaces in `:core:domain`, implementations in `:core:data`.

## Package layout in `:app`

```
ui/theme/  ui/components/  ui/format/  feature/{home,capture,wishlist,reflection,decision,history,money,priorities,settings}/  notifications/
navigation/  (Navigation 3 keys and the NavDisplay)   di/  (Hilt modules, e.g. the Clock)
```

`ui/theme/` is the fixed calm theme (ADR-0028), `ui/components/` the shared components, and `ui/format/` the locale-aware text helpers (current locale, dates, day plurals). Money formatting and parsing live in `:core:domain` `money/` (ADR-0027). Conventions: `.claude/rules/ui.md`.

`build-logic/` is not a module but an included build with the project's own Gradle check plugins (ADR-0025).

## Persistence (`:core:data`, ADR-0019, ADR-0032)

- Room, schema version 1: `items` (every stored `Item` fact), `reflections` (1:1 with the item; `reasons_against` is a JSON array of strings, not a child table), `decisions` (append-only history). Children reference `items(id)` with `ON DELETE CASCADE`. No stage column. M2 adds `allocations` and `pool_entries`.
- Schema JSON goes to `core/data/schemas/` and is committed (ADR-0019).
- `ItemRepository` and `SettingsRepository` (interfaces in `:core:domain`) are bound by `:core:data`'s own Hilt module `DataModule`; the implementations stay `internal`. `:app` just injects the interfaces.
- Item writes are compare-and-set: `update(current, edited)` and `recordDecision(current, decision, result)` write only if the stored row still equals what the caller read, and return false otherwise. `recordDecision` writes the decision and the item's new state in one transaction; `update` never changes the status.
- Settings (DataStore Preferences) store only what the user set; defaults (currency from the device region, else EUR; default tiers in that currency) are computed on read. The first saved setting also stores the currency.
- Conventions and tests: `.claude/rules/data.md`.

## Pitfalls (common Android traps — apply from day one)

- **"Today" changes at midnight.** Any `Flow`/`combine` whose output depends on today's date (countdowns, "Ready" lists) needs a day-change signal as an input; nothing else re-emits at midnight. Inject it so tests can drive it — never wait on a real-time ticker in tests (it hangs).
- **Calendar days, not milliseconds.** Add days with `LocalDate`/`ZonedDateTime`; `+ 86_400_000` breaks on daylight-saving days (ADR-0018).
- **Enums in Room are strings, read defensively** (`runCatching { valueOf(it) }.getOrDefault(fallback)`); keep retired enum constants so old rows/backups still load (ADR-0019).
- **Migrations are explicit** and tested; never `fallbackToDestructiveMigration()` (ADR-0019).
- **Robolectric gets a test Application** whose app-start work is a no-op, so start-up coroutines don't race test fixtures. Put app-start work in one overridable method and test it directly.
- **BroadcastReceivers** (notification actions) delegate to an `internal suspend fun` outside `goAsync()` so the logic is unit-testable.
- **Images:** don't delete a photo file eagerly when an item changes; a periodic sweep removes files no row references. Photo Picker URIs are copied into app storage (no persistable permission needed afterwards).
- **UI tests assert what the user perceives** (text, content descriptions, state descriptions, actions), never tree structure (child counts, testTag topology).
- Collect UI state with `collectAsStateWithLifecycle()`; `StateFlow` for state, a channel/`SharedFlow` for one-shot events.

## Hard constraints (checked by `verify` and CI)

Implemented as Gradle plugins in the `build-logic` included build (ADR-0025, `.claude/rules/build.md`).

- The merged release manifest must **not** contain `android.permission.INTERNET` (ADR-0003): `:app:checkNoInternetPermission`.
- `values/` and `values-de/` have the same string, plural and string-array names in every module (ADR-0016): `checkStringParity`.
- No user-facing strings hard-coded in Kotlin.

## Cloud build environment (ADR-0021)

- Android SDK: installed by `scripts/install-android-sdk.sh`, called from the cloud environment's setup script (cached).
- `local.properties`, `ANDROID_HOME` and the SDK presence check: `scripts/session-start.sh`, a SessionStart hook (cloud only).
- Network allowlist: `dl.google.com`, `downloads.gradle.org`, `release-assets.githubusercontent.com` (plus the default package managers).
- Details, gotchas and diagnosis: `.claude/rules/ci-build.md`.

## applicationId (ADR-0026)

`applicationId` and `namespace` are `io.github.locngu.beforeyoubuy`, defined in one place: `app.id` in `gradle.properties` (`:core:data` appends `.core.data`). Kotlin packages use the same name. It may still change to an owned domain until the first Play upload; after that it is permanent. Android treats a changed ID as a different app, so a change needs export/import (#27) first (ADR-0026).

## Releases (ADR-0029, ADR-0030)

A release `vX.Y.Z` of `main` publishes a signed APK on GitHub Releases (`.github/workflows/release.yml`), started by its **Run workflow** button (which tags after a successful build) or by pushing a tag. The tag is the only source of `versionName`/`versionCode`; release signing comes from environment variables and is never committed. Owner steps (key, secrets, starting a release, installing): `docs/releasing.md`. Play (AAB, tracks) is #35.

## Not set up yet

- **Backup:** `allowBackup="false"` and backup rules that exclude everything, until export/import decides what Android backup includes (ADR-0002, product brief §backup).
- **In-app language picker:** the per-app language works through Android 13+ system settings (`res/xml/locales_config.xml`). A picker in Settings (and per-app language on Android 8–12 via AppCompat) comes with the Settings screen.
