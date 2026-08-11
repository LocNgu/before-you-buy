#!/usr/bin/env bash
# Driver for building, launching, and driving this Android app via adb.
# Run from anywhere: .claude/skills/run-app/driver.sh <command> [args...]
#
# Project-agnostic: the application id is read from app/build.gradle.kts and the
# launcher activity is resolved on-device, so nothing here needs editing when the
# package name changes. Override with APP_ID / APP_ACTIVITY if detection fails.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
EMULATOR_BIN="$SDK/emulator/emulator"
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
SHOTS_DIR="${APP_SHOTS_DIR:-/tmp/app-shots}"
DUMP_PATH="/tmp/app-window-dump.xml"

# applicationId if present, else the namespace (they match in a single-module app).
detect_pkg() {
  if [ -n "${APP_ID:-}" ]; then
    echo "$APP_ID"
    return
  fi
  local gradle="$ROOT/app/build.gradle.kts"
  local pkg
  pkg="$(sed -nE 's/.*applicationId[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/p' "$gradle" 2>/dev/null | head -1)"
  [ -z "$pkg" ] && pkg="$(sed -nE 's/.*namespace[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/p' "$gradle" 2>/dev/null | head -1)"
  if [ -z "$pkg" ]; then
    echo "Could not detect the application id from app/build.gradle.kts — set APP_ID=<package>" >&2
    exit 1
  fi
  echo "$pkg"
}

# Resolved lazily so `build`/`test` still work before the app module exists.
PKG=""
pkg() {
  [ -z "$PKG" ] && PKG="$(detect_pkg)"
  echo "$PKG"
}

serial() {
  if [ -n "${ANDROID_SERIAL:-}" ]; then
    echo "$ANDROID_SERIAL"
    return
  fi
  adb devices | awk '$2=="device"{print $1; exit}'
}

ensure_emulator() {
  local s
  s="$(serial)"
  if [ -n "$s" ]; then
    echo "Using running device/emulator: $s" >&2
    return
  fi
  local avd="${APP_AVD:-$("$EMULATOR_BIN" -list-avds | head -1)}"
  if [ -z "$avd" ]; then
    echo "No device attached and no AVD found — create one in Android Studio's Device Manager." >&2
    exit 1
  fi
  echo "No device attached — booting AVD '$avd' headless..." >&2
  nohup "$EMULATOR_BIN" -avd "$avd" -no-window -no-audio -no-boot-anim \
    > /tmp/app-emulator-boot.log 2>&1 &
  disown || true
  local i=0
  while [ "$i" -lt 120 ]; do
    s="$(serial)"
    if [ -n "$s" ] && [ "$(adb -s "$s" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; then
      echo "Booted: $s" >&2
      return
    fi
    sleep 3
    i=$((i + 3))
  done
  echo "Emulator did not finish booting within 120s; see /tmp/app-emulator-boot.log" >&2
  exit 1
}

# Resolves the LAUNCHER activity from the installed package rather than hardcoding it.
launcher_component() {
  if [ -n "${APP_ACTIVITY:-}" ]; then
    echo "$APP_ACTIVITY"
    return
  fi
  adb -s "$(serial)" shell cmd package resolve-activity --brief \
    -a android.intent.action.MAIN -c android.intent.category.LAUNCHER "$(pkg)" \
    2>/dev/null | tail -1 | tr -d '\r'
}

cmd_build() {
  (cd "$ROOT" && ./gradlew assembleDebug --console=plain)
}

cmd_install() {
  ensure_emulator
  adb -s "$(serial)" install -r "$APK"
}

cmd_launch() {
  ensure_emulator
  local s component
  s="$(serial)"
  if ! adb -s "$s" shell pm list packages | grep -q "$(pkg)"; then
    adb -s "$s" install -r "$APK"
  fi
  component="$(launcher_component)"
  if [ -n "$component" ] && [ "$component" != "No activity found" ]; then
    adb -s "$s" shell am start -n "$component"
  else
    adb -s "$s" shell monkey -p "$(pkg)" -c android.intent.category.LAUNCHER 1 >/dev/null
  fi
  sleep 2
}

cmd_screenshot() {
  local name="${1:-shot}"
  mkdir -p "$SHOTS_DIR"
  local s path
  s="$(serial)"
  path="$SHOTS_DIR/${name}.png"
  adb -s "$s" exec-out screencap -p > "$path"
  echo "$path"
}

cmd_dump() {
  local s
  s="$(serial)"
  adb -s "$s" shell uiautomator dump /sdcard/window_dump.xml >/dev/null
  adb -s "$s" pull /sdcard/window_dump.xml "$DUMP_PATH" >/dev/null
  echo "$DUMP_PATH"
}

# Finds the center point of the first element whose text or content-desc
# contains the given substring and taps it. Case-sensitive substring match.
cmd_tap_text() {
  local needle="$1"
  local s dump bounds x1 y1 x2 y2 cx cy
  s="$(serial)"
  dump="$(cmd_dump)"
  bounds="$(grep -o '\(text\|content-desc\)="[^"]*"[^>]*bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' "$dump" \
    | grep -F "$needle" \
    | head -1 \
    | grep -o 'bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' \
    | grep -o '[0-9]\+' | tr '\n' ' ')"
  if [ -z "$bounds" ]; then
    echo "No element matching '$needle' found in UI dump" >&2
    exit 1
  fi
  read -r x1 y1 x2 y2 <<< "$bounds"
  cx=$(( (x1 + x2) / 2 ))
  cy=$(( (y1 + y2) / 2 ))
  adb -s "$s" shell input tap "$cx" "$cy"
}

cmd_tap() {
  adb -s "$(serial)" shell input tap "$1" "$2"
}

cmd_text() {
  adb -s "$(serial)" shell input text "${1// /%s}"
}

cmd_back() {
  adb -s "$(serial)" shell input keyevent KEYCODE_BACK
}

cmd_logcat() {
  local s
  s="$(serial)"
  adb -s "$s" logcat -d --pid="$(adb -s "$s" shell pidof -s "$(pkg)")"
}

cmd_stop() {
  adb -s "$(serial)" shell am force-stop "$(pkg)"
}

cmd_test() {
  (cd "$ROOT" && ./gradlew testDebugUnitTest --console=plain)
}

case "${1:-}" in
  build) cmd_build ;;
  install) cmd_install ;;
  launch) cmd_launch ;;
  screenshot) cmd_screenshot "${2:-shot}" ;;
  dump) cmd_dump ;;
  tap-text) cmd_tap_text "$2" ;;
  tap) cmd_tap "$2" "$3" ;;
  text) cmd_text "$2" ;;
  back) cmd_back ;;
  logcat) cmd_logcat ;;
  stop) cmd_stop ;;
  test) cmd_test ;;
  *)
    echo "Usage: driver.sh {build|install|launch|screenshot [name]|dump|tap-text <substring>|tap <x> <y>|text <string>|back|logcat|stop|test}" >&2
    exit 1
    ;;
esac
