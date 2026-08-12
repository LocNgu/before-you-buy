#!/usr/bin/env bash
# PreToolUse guard for Bash calls.
#
# CLAUDE.md and permission globs are both string-matched, so an equivalent phrasing
# of a forbidden command slips past them (`git -C . reset --hard` vs `git reset --hard`).
# This hook normalizes the command first, so the invariants hold regardless of form:
#
#   - never push to main/develop
#   - never force-push a protected branch
#   - never `git reset --hard`
#   - never merge a PR (human-only)
#
# Blocks with a structured deny so the reason reaches both Claude and the user.
#
# Deliberately errs toward blocking: a command that merely *mentions* a forbidden
# phrase (`echo "never git push origin main"`) is denied too. Writing a real shell
# parser to avoid that would be more code and less safe, and file content should go
# through the Write tool anyway, which this hook does not touch.
set -uo pipefail

input="$(cat)"

if command -v jq >/dev/null 2>&1; then
  cmd="$(jq -r '.tool_input.command // empty' <<<"$input")"
else
  # jq should be present; degrade to permissive rather than blocking every Bash call.
  exit 0
fi

[ -z "$cmd" ] && exit 0

deny() {
  jq -n --arg reason "$1" '{
    hookSpecificOutput: {
      hookEventName: "PreToolUse",
      permissionDecision: "deny",
      permissionDecisionReason: $reason
    }
  }'
  exit 0
}

# Collapse whitespace and strip `git -C <path>` / `-c key=value` so the patterns below
# match however the call was phrased.
norm="$(printf '%s' "$cmd" | tr '\n' ' ' | tr -s ' ')"
norm="$(printf '%s' "$norm" | sed -E 's/git +(-C +[^ ]+|-c +[^ ]+|--git-dir=[^ ]+|--work-tree=[^ ]+) */git /g')"

case "$norm" in
  *"git push"*)
    if printf '%s' "$norm" | grep -qE 'git push([^|;&]*)(--force|--force-with-lease|-f)([^|;&]*)(origin +)?(main|develop)\b'; then
      deny "Force-pushing a protected branch is forbidden (.claude/CLAUDE.md). Push to a claude/* branch instead."
    fi
    if printf '%s' "$norm" | grep -qE 'git push([^|;&]*) (origin +)?(main|develop)\b'; then
      deny "Pushing directly to main/develop is forbidden (.claude/CLAUDE.md). Open a PR from a claude/* branch instead."
    fi
    ;;
esac

if printf '%s' "$norm" | grep -qE 'git reset +([^|;&]* )?--hard'; then
  deny "git reset --hard is forbidden (.claude/CLAUDE.md) — it discards work irrecoverably. Use git restore/stash, or revert a specific commit."
fi

if printf '%s' "$norm" | grep -qE '(gh|hub) +pr +merge|git +merge +(origin/)?(main|develop) +--(no-)?ff.*push'; then
  deny "Merging pull requests is human-only (.claude/CLAUDE.md)."
fi

exit 0
