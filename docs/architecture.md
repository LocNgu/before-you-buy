# Architecture (planned)

Status: planned, not yet implemented. The scaffold issue turns this into code; update this file when reality differs.

## Stack

| Concern | Choice | Why |
|---|---|---|
| Language | Kotlin (latest stable 2.x, K2) | Android standard |
| UI | Jetpack Compose + Material 3 | Standard, well documented |
| Navigation | Navigation 3 | Stable since Nov 2025, Compose-first |
| DI | Hilt | Most documented option; works with `hiltViewModel()` |
| Persistence | Room (via KSP) | Relational data (items, allocations, decisions) |
| Settings | DataStore (Preferences) | Small key-value settings |
| Background | WorkManager | Daily check for cooling-off ends and nudges |
| Serialization | kotlinx.serialization | Export/import JSON |
| Time | `java.time` + injectable `Clock` | Native on minSdk 26; testable |
| Build | Gradle Kotlin DSL + version catalog (`gradle/libs.versions.toml`) | |
| Lint/format | Android Lint + ktlint (Spotless or ktlint-gradle) | |
| Tests | JUnit 4, kotlinx-coroutines-test, Turbine, Robolectric, Roborazzi (screenshot tests) | Screenshot tests let Claude sessions verify UI without an emulator |
| SDK levels | minSdk 26, targetSdk/compileSdk 36 (or newer stable) | Play requires target 36 since Aug 2026 |

All dependencies must be under licences compatible with a commercially distributed, non-open-source app (Apache-2.0, MIT, BSD…). No GPL/AGPL libraries. No analytics/crash SDKs, no ads SDKs.

## Modules

```
:app            Android app: activities, Compose screens (package-by-feature), ViewModels, DI wiring, WorkManager workers, notifications
:core:domain    Pure Kotlin/JVM (no Android deps): models, lifecycle stage derivation, cooling-off rules, money math, allowance accrual, opportunity cost, share-text parsing
:core:data      Room database, DAOs, DataStore, repositories, export/import
```

Rules:

- **All business rules live in `:core:domain`** and are covered by fast JVM unit tests. ViewModels orchestrate; they do not compute rules.
- Domain functions take a `Clock`/`Instant` parameter — never call `Instant.now()` directly.
- Money is a value class over `Long` minor units plus a currency code; never `Double`/`Float`.
- The lifecycle **stage is derived, never stored** (see product brief §5). Stored: status (`ACTIVE`, `PURCHASED`, `REJECTED`), timestamps, answers, allocations.
- Room schema is exported (`room.schemaLocation`) and every schema change ships with a migration + migration test from the first public release on.

## Package layout in `:app` (package-by-feature)

```
app/src/main/kotlin/<applicationId>/
  ui/theme/          theme, typography, shared components
  feature/home/
  feature/capture/   quick add + share receiver
  feature/wishlist/  list + item detail
  feature/reflection/
  feature/decision/
  feature/history/
  feature/money/     allowance, allocations
  feature/priorities/
  feature/settings/
  notifications/     workers, notification builders, action receivers
```

## Hard constraints (checked in CI)

- The merged release manifest must **not** contain `android.permission.INTERNET` (until an explicit decision adds opt-in link previews).
- No user-facing strings hard-coded in Kotlin — all in `strings.xml` with `values/` (English) and `values-de/` (German).
- Notification and UI copy follow the tone rules in `docs/product-brief.md` §8 and §6.6.

## Placeholder applicationId

Until the final ID is chosen (open decision), use `dev.placeholder.beforeyoubuy` as `applicationId` and `namespace`. Keep it in one place (`app/build.gradle.kts`) so it can be changed once before the first Play upload.
