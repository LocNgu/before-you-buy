# Before You Buy

> **TODO:** replace this section with the real product pitch — what the app does, who it's for, and
> the principles behind it.

An Android app. Kotlin · Jetpack Compose · Material 3.

## Status

Pre-scaffold. The repository currently carries shared development infrastructure — agent
instructions, decision records, contributor docs — and no app module yet. Architecture, DI,
persistence and SDK levels are deliberately undecided; see the open-decisions table in
[AGENTS.md](AGENTS.md).

## Features

- TODO

## Build

TODO once the Gradle project exists.

## Repository layout

| Path | What's in it |
|---|---|
| `AGENTS.md` | Tool-agnostic agent instructions: conventions, open decisions, git workflow |
| `.claude/` | Claude-specific layer — `CLAUDE.md`, subagents, path-scoped rules, hooks, run-app skill |
| `.github/` | Issue and PR templates, Dependabot config |
| `docs/decisions/` | Architecture Decision Records (product + technical) |
| `scripts/` | Cloud-session setup script |

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Work flows through issues → `claude/*` or `feature/*`
branches → PRs into `develop`; `develop` → `main` cuts a release.

## License

See [LICENSE](LICENSE) — all rights reserved, source-available for reference only.
