# Contributing to Before You Buy

Thanks for your interest in this project. This guide covers how to get set up and
what we expect from contributions.

For a deeper tour of the architecture and conventions, see the
[README](README.md) and [`.claude/CLAUDE.md`](.claude/CLAUDE.md) (the latter is
aimed at AI agents but is the most detailed reference for how the codebase is
organized).

## Reporting bugs & requesting features

Open a **GitHub Issue**:

- **Bugs** — include steps to reproduce, what you expected, what happened, and
  your device / Android version.
- **Features** — describe the problem you're trying to solve and the acceptance
  criteria you'd consider "done".

Please search existing issues first to avoid duplicates.

## Development setup

- **Android Studio** (latest stable) or the command-line Gradle wrapper.
- **JDK 17** and the **Android SDK** (`platforms;android-37`,
  `build-tools;35.0.0`).
- Clone the repo and let Gradle sync; the build uses the Gradle wrapper
  (`./gradlew`), so no separate Gradle install is required.

## Branching & pull requests

- **Base every branch off the latest `develop`**, not `main`:
  ```
  git fetch origin develop
  git checkout -b feature/<short-description> origin/develop
  ```
  Use a descriptive prefix such as `feature/` or `fix/`. (Automated agent
  branches use the `claude/` prefix — see `.claude/CLAUDE.md`.)
- **One change per branch/PR** — don't mix unrelated changes.
- **Open pull requests against `develop`**, never `main`. `main` is the release
  branch; releases flow `develop` → `main`.
- Update `CHANGELOG.md` under the `## [Unreleased]` section for any user-visible
  change (`chore:`/docs-only PRs may omit it). Format follows
  [Keep a Changelog](https://keepachangelog.com/).
- Keep commit messages clear and imperative (e.g. `fix: …`, `feat: …`,
  `docs: …`, `chore: …`).

## Code style

- Static analysis runs on every PR via **Detekt** (with `detekt-formatting` /
  ktlint rules); the config lives in `config/detekt/`. New violations fail CI.
- Run it locally before pushing:
  ```
  ./gradlew detekt
  ```
  Formatting issues can be auto-fixed by enabling `autoCorrect` on the Detekt
  tasks locally; CI only reports, it never auto-corrects.
- All user-facing UI strings must live in `res/values/strings.xml` — no
  hardcoded strings in Compose.

## Architecture decisions

Significant product and technical decisions are recorded as ADRs under
[`docs/decisions/`](docs/decisions/README.md). Read the relevant one before
changing an area it covers; add a new one when your PR makes a decision that
future contributors would otherwise have to re-derive.

## Running tests locally

```
./gradlew testDebugUnitTest   # JVM unit tests
./gradlew lintDebug           # Android lint
./gradlew detekt              # static analysis
```

Instrumented (androidTest) tests run on CI's emulator; they can also be run
locally against a connected device/emulator with
`./gradlew connectedDebugAndroidTest`.

## License

By contributing, you agree that your contributions are licensed under the same
license as this project.
