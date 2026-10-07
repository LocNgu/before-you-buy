#!/usr/bin/env bash
# SessionStart hook (ADR-0021, issue #7). Cloud sessions only; must stay fast.
#
# Points Gradle at the Android SDK installed by scripts/install-android-sdk.sh
# (via the cloud environment's setup script) and exports ANDROID_HOME for the
# session. Prints one line if the SDK is missing; that output reaches Claude.

[ "${CLAUDE_CODE_REMOTE:-}" = "true" ] || exit 0

sdk="${ANDROID_HOME:-/opt/android-sdk}"
project="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "$0")/.." && pwd)}"

if ! ls -d "$sdk"/platforms/android-* >/dev/null 2>&1; then
  echo "Android SDK not found at $sdk. Run scripts/install-android-sdk.sh (about a minute), or add it to the cloud environment's setup script — see issue #7."
  exit 0
fi

wanted="sdk.dir=$sdk"
if [ "$(cat "$project/local.properties" 2>/dev/null)" != "$wanted" ]; then
  echo "$wanted" >"$project/local.properties"
fi

if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  {
    echo "export ANDROID_HOME=\"$sdk\""
    echo "export ANDROID_SDK_ROOT=\"$sdk\""
    echo "export PATH=\"$sdk/cmdline-tools/latest/bin:$sdk/platform-tools:\$PATH\""
  } >>"$CLAUDE_ENV_FILE"
fi
exit 0
