@AGENTS.md

# Claude Code

Everything above is tool-agnostic and lives in `AGENTS.md`. This section is Claude-specific.

## Development workflow

**Issue-first (always):** on any feature request or bug report, create a GitHub issue via
`mcp__github__issue_write`, share the link, and wait for explicit go-ahead before writing any code,
branch, or PR.

**Spec the issue in this session, not in a subagent.** Resolve ambiguities by interviewing the human
one question at a time — multiple choice where possible, confirm each answer before moving on — then
post a single `## Spec clarifications` comment recording out-of-scope items, edge cases, and
decisions. A subagent can't hold that conversation; it runs in isolation and returns a summary.
Skip the interview when the issue has no ambiguities, and say so on the issue for the paper trail.
If the scope spans 3+ independently shippable layers, propose a sub-task split in dependency order
and let the human decide before implementing.

1. **Implement** (`implementer` subagent) — writes code, pushes a `claude/*` branch, returns the PR
   title/body as text; **this session** opens the PR targeting `develop` (pre-authorized).
2. **Review** (`reviewer` subagent, read-only — can't post) — findings tagged **BLOCKING** /
   **NON-BLOCKING (SMALL|LARGE)**, plus an acceptance-criteria pass. This session posts them. Each
   round is a fresh standalone review; max 2 rounds, then wait for the human. Self-review must use
   `event: COMMENT` (APPROVE/REQUEST_CHANGES are blocked for the same account). For NON-BLOCKING,
   ask the human (recommend in-PR fix for SMALL, new issue for LARGE) before acting.
3. **Update docs** — implementer updates `AGENTS.md` / this file and `CHANGELOG.md` `[Unreleased]`
   in the feature PR, before merge (`chore:`/docs-only PRs may omit the CHANGELOG entry).
4. **Merge** — **human only**; Claude never merges.

Review is never skipped, including for one-line changes: CI passing is not a review pass. An
unpinned dependency compiles green and is still a BLOCKING finding. Don't add "double-check your
work" steps beyond this — verification lives in CI and the review round, not in duplicated passes.

**Auto-review on green CI:** after opening the PR, `subscribe_pr_activity`; when new commits land
**and** that PR's CI is green, launch the next reviewer round (capped at 2). If CI is red, diagnose
and re-kick rather than reviewing.

**Comment cadence** — one comment per phase, in order: spec → issue (`add_issue_comment`); each
review round → PR inline review (`pull_request_review_write` + `add_comment_to_pending_review`);
summary → PR.

## Subagents

Two, both justified by context isolation plus a restricted tool set:

- `implementer` — the only agent that writes files. Returns PR text; cannot post to GitHub.
- `reviewer` — read-only. Reviews the diff and validates acceptance criteria in one pass.

Neither pins a `model:`, so both inherit the session model and `/model` stays a single lever. Add a
third subagent only when a task genuinely needs its own context window — not to make the pipeline
look thorough.

**`mcp__github__*` writes are this session's job.** Subagents return text; the orchestrator posts.

## Permissions

`.claude/settings.json` carries `permissions` (client-enforced, matched on command strings) and a
`PreToolUse` hook at `.claude/hooks/guard-git.sh` for the invariants that must hold regardless of
string form: no pushing to `main`/`develop`, no force-push to a protected branch, no
`git reset --hard`, no merging PRs by any route.

| Action | Permission |
|---|---|
| Read files · read-only git · `add`/`commit`/`stash`/`cherry-pick` · checkout/push `claude/*` · `./gradlew *` | Allowed, no prompt |
| `git checkout develop` · `git push --force origin claude/*` | Prompts — approve when appropriate |
| Push to `main`/`develop` · force-push protected · `git reset --hard` · merging a PR | **Blocked by hook** |

## Pointers

Path-scoped rules load only when you touch matching files.

- `.claude/rules/ci-build.md` — Android toolchain traps and CI design, for when the app module lands
- `.claude/rules/release.md` — release-cutting workflow
- `.claude/skills/run-app/` — build/install/drive the app on an emulator via adb
- Feature history → `CHANGELOG.md` + `docs/decisions/` + `git log`. Not duplicated here.

Keep this file and `AGENTS.md` each under ~200 lines. When an area grows its own non-obvious rules,
add a path-scoped file under `.claude/rules/` and link it here rather than growing either file.
