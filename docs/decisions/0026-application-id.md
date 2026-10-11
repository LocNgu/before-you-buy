# ADR-0026: applicationId `io.github.locngu.beforeyoubuy`

**Status**: accepted
**Type**: technical
**Date**: 2026-10-11

## Context

Development used the placeholder `io.github.locngu.beforeyoubuy` (#33). The owner wants to install the app from GitHub Releases and use it daily (#53), long before the first Play upload. Android identifies an app by its `applicationId`: a build with a different ID installs as a separate app with its own (empty) data. On Google Play the ID can never change after the first upload.

The owner plans several small apps, so the ID should follow a scheme that works for all of them. Options considered (#33):

- **`io.github.<user>.<app>`:** free and available today, but tied to the GitHub account name.
- **Reverse of an owned domain** (`de.<domain>.<app>`): independent of GitHub and more professional, but needs a domain the owner doesn't have yet.
- **Brand namespace** (`app.<brand>.android`): only makes sense once a brand domain exists.

## Decision

- The applicationId, and the root of namespaces and Kotlin packages, is **`io.github.locngu.beforeyoubuy`**.
- The owner's apps use the scheme `io.github.locngu.<codename>`. The codename is short, stable and not tied to the display name, which may change (#31).
- The ID **may still change** (e.g. to an owned domain) **until the first Play upload**, and is permanent after that.
- A change needs export/import (#27) to exist first. The steps are: install the new-ID build next to the old one, export from the old app, import into the new one, then uninstall the old app. Signing keys don't change with the ID.

## Consequences

- Release builds from GitHub (#53) can be installed as the long-term app now.
- Moving to a domain later costs one export/import for each device, and only before Play.
- `app.id` in `gradle.properties` remains the only place the ID is written. Kotlin packages moved with it, so the generated `R` class and the source packages match.
- Before the first Play upload, check that the ID isn't taken (`https://play.google.com/store/apps/details?id=io.github.locngu.beforeyoubuy`).
