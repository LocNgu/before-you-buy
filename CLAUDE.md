# Before You Buy (working name)

Offline Android app that makes people pause, reflect, prioritize and save before buying. Local only — no account, no server, no analytics, no `INTERNET` permission.

> **Keep this file lean — it loads into every session.** Only repo-wide rules, one or two lines each. Area-specific detail goes in a path-scoped `.claude/rules/<area>.md` (with `paths:` frontmatter, so it loads only when matching files are touched). Decisions go in `docs/decisions/`. History goes in `CHANGELOG.md` and `git log`, never here.

## Where things are

- `docs/product-brief.md` — the product: concepts, lifecycle, screens, tone. Read the sections your issue cites, not the whole file.
- `docs/decisions/` — ADRs. **Scan the index before working in an area.** If a request contradicts an ADR, name it and ask the owner before proceeding; changed decisions get a new superseding ADR (rules in `docs/decisions/README.md`).
- `docs/architecture.md` — stack, modules, pitfalls.
- `docs/research.md` — evidence behind product decisions (only when a design question comes up).

## "Work on #N" — the default procedure (ADR-0020)

A request like "work on #N" or "do #N" means this whole procedure. Don't wait to be told the steps; stop only where it says so.

1. **Read** the issue, its comments and its parent epic (GitHub MCP tools; `gh` isn't available in cloud sessions). Then read the ADRs, product-brief sections and `.claude/rules/` files it cites.
2. **Stop and ask the owner first** if the issue is labelled `decision` or `needs-owner`, is blocked by an open dependency ("Depends on #…"), contradicts an ADR, or leaves a product question open. Batch all the questions into one message. Don't guess.
3. **Branch** from freshly fetched `origin/main`: use the session's assigned `claude/*` branch if there is one, else `claude/<N>-<kebab-title>`. One issue per branch. Never mix unrelated work.
4. **Implement** only the issue's scope. Every acceptance criterion is met or explicitly explained.
5. **In the same PR:** tests for new behaviour, `CHANGELOG.md` `[Unreleased]` for user-visible changes, the `.claude/rules/<area>.md` for the area you built, and an ADR for any significant new decision.
6. **Verify:** run the verify task (the exact command lives only in `docs/architecture.md`). Then review your own diff (`/code-review`) and fix what it finds.
7. **Open the PR** against `main` using `.github/pull_request_template.md`. Include `Closes #N`, or `Part of #N` when something is left for the owner. Map each acceptance criterion to how it was verified, and name any check that couldn't run, with the reason.
8. **Hand over:** reply with the PR link, what changed, and anything the owner must do or decide. Never merge. Only the owner merges.

Guardrails:

- `.claude/hooks/guard-git.sh` blocks pushing to `main`, `git reset --hard` and merging PRs, whatever the phrasing. Don't work around it. Change it only together with its tests (`.claude/hooks/test-guard-git.sh`).
- After two failed attempts to fix the same test, stop changing it. Find the real cause (framework docs, a minimal repro), and check whether the test asserts structure instead of behaviour.
- If the issue turns out to be much larger than one PR, stop and propose a split before going deep.

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
