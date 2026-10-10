# ADR-0024: Branch names carry the issue number

**Status**: accepted
**Type**: technical
**Date**: 2026-10-10

## Context

ADR-0020 named branches `claude/<kebab-name>`. `CLAUDE.md` later set the more specific `claude/<N>-<kebab-title>` and added that a session's assigned `claude/*` branch takes precedence, so the two disagreed. Cloud sessions are often handed a generated `claude/*` branch name up front, and renaming it would fight the environment.

## Decision

Amends ADR-0020's branch-name clause:

- If the session has an assigned `claude/*` branch, use it.
- Otherwise name the branch `claude/<N>-<kebab-title>`, where `N` is the issue number (e.g. `claude/42-add-cooling-off-timer`).

Branches are always cut from freshly fetched `origin/main`, one issue per branch, as in ADR-0020.

## Consequences

- The issue a branch belongs to is visible from its name, without opening the PR.
- Assigned session branches don't show the issue number; the PR's `Closes #N` / `Part of #N` links it instead.
- The convention is not enforced by `.claude/hooks/guard-git.sh`, which only blocks pushes to `main`.
