# ADR-0020: main only; one issue per PR; one session implements; human merges

**Status**: accepted
**Type**: technical
**Date**: 2026-10-05

## Context

Alternatives considered: a `develop` + `main` model with release branches, and a multi-agent pipeline (separate spec, implementer, reviewer and QA agents). The owner judged the pipeline to add a lot of overhead without proven benefit, and preferred `main` only for simplicity.

## Decision

- All PRs target `main`. Releases are tags on `main`.
- One issue per branch/PR (`claude/<kebab-name>`); never mix unrelated work.
- One Claude session implements an issue end to end: read the issue and relevant ADRs/rules, implement, run the verify task, self-review the diff (e.g. with the built-in `/code-review`), open the PR.
- Only the owner merges.

## Consequences

- Less ceremony and cost per issue. If quality problems show up, add review steps deliberately (with a new ADR), ideally measured.
