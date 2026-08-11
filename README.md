# Before You Buy

> **TODO:** replace this section with the real product pitch — what the app does, who it's for, and
> the principles behind it.

An Android app. Kotlin · Jetpack Compose · Material 3.

## Status

Early scaffolding. The repository currently carries the shared project infrastructure
(agent pipeline, CI, static analysis, decision records) — the app module itself comes next.

## Features

- TODO

## Tech stack

- Kotlin, Jetpack Compose, Material 3
- MVVM + Repository, manual DI (no Hilt)
- Compose Navigation with a type-safe `Screen` sealed class
- AGP 9.3.1 · Gradle 9.7.0 · Kotlin 2.4.10 · KSP 2.3.11 · compileSdk 37 / targetSdk 35 / minSdk 26

## Build

```bash
./gradlew assembleDebug        # debug APK
./gradlew testDebugUnitTest    # JVM unit tests
./gradlew lintDebug            # Android lint
./gradlew detekt               # static analysis
```

## Repository layout

| Path | What's in it |
|---|---|
| `.claude/` | Agent instructions (`CLAUDE.md`), sub-agents, path-scoped rules, the run-app skill |
| `.github/` | CI/CD workflows, Dependabot config, PR template |
| `config/detekt/` | Detekt static-analysis config |
| `docs/decisions/` | Architecture Decision Records (product + technical) |
| `scripts/` | Cloud-session setup script |

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Work flows through issues → `claude/*` or `feature/*`
branches → PRs into `develop`; `develop` → `main` cuts a release.

## License

See [LICENSE](LICENSE) — all rights reserved, source-available for reference only.
