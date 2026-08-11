## Summary

<!-- One or two sentences: what does this PR do and why? -->

## Linked issue

<!-- Every feature/bug-fix PR needs an issue (issue-first rule). Use "Closes #123". -->
<!-- For release-prep or process/docs PRs with no issue, write "None (docs/release)". -->

Closes #

## Changes

<!-- Bullet list of the notable changes. Keep it to what a reviewer needs. -->

-

## Testing

<!-- How was this verified? New/updated unit tests, Compose tests, manual steps on device/emulator. -->
<!-- If CI is the only verification, say so. -->

-

## Checklist

- [ ] `CHANGELOG.md` `[Unreleased]` updated (not required for `chore:`/docs-only PRs)
- [ ] `.claude/CLAUDE.md` updated if architecture, conventions, or completed features changed
- [ ] All new UI strings live in `strings.xml` (no hardcoded strings in Compose)
- [ ] New dependencies pinned to an exact version in `app/build.gradle.kts`
- [ ] DB schema JSON committed under `app/schemas/` and a `Migration` added if the DB version was bumped
- [ ] New/changed behaviour covered by tests, or explained above why not
- [ ] Relevant ADRs in `docs/decisions/` consulted; new/superseding ADR added if a decision changed
