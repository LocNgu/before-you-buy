# CLAUDE.md

Guidance for Claude sessions working in this repository. Most code here is written by Claude sessions, one GitHub issue at a time.

## Read first

1. `docs/product-brief.md` — what the app is, the rules, the lifecycle, copy/tone rules
2. `docs/architecture.md` — stack, modules, hard constraints
3. `docs/research.md` — why things are the way they are (only when a design question comes up)
4. The GitHub issue you were asked to implement, including its parent epic

## Working on an issue

- Implement **only** the issue's scope. If something outside it is needed, note it in the PR description instead of widening the change.
- Every acceptance-criteria checkbox must be met or explicitly explained in the PR.
- Business rules go into `:core:domain` with JVM unit tests. Don't put rules in ViewModels or Composables.
- UI changes: add/update Roborazzi screenshot tests once the screenshot setup exists.
- All user-facing strings go into `strings.xml` in **both** `values/` (English) and `values-de/` (German).
- Run before pushing: `./gradlew spotlessCheck lint test` (adjust once the scaffold defines the exact tasks — keep this line accurate).
- If you change a documented decision, update the relevant doc in the same PR.

## Product non-negotiables

- Never build anything that encourages spending: no "you have money available", no deals, price-drop alerts, product suggestions or urgency.
- No shaming. "Decided against" is neutral — no streaks, trophies, confetti or "you saved!" language.
- Reject/"Let it go" is always as easy and visible as Buy.
- While an item is cooling off, lead with the user's reasons, not the product image; no prominent "open in shop" button.
- No `INTERNET` permission, no analytics, no ads, no account. Data stays on the device.
- Money is `Long` minor units, never floating point. Time comes from an injected `Clock`.

## Licensing constraints

- The project is **source-available (PolyForm Noncommercial 1.0.0), not open source** — don't describe it as open source.
- Only add dependencies with permissive licences (Apache-2.0, MIT, BSD). No GPL/AGPL.
- Don't accept or merge code contributions from third parties (see `CONTRIBUTING.md`).

## Environment notes

- Cloud sessions need network access to `dl.google.com` to install the Android SDK (issue #7). `maven.google.com` is reachable by default.
- Java 21 and Gradle are preinstalled in the cloud image; the Android SDK is not.
