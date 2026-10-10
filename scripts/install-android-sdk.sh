#!/usr/bin/env bash
# Installs the Android SDK pieces needed to build this app (ADR-0021, issue #7).
#
# Meant to run from the Claude cloud environment's setup script, whose result is
# cached, but works anywhere on Linux:
#   curl -fsSL https://raw.githubusercontent.com/LocNgu/before-you-buy/main/scripts/install-android-sdk.sh | bash
#   scripts/install-android-sdk.sh            # from a checkout
#
# Installs cmdline-tools, platform-tools, the platform for compileSdk and a
# matching build-tools into $ANDROID_HOME (default /opt/android-sdk).
# Needs network access to dl.google.com.
#
# Idempotent: skips what is already installed. Always exits 0 so a flaky
# download never blocks a session from starting; failures are printed as
# warnings and the SessionStart hook reports a missing SDK.
#
# Overrides (environment variables):
#   ANDROID_HOME          install location
#   ANDROID_COMPILE_SDK   API level, if it can't be read from the build files

set -uo pipefail

# Used when the build files can't be read (e.g. the script runs before the
# repository is cloned). Keep in step with compileSdk.
DEFAULT_COMPILE_SDK=37

# sdkmanager only knows packages that existed when its own build was released,
# so an old build can't see new platforms. Bump this when a new compileSdk
# isn't found (builds are listed in https://dl.google.com/android/repository/repository2-3.xml).
# Not newer on purpose: from build 16111833 (cmdline-tools 23) `sdkmanager` is a
# deprecated wrapper around the new `android` CLI, which also calls
# play.google.com (blocked in cloud sessions) and reports failures for
# successful installs. Re-test before bumping past it.
CMDLINE_TOOLS_BUILD=15859902

ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/opt/android-sdk}}"
export ANDROID_HOME ANDROID_SDK_ROOT="$ANDROID_HOME"

log() { echo "[android-sdk] $*"; }
warn() { echo "[android-sdk] WARNING: $*" >&2; }

finish() {
  log "done (ANDROID_HOME=$ANDROID_HOME)"
  exit 0
}

compile_sdk() {
  if [ -n "${ANDROID_COMPILE_SDK:-}" ]; then
    echo "$ANDROID_COMPILE_SDK"
    return
  fi
  local root value
  # Piped through `curl | bash` there is no script file and no checkout to read.
  if [ ! -f "${BASH_SOURCE[0]:-}" ]; then
    echo "$DEFAULT_COMPILE_SDK"
    return
  fi
  root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
  for file in "$root/gradle/libs.versions.toml" "$root/app/build.gradle.kts" "$root/build.gradle.kts"; do
    [ -f "$file" ] || continue
    # Matches `compileSdk = 36`, `compileSdk = "36"` and `compileSdk = "36.1"` (major only).
    value="$(sed -nE 's/^[[:space:]]*compileSdk[[:space:]]*=[[:space:]]*"?([0-9]+).*/\1/p' "$file" | head -1)"
    if [ -n "$value" ]; then
      echo "$value"
      return
    fi
  done
  echo "$DEFAULT_COMPILE_SDK"
}

for tool in curl unzip java; do
  if ! command -v "$tool" >/dev/null 2>&1; then
    warn "'$tool' is not installed; cannot install the Android SDK"
    finish
  fi
done

SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
INSTALLED_BUILD_FILE="$ANDROID_HOME/cmdline-tools/latest/.bootstrap-build"

if [ ! -x "$SDKMANAGER" ] || [ "$(cat "$INSTALLED_BUILD_FILE" 2>/dev/null)" != "$CMDLINE_TOOLS_BUILD" ]; then
  log "installing cmdline-tools build $CMDLINE_TOOLS_BUILD"
  tmp="$(mktemp -d)"
  url="https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_BUILD}_latest.zip"
  if curl -fsSL --retry 3 -o "$tmp/tools.zip" "$url" && unzip -q "$tmp/tools.zip" -d "$tmp"; then
    mkdir -p "$ANDROID_HOME/cmdline-tools"
    rm -rf "$ANDROID_HOME/cmdline-tools/latest"
    mv "$tmp/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
    echo "$CMDLINE_TOOLS_BUILD" >"$INSTALLED_BUILD_FILE"
  else
    warn "could not download $url (is dl.google.com allowed in the network settings?)"
  fi
  rm -rf "$tmp"
fi

if [ ! -x "$SDKMANAGER" ]; then
  warn "sdkmanager missing; skipping package installation"
  finish
fi

yes 2>/dev/null | "$SDKMANAGER" --licenses >/dev/null 2>&1 || true

API="$(compile_sdk)"
# Package ids are the first column. Classic sdkmanager prints `id | version | ...`;
# the newer wrapper prints `id version ...` with '/' separators, so normalise both.
AVAILABLE="$("$SDKMANAGER" --list 2>/dev/null | sed -nE 's/^[[:space:]]+([A-Za-z][^[:space:]|]*).*/\1/p' | tr '/' ';' | sort -u)"
if [ -z "$AVAILABLE" ]; then
  warn "could not list SDK packages"
  finish
fi

# From API 37 on, platforms are only published with a minor version
# (platforms;android-37.0, -37.1, ...). Prefer the bare id, else the lowest
# stable minor, so the platform matches `compileSdk = N`.
platform="$(grep -xE "platforms;android-${API}" <<<"$AVAILABLE" | head -1)"
if [ -z "$platform" ]; then
  platform="$(grep -xE "platforms;android-${API}\.[0-9]+" <<<"$AVAILABLE" | sort -t. -k2,2n | head -1)"
fi

# Newest stable build-tools for the same major, else the newest stable overall.
build_tools="$(grep -xE "build-tools;${API}\.[0-9]+\.[0-9]+" <<<"$AVAILABLE" | sort -V | tail -1)"
if [ -z "$build_tools" ]; then
  build_tools="$(grep -xE "build-tools;[0-9]+\.[0-9]+\.[0-9]+" <<<"$AVAILABLE" | sort -V | tail -1)"
fi

packages=("platform-tools")
if [ -n "$platform" ]; then
  packages+=("$platform")
else
  warn "no stable platform package found for API $API; Gradle may download it on first build"
fi
[ -n "$build_tools" ] && packages+=("$build_tools")

missing=()
for pkg in "${packages[@]}"; do
  # Installed packages live at the path given by their id, with ';' as '/'.
  [ -d "$ANDROID_HOME/${pkg//;//}" ] || missing+=("$pkg")
done

if [ ${#missing[@]} -eq 0 ]; then
  log "already installed: ${packages[*]}"
  finish
fi

log "installing: ${missing[*]}"
# Judge success by what is on disk: under pipefail, `yes` exits non-zero
# (broken pipe) even when sdkmanager succeeds.
yes 2>/dev/null | "$SDKMANAGER" --install "${missing[@]}" >/dev/null 2>&1
failed=()
for pkg in "${missing[@]}"; do
  [ -d "$ANDROID_HOME/${pkg//;//}" ] || failed+=("$pkg")
done
if [ ${#failed[@]} -gt 0 ]; then
  warn "could not install: ${failed[*]}"
else
  log "installed: ${missing[*]}"
fi
finish
