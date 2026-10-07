#!/usr/bin/env bash
# PreToolUse hook for Bash: enforces the git rules in CLAUDE.md (ADR-0020).
#
# Permission globs in .claude/settings.json match the raw command text, so
# equivalent spellings slip past them (`git -C . reset --hard`, `git push origin
# HEAD:main`, a bare `git push` while on main). This hook parses the command
# first, so these hold however the call is phrased:
#
#   - never push to main (incl. force-push, delete, --all/--mirror)
#   - never `git reset --hard`
#   - never merge a pull request (only the owner merges)
#
# It errs toward blocking: a command that merely mentions a forbidden push in a
# nested string may be denied too. Tests: .claude/hooks/test-guard-git.sh
set -uo pipefail

PROTECTED="main"

input="$(cat)"
command -v jq >/dev/null 2>&1 || exit 0 # degrade to permissive rather than block every call
cmd="$(jq -r '.tool_input.command // empty' <<<"$input")"
cwd="$(jq -r '.cwd // empty' <<<"$input")"
[ -n "$cmd" ] || exit 0
cwd="${cwd:-${CLAUDE_PROJECT_DIR:-$PWD}}"

deny() {
  jq -n --arg reason "$1" '{hookSpecificOutput: {hookEventName: "PreToolUse",
    permissionDecision: "deny", permissionDecisionReason: $reason}}'
  exit 0
}

current_branch() { git -C "$1" branch --show-current 2>/dev/null; }

is_protected_ref() {
  # main, +main, refs/heads/main, HEAD:main, :main (delete), x:refs/heads/main
  [[ "$1" =~ ^\+?([^:]*:)?(refs/heads/)?${PROTECTED}$ ]]
}

check_segment() {
  local -a t
  read -r -a t <<<"$1"
  local n=${#t[@]} i=0 dir="$cwd"

  # gh pr merge / gh api .../pulls/N/merge
  if [ "$n" -ge 3 ] && [ "${t[0]}" = "gh" ] && [ "${t[1]}" = "pr" ] && [ "${t[2]}" = "merge" ]; then
    deny "Merging pull requests is owner-only (CLAUDE.md, ADR-0020)."
  fi
  if [ "$n" -ge 2 ] && [ "${t[0]}" = "gh" ] && [ "${t[1]}" = "api" ] && [[ "$1" =~ pulls/[0-9]+/merge ]]; then
    deny "Merging pull requests is owner-only (CLAUDE.md, ADR-0020)."
  fi

  # Find `git`, skipping env assignments like FOO=bar.
  while [ "$i" -lt "$n" ] && [[ "${t[$i]}" == *=* ]]; do i=$((i + 1)); done
  [ "$i" -lt "$n" ] && [ "${t[$i]}" = "git" ] || return 0
  i=$((i + 1))

  # Skip git's global options.
  while [ "$i" -lt "$n" ]; do
    case "${t[$i]}" in
      -C) dir="${t[$((i + 1))]:-$dir}"; [[ "$dir" = /* ]] || dir="$cwd/$dir"; i=$((i + 2)) ;;
      -c) i=$((i + 2)) ;;
      --git-dir=* | --work-tree=* | --no-pager | -P | --bare) i=$((i + 1)) ;;
      *) break ;;
    esac
  done
  local sub="${t[$i]:-}"
  i=$((i + 1))

  case "$sub" in
    reset)
      for ((; i < n; i++)); do
        [ "${t[$i]}" = "--hard" ] &&
          deny "git reset --hard is forbidden (CLAUDE.md): it discards work irrecoverably. Use git restore, git stash or git revert."
      done
      ;;
    push)
      local -a pos=()
      for ((; i < n; i++)); do
        case "${t[$i]}" in
          --all | --mirror) deny "git push ${t[$i]} would push $PROTECTED; push a claude/* branch instead." ;;
          -o | --push-option | --repo | --receive-pack | --exec) i=$((i + 1)) ;;
          -*) ;;
          *) pos+=("${t[$i]}") ;;
        esac
      done
      local ref
      for ref in "${pos[@]:1}"; do
        is_protected_ref "$ref" &&
          deny "Pushing to $PROTECTED is forbidden (CLAUDE.md, ADR-0020). Push a claude/* branch and open a PR."
      done
      # No refspec: git pushes the current branch.
      if [ "${#pos[@]}" -le 1 ] && [ "$(current_branch "$dir")" = "$PROTECTED" ]; then
        deny "You are on $PROTECTED; a bare git push would push it (CLAUDE.md, ADR-0020). Create a claude/* branch first."
      fi
      # HEAD while on main.
      for ref in "${pos[@]:1}"; do
        if [[ "$ref" =~ ^\+?HEAD$ ]] && [ "$(current_branch "$dir")" = "$PROTECTED" ]; then
          deny "You are on $PROTECTED; pushing HEAD would push it (CLAUDE.md, ADR-0020)."
        fi
      done
      ;;
  esac
}

# Split into simple commands on ; && || | and newlines; strip quotes and
# leading `cd x`-style prefixes are handled by splitting on &&.
flat="$(printf '%s' "$cmd" | tr '\n' ';' | sed -E "s/(\&\&|\|\||;|\||\&)/\n/g" | tr -d "\"'")"
while IFS= read -r segment; do
  check_segment "$segment"
done <<<"$flat"

# Fallback for nested forms (bash -c "...", subshells) the splitter can't see into.
if printf '%s' "$cmd" | grep -qE "git([[:space:]]+-C[[:space:]]+[^[:space:]]+)?[[:space:]]+push[^;&|]*[[:space:]:+/]${PROTECTED}([[:space:]\"')]|$)"; then
  deny "Pushing to $PROTECTED is forbidden (CLAUDE.md, ADR-0020)."
fi
exit 0
