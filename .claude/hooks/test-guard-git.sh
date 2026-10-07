#!/usr/bin/env bash
# Tests for guard-git.sh. Run: .claude/hooks/test-guard-git.sh
set -uo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
hook="$here/guard-git.sh"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

git -C "$tmp" init -q -b main && git -C "$tmp" -c user.email=t@t -c user.name=t commit -q --allow-empty -m init
mkdir -p "$tmp/feature" && git -C "$tmp" worktree add -q -b claude/x "$tmp/feature" 2>/dev/null

fail=0
run() { # expect cwd command
  local out
  out="$(jq -n --arg c "$3" --arg d "$2" '{tool_input: {command: $c}, cwd: $d}' | "$hook")"
  local got=allow
  [[ "$out" == *'"deny"'* ]] && got=deny
  if [ "$got" != "$1" ]; then
    echo "FAIL ($got, expected $1): $3   [cwd ${2#"$tmp"}]"
    fail=1
  fi
}

M="$tmp"; F="$tmp/feature"

# Blocked
run deny "$F" "git push origin main"
run deny "$F" "git push -u origin main"
run deny "$F" "git push --force origin main"
run deny "$F" "git push -f origin main"
run deny "$F" "git push --force-with-lease origin main"
run deny "$F" "git push origin +main"
run deny "$F" "git push origin HEAD:main"
run deny "$F" "git push origin claude/x:refs/heads/main"
run deny "$F" "git push origin :main"
run deny "$F" "git push --delete origin main"
run deny "$F" "git -C . push origin main"
run deny "$F" "git -c push.default=current push origin main"
run deny "$F" "cd /tmp && git push origin main"
run deny "$F" "git push --all origin"
run deny "$F" "git push --mirror origin"
run deny "$M" "git push"
run deny "$M" "git push origin"
run deny "$M" "git push -u origin HEAD"
run deny "$F" "git -C $M push"
run deny "$F" "git reset --hard"
run deny "$F" "git reset --hard origin/main"
run deny "$F" "git -C . reset --hard HEAD~1"
run deny "$F" "gh pr merge 44 --squash"
run deny "$F" "gh api -X PUT repos/o/r/pulls/44/merge"
run deny "$F" "bash -c 'git push origin main'"

# Allowed
run allow "$F" "git push -u origin claude/x"
run allow "$F" "git push origin claude/x"
run allow "$F" "git push --force-with-lease origin claude/x"
run allow "$F" "git push"
run allow "$F" "git push -u origin HEAD"
run allow "$F" "git push origin claude/maintenance"
run allow "$F" "git push origin claude/x:claude/main-menu"
run allow "$F" "git reset --soft HEAD~1"
run allow "$F" "git fetch origin main"
run allow "$F" "git checkout -b claude/y origin/main"
run allow "$F" "git merge origin/main"
run allow "$F" "git log --oneline main..HEAD"
run allow "$F" "git commit -m 'never push to main'"
run allow "$F" "gh pr view 44"
run allow "$F" "ls -la"

[ "$fail" -eq 0 ] && echo "all guard-git tests passed"
exit "$fail"
