---
name: run-app
description: Build, install, launch, and drive this Android app on an emulator via adb. Use when asked to run the app, start it, take a screenshot of a screen, tap through a flow, or verify a UI change actually works on-device.
---

This is an Android app (Kotlin + Jetpack Compose, Gradle build). It has no
headless/CLI mode — driving it means running an Android emulator and sending
it `adb` input events. Drive it via `.claude/skills/run-app/driver.sh`, which
wraps `gradlew`, `adb`, and `uiautomator` into single commands (build, install,
launch, screenshot, tap-by-text, tap-by-coordinate, back, logcat).

All paths below are relative to the repo root.

## Prerequisites

- Android SDK with an emulator image and at least one AVD already created.
  Create one with Android Studio's Device Manager or `avdmanager` if there is none.
- `adb` on `PATH`; `ANDROID_HOME` set (the driver falls back to
  `~/Library/Android/sdk`).
- JDK 17 for Gradle.

The driver reads the application id from `app/build.gradle.kts`
(`applicationId`, else `namespace`) and resolves the launcher activity on-device,
so it needs no editing when the package name changes. Override with `APP_ID` /
`APP_ACTIVITY` if detection ever fails.

## Build

```bash
./gradlew assembleDebug --console=plain
```

Produces `app/build/outputs/apk/debug/app-debug.apk`.

## Run (agent path)

Use the driver for every step — it auto-detects a running emulator/device
and only boots a fresh one if none is attached (checked via `adb devices`),
so it never disturbs a device you already have open.

```bash
.claude/skills/run-app/driver.sh build               # ./gradlew assembleDebug
.claude/skills/run-app/driver.sh launch              # ensure emulator + install if missing + am start
.claude/skills/run-app/driver.sh screenshot <name>   # -> /tmp/app-shots/<name>.png (prints path)
.claude/skills/run-app/driver.sh tap-text "Settings" # uiautomator dump, tap first match's center
.claude/skills/run-app/driver.sh tap <x> <y>         # raw coordinate tap (from a screenshot you inspected)
.claude/skills/run-app/driver.sh text "hello"        # types text into a focused field
.claude/skills/run-app/driver.sh back                # KEYCODE_BACK — from the root screen this exits to the launcher
.claude/skills/run-app/driver.sh dump                # pulls uiautomator XML to /tmp/app-window-dump.xml
.claude/skills/run-app/driver.sh logcat              # dumps current logcat filtered to the app's pid
.claude/skills/run-app/driver.sh stop                # am force-stop
.claude/skills/run-app/driver.sh test                # ./gradlew testDebugUnitTest
```

Typical flow for verifying a UI change:

```bash
.claude/skills/run-app/driver.sh build
.claude/skills/run-app/driver.sh launch
.claude/skills/run-app/driver.sh screenshot before
# ... inspect the screenshot, then drive the flow you care about ...
.claude/skills/run-app/driver.sh tap-text "Add"
.claude/skills/run-app/driver.sh screenshot after
```

Then actually view the PNGs (e.g. with the Read tool) — don't assume from
exit codes.

`tap-text` matches any element's `text` or `content-desc` attribute by
substring, case-sensitively, and taps the center of the first match in
document order. If nothing matches it exits 1 with a message rather than
tapping the wrong thing.

`ANDROID_SERIAL` can be set to target a specific device when more than one
is attached; otherwise the driver uses the first device adb reports as
`device` (not `offline`/`unauthorized`). `APP_AVD` picks a specific AVD to boot.

## Test

```bash
./gradlew testDebugUnitTest --console=plain
```

JVM unit tests only — no emulator needed. Instrumented tests
(`connectedDebugAndroidTest`) need the emulator and take much longer; run
them only if the change touches DB migrations, Compose screen tests, or
other `androidTest` code.

## Gotchas

- **Some composables expose no `text` or `content-desc` to the accessibility
  tree** and are entirely absent from `uiautomator dump` output even though
  they're clearly on screen — extended FABs are a repeat offender. `tap-text`
  fails with "No element matching" for those. Use `screenshot` + eyeball the
  pixel center + `tap <x> <y>` instead. (If a control you own is invisible to
  the dump, that's usually an accessibility bug worth fixing rather than
  working around.)
- **`back` from the app's home screen exits to the Android launcher** — it
  does not just pop a Compose nav-graph screen when the back stack is empty.
  To return to a specific in-app screen, `tap-text` that screen's back arrow
  or re-navigate rather than pressing `back` blindly.
- **`launch` on an already-open app does not reset navigation state** — `am
  start` on a running task just brings the existing top-most screen forward
  (`Warning: Activity not started, intent has been delivered to currently
  running top-most instance.` — not an error). For a clean start state,
  `stop` first, then `launch`.
- **A one-time dialog can intercept your next tap.** If the app shows
  first-run or periodic prompts, a `tap-text` aimed at what's underneath will
  hit the overlay instead. Screenshot first, or dismiss defensively.
- **Treat a device with real personal data as non-disposable.** Don't run
  destructive flows (wipe, delete-forever, restore-from-backup) against the
  user's everyday emulator without asking; boot a second AVD on another port
  for that.
- **`nohup emulator ... &` from a non-interactive shell needs `disown`** or
  job control can hang the launching command — `ensure_emulator` already does this.
- **macOS has no `timeout` binary** (it's GNU coreutils) — the driver polls
  boot state with a manual loop-and-sleep counter instead.

## Troubleshooting

- **`adb devices` lists a freshly-killed emulator as `offline` for a few
  seconds** after `adb -s <serial> emu kill` returns `OK`. The process is
  actually gone (verify with `ps aux | grep -i avd`); `adb kill-server` +
  `adb start-server` clears the stale entry immediately.
- **`cmd package resolve-activity` returns nothing** — the app isn't installed
  yet (`install` first) or declares no LAUNCHER intent filter; the driver falls
  back to `monkey` in that case.
