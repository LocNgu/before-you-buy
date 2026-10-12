# ADR-0029: Signed APKs on GitHub Releases for version tags; one release key for GitHub and Play

**Status**: accepted; amended by ADR-0030 (how a release is started)
**Type**: technical
**Date**: 2026-10-11

## Context

The owner wants to install the app from GitHub and use it daily long before the Play Store work (#35). Android only installs an update over an existing app if the `applicationId` (ADR-0026) and the signing certificate are the same and the `versionCode` is higher; otherwise the old app must be uninstalled, which deletes its local data (ADR-0002). Owner decisions on #53 (2026-10-11):

- APKs are published on **GitHub Releases**; Play comes later.
- A **dedicated release key**, kept in GitHub Actions secrets and backed up by the owner. Play App Signing should later use **this same key**, so GitHub and Play builds can update each other.
- Releases only for **version tags `v*` on `main`** (ADR-0020); no rolling build from `main`.

Alternatives considered: a rolling pre-release from every push to `main` (more installs to manage, versionCodes from build numbers; rejected by the owner); a separate upload key for Play with Google generating the app signing key (GitHub and Play builds then couldn't update each other); committing an encrypted keystore (key material in the repo, even encrypted).

## Decision

- **Versioning:** the tag is the single source. `vX.Y.Z` gives `versionName = X.Y.Z` and `versionCode = X*10000 + Y*100 + Z`, with Y and Z from 0 to 99 and no pre-release suffix, so codes only ever increase. Builds without a tag get `0.0.0-dev` / `1`, below every release. Nothing writes a version in a build file.
- **Signing:** the `release` build type is signed only when four environment variables are set (`RELEASE_KEYSTORE_PATH`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`). With none it stays unsigned, so PR CI and local builds need no secrets. With only some it fails. Signing refuses the configuration cache, so the passwords are never stored on disk.
- **Secrets** live in a GitHub environment `release` that only `v*` tags may use, not as repository secrets, which any workflow run on any branch could read.
- **Release workflow** (`.github/workflows/release.yml`), on a pushed tag `v*`: fails unless the tag is on `main` and all signing secrets are set; runs `verify`; builds a signed release; refuses an unsigned or debug-signed APK; publishes a GitHub Release named after the tag with `before-you-buy-<version>.apk`, its SHA-256 checksum, and the tag's `CHANGELOG.md` section as notes. The build job is read-only (checkout without persisted credentials); only a separate publish job, which runs no build code, has `contents: write`.
- The logic (version from tag, signing from the environment, changelog section) lives in the `beforeyoubuy.release` plugin in `build-logic`, with JUnit tests (ADR-0025).
- When Play is set up (#35), its App Signing uses this key (upload the existing key instead of letting Google generate one).

## Consequences

- An installed GitHub build updates in place from the next tag, keeping its data, as long as the key and the ID stay the same.
- Losing the key, or changing it, means every installed copy must be uninstalled to update, which loses local data until export/import (#27) exists. The owner keeps an offline backup (`docs/releasing.md`).
- Version numbers are limited to MINOR and PATCH ≤ 99 and no pre-release tags. Changing the scheme later must keep codes increasing.
- A release needs a `CHANGELOG.md` section for its version, so cutting one is: changelog PR, merge, tag `main`, push the tag.
- AAB, R8 mapping upload and Play tracks stay in #35.
