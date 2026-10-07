# Before You Buy (working name)

Offline Android app that makes people pause, reflect, prioritize and save before buying. Local only — no account, no server, no analytics, no `INTERNET` permission.

> **Keep this file lean — it loads into every session.** Only repo-wide rules, one or two lines each. Area-specific detail goes in a path-scoped `.claude/rules/<area>.md` (with `paths:` frontmatter, so it loads only when matching files are touched). Decisions go in `docs/decisions/`. History goes in `CHANGELOG.md` and `git log`, never here.

## Where things are

- `docs/product-brief.md` — the product: concepts, lifecycle, screens, tone. Read the sections your issue cites, not the whole file.
- `docs/decisions/` — ADRs. **Scan the index before working in an area.** If a request contradicts an ADR, name it and ask the owner before proceeding; changed decisions get a new superseding ADR (rules in `docs/decisions/README.md`).
- `docs/architecture.md` — stack, modules, pitfalls.
- `docs/research.md` — evidence behind product decisions (only when a design question comes up).

## Workflow (ADR-0020)

- One issue → one `claude/<kebab-name>` branch → one PR to `main`. Never mix unrelated work. Only the owner merges.
- `.claude/hooks/guard-git.sh` blocks pushing to `main`, `git reset --hard` and merging PRs, whatever the phrasing. Don't work around it; change it only with its tests (`.claude/hooks/test-guard-git.sh`).
- Implement only the issue's scope; meet every acceptance criterion or explain why in the PR.
- Before opening/updating a PR: run the verify task (see `docs/architecture.md`; keep the exact command there, nowhere else), then review your own diff (e.g. `/code-review`).
- After two failed attempts to fix the same test, stop changing it: find the real cause (framework docs, a minimal repro) and check whether the test asserts structure instead of behaviour.
- In the same PR: update `CHANGELOG.md` `[Unreleased]` for user-visible changes, add/update the `.claude/rules/<area>.md` for the area you built, and add an ADR for any significant new decision.

## Product non-negotiables

- Never encourage spending: no "money available", deals, price alerts, suggestions or urgency (ADR-0014).
- No shaming or celebration of not buying (ADR-0012). Reject/"Let it go" is always as easy as Buy.
- While cooling off: reasons first, image de-emphasized, no prominent "open in shop" (ADR-0013).

## Code non-negotiables

- Business rules live in `:core:domain` with JVM tests — not in ViewModels or Composables.
- Money is `Long` minor units; time comes from an injected `Clock`; days are calendar days (ADR-0018).
- No `INTERNET` permission (ADR-0003). Only Apache-2.0/MIT/BSD dependencies (ADR-0004).
- Every user-facing string in `values/` **and** `values-de/` (ADR-0016).
- Room: explicit migrations only, schemas committed (ADR-0019).
- The project is **source available, not open source** — never call it open source.
