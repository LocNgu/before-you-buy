# Before You Buy

> **TODO (first real PR):** replace this with a 2–3 sentence description of what the app does and
> the principles behind it (offline-first? accounts? telemetry?). Delete this blockquote when done.

Android app. Kotlin · Jetpack Compose · Material 3.

This file is the tool-agnostic source of truth, readable by any coding agent. `CLAUDE.md` imports it
and adds Claude-specific workflow on top.

## Status: pre-scaffold

There is no `app/` module yet. **The architecture, DI approach, persistence layer, dependency
declaration style and SDK levels are deliberately undecided.** Do not assume them, and do not
import them from a sibling project. When each is chosen, record an ADR and add the resulting
convention below.

## Commands

TODO once the Gradle project exists. Expected shape:

```bash
./gradlew compileDebugKotlin compileDebugUnitTestKotlin compileDebugAndroidTestKotlin   # compile
./gradlew testDebugUnitTest        # unit tests
./gradlew lintDebug                # Android lint
```

Prefer `-q` and grep for failures over dumping full build logs.

## Conventions

These are real traps, each one paid for in a shipped bug on a prior Android/Compose project. They
hold regardless of what architecture this app ends up with.

- **StateFlow** for UI state, **SharedFlow** for one-shot events. Always
  `collectAsStateWithLifecycle()` — never `collectAsState()`.
- **Suspend inside Flow operators** — `List.map {}` takes a non-suspend lambda. To call a `suspend`
  function inside `combine {}` / `map {}`, use a `for` loop with `mutableListOf`.
- **Enums read from storage** — `runCatching { Enum.valueOf(s) }.getOrDefault(fallback)`, never bare
  `.valueOf()`. A renamed enum constant otherwise crashes on old rows.
- **DataStore delegate** — `val Context.settingsDataStore by preferencesDataStore(...)` must sit at
  file top level, never inside a class. Required by the AndroidX API.
- **Dates** — one helper owns all display formatting. Never compute `(now - ts) / 86_400_000` inline;
  calendar-day comparisons go through a `toLocalDate()`-style helper, not raw millisecond math.
- **All user-facing strings in `strings.xml`** — no hardcoded strings in Compose. `cd_back` is the
  canonical back-button content description.
- **Compose tests assert user-visible semantics** (contentDescription / stateDescription / text /
  actionable), **never** tree structure (child counts, testTag topology). A testTag never merges past
  a clickable or merged ancestor. If a fix is about an announcement, assert the announcement.
- **MockK mocks members, not extensions.** `DataStore.edit`, `RoomDatabase.withTransaction` and most
  Flow operators are extension functions — `coEvery { mock.edit(...) }` looks valid and then fails to
  resolve. Mock the underlying member (`DataStore.updateData`) or use a real instance.
- **Pin every dependency to an exact version.** Never a range, never a dynamic `+`.
- **Two-strikes rule** — after two failed attempts at the same test, stop pushing variants. Re-derive
  the mechanism from framework source or a minimal repro, and reconsider whether the test asserts the
  wrong thing (structure vs. contract).

## Decisions to make deliberately

Open choices. Each needs an ADR before the convention is written down as settled — the note is
context, not a verdict.

| Decision | Context |
|---|---|
| Dependency injection | Hilt is Google's documented recommendation. Manual DI via the Application class is viable for a small single-dev app but is a real constraint — choose on purpose. |
| Dependency declaration | `gradle/libs.versions.toml` (version catalog) is the Gradle recommendation and the Android Studio template default. Inlining versions in `app/build.gradle.kts` is the older style. |
| Persistence | Room, DataStore, both, or neither, depending on what the app stores. |
| `minSdk` / `targetSdk` | A device-reach and Play-compliance decision. Check Play's current `targetSdk` requirement rather than copying a number. |
| Dependency bot | Dependabot is native to GitHub. Renovate needs an app install but has first-class version-catalog and `gradle-wrapper` support. |
| Screenshot testing | Roborazzi / Paparazzi are common for Compose; decide before the UI grows. |

## Architecture Decision Records

Decisions live in `docs/decisions/{product,technical}/`. **Consult the relevant ADR before working in
an area it covers; never refactor away a pattern a technical ADR describes without a superseding
decision.** Write a new ADR from `docs/decisions/template.md` when a change makes a decision a future
implementer would otherwise have to re-derive. If a request contradicts an ADR, name it and its
rationale and get human confirmation first. The only permitted edit to a finalized ADR is its Status
line → `superseded by [ADR-XXXX](file.md)`.

## Git workflow

One branch and one PR per change — never mix unrelated work.

```bash
git fetch origin develop && git checkout -b <prefix>/<kebab-desc> origin/develop
```

Branch prefixes: `claude/` for agent work, `feature/` / `fix/` for hand-written. PRs target
`develop`; `develop` → `main` cuts a release. Return to an up-to-date `develop` before starting
anything new.

> **Setup TODO:** `develop` does not exist yet. Create it off `main` and make it the default branch
> before the first feature PR.

Contributor-facing detail lives in `CONTRIBUTING.md`.
