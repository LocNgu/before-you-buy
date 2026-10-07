# Architecture

Status: planned, not yet implemented. The scaffold issue (#8) turns this into code; update this file when reality differs. Decisions behind it: ADR-0001, ADR-0003, ADR-0017–ADR-0021.

## Verify task — the one pre-push gate

```bash
./gradlew verify
```

`verify` is an aggregate Gradle task defined in the root build (issue #8). It runs exactly what CI runs: formatting check, Android Lint, unit tests (incl. Robolectric and Roborazzi verification), the no-`INTERNET` manifest check and the string-parity check. **This is the only place the command is written down** — CI, `CLAUDE.md` and the PR template refer to it. While iterating, run narrower tasks (`:core:domain:test`, `--tests "…"`) with `-q`; they never replace `verify` before a push.

## Stack (ADR-0017)

| Concern | Choice |
|---|---|
| Language / UI | Kotlin (K2), Jetpack Compose, Material 3 |
| Navigation | Navigation 3 |
| DI | Hilt |
| Persistence | Room (KSP) + DataStore Preferences |
| Background | WorkManager |
| Serialization | kotlinx.serialization (export/import) |
| Time | `java.time` + injected `Clock` (ADR-0018) |
| Build | Gradle Kotlin DSL + version catalog `gradle/libs.versions.toml` |
| Format/lint | Spotless + ktlint, Android Lint |
| Tests | JUnit 4, kotlinx-coroutines-test, Turbine, Robolectric, Roborazzi |
| SDK levels | minSdk 26, targetSdk/compileSdk 36 or newer stable (Play requires target 36 since Aug 2026) |

Dependencies: Apache-2.0/MIT/BSD only; no analytics, crash-reporting or ads SDKs (ADR-0002, ADR-0004).

## Modules

```
:app            Activities, Compose screens (package-by-feature), ViewModels, Hilt wiring, workers, notifications
:core:domain    Pure Kotlin/JVM: models, lifecycle stage, cooling-off, money math, allowance, opportunity cost, share-text parsing
:core:data      Room, DAOs, DataStore, repositories, export/import
```

- **All business rules live in `:core:domain`** with JVM unit tests. ViewModels orchestrate; Composables render.
- The lifecycle **stage is derived, never stored** (ADR-0007).
- Repository interfaces in `:core:domain`, implementations in `:core:data`.

## Package layout in `:app`

```
ui/theme/  feature/{home,capture,wishlist,reflection,decision,history,money,priorities,settings}/  notifications/
```

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

- The merged release manifest must **not** contain `android.permission.INTERNET` (ADR-0003).
- `values/strings.xml` and `values-de/strings.xml` have the same keys (ADR-0016).
- No user-facing strings hard-coded in Kotlin.

## Cloud build environment (ADR-0021)

- Android SDK: installed by `scripts/install-android-sdk.sh`, called from the cloud environment's setup script (cached).
- `local.properties` + SDK presence check: SessionStart hook (cloud only).
- Network allowlist: `dl.google.com`, `downloads.gradle.org`, `release-assets.githubusercontent.com` (plus the default package managers). Setup steps: issue #7.

## Placeholder applicationId

Until the final ID is chosen (#33), use `dev.placeholder.beforeyoubuy` as `applicationId` and `namespace`, defined in one place in `app/build.gradle.kts`.
