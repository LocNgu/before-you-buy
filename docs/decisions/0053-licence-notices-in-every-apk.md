# ADR-0053: Every APK carries the third-party licence notices; `verify` enforces the licence rule

**Status**: accepted
**Type**: technical
**Date**: 2026-11-02

## Context

Every library the app ships is Apache-2.0 or BSD-3-Clause. Both licences require that whoever receives the app also receives the licence texts and notices (Apache-2.0 §4, BSD clause 2). Versions 0.1.0 to 0.6.0 shipped without them. ADR-0023 said a licences screen or file is needed "before the first Play release", but the APKs on GitHub Releases (ADR-0029) are distributed too. ADR-0023 also limits shipped code to Apache-2.0, MIT and BSD, and nothing enforced that, so a new transitive dependency could break the rule unnoticed. The owner split #48 (notices in the APK and a check) from #98 (the screen that shows them).

Alternatives considered:

- **A hand-written notices file:** it drifts from the real dependencies.
- **Google's `oss-licenses` plugin:** its runtime library is under the Play Services terms, which ADR-0023 doesn't allow in shipped code.
- **AboutLibraries as configured by default:** it downloads licence texts from spdx.org during the build, and if a download fails it leaves that licence without text and carries on. A release could then ship without the texts, and the build would depend on the network.
- **AboutLibraries' own strict mode as the check:** its failure message names the licence and the libraries but not what to do, it can't record why an artifact was checked by hand, it doesn't check that texts are present, and it only fails while a variant's resources are generated.
- **Cash App's `licensee` as the check:** a second, independent reading of the POMs, which could disagree with what AboutLibraries puts into the APK, and it doesn't check the notices.
- **Only the generic SPDX texts:** the MIT and BSD texts start with a placeholder copyright line (`Copyright (c) <year> <owner>`). Both licences require the actual copyright notice, so the generic text alone doesn't satisfy them.

## Decision

- **AboutLibraries generates the notices.** Its Gradle plugin (`com.mikepenz.aboutlibraries.plugin.android`, Apache-2.0, build time only) writes `res/raw/aboutlibraries.json` into each variant's resources: every external library on the variant's classpath, with its licences, read from the POMs during the build. BOMs are left out. `res/raw/keep.xml` stops resource shrinking from removing the file while nothing references it. No runtime library is added; the screen (#98) can show this file with AboutLibraries' Compose UI.
- **Licence texts are never downloaded** (`offlineMode`). They are committed in `app/third-party/licenses/`, in AboutLibraries' config format. The POMs come through Gradle's dependency resolution, like every dependency.
- **MIT and BSD libraries carry their own copy of the licence**: the library's LICENSE file as its own entry in `app/third-party/licenses/`, linked to the library in `app/third-party/libraries/` (first case: protobuf's licence for `datastore-preferences-external-protobuf`, with Google's copyright line; the copy inside that artifact has no copyright line). A library whose artifact contains a NOTICE file (Apache-2.0 §4(d)) gets the NOTICE text the same way (first case: `jakarta.inject-api`'s `META-INF/NOTICE.md`).
- **`checkDependencyLicences`** (build-logic plugin `beforeyoubuy.dependency-licences`, part of `verify`, ADR-0025) reads that same file for every variant, so it checks exactly what the APK carries. The debug variant covers debug-only dependencies; the test classpaths aren't part of any variant. It fails when:
  - a module on the variant's runtime classpath is missing from the file. AboutLibraries leaves a library out, with only a log warning, when it can't download its POM, which happened while this was built (Maven Central rate limits);
  - a licence AboutLibraries recognises isn't Apache-2.0, MIT, BSD-2-Clause or BSD-3-Clause;
  - a licence it doesn't recognise, or no licence at all, appears, unless `app/third-party/allowlist.json` lists the artifact with the licence a person found by hand (one of those four) and why, and the notices link the library to that licence's text. The allow-list can't permit any other licence, and an entry stops applying once the licence is recognised (the check's report lists such entries);
  - a licence a library uses has no text, an MIT or BSD library has no own copy (one still showing the SPDX template's `<year>`/`<owner>` placeholders doesn't count), or the text of a NOTICE file in a shipped AAR or JAR (not an AAR's `lint.jar`, which never reaches the APK) isn't among its library's licences.
- A POM that names several licences is held to all of them: one recognised licence that isn't allowed fails the library, even if the POM means "either". Accepting a dual-licensed library needs its own decision.
- A failure names the ADR, the artifact and the fix (allow-list entry, missing text, or run again), and is also a GitHub Actions error annotation.

## Consequences

- Every APK, debug or release, from GitHub Releases or later Play, carries the notices. ADR-0023's "licences screen or file" is now this file; the screen follows in #98.
- Adding a dependency can fail `verify`: an MIT or BSD library needs its LICENSE file committed, a NOTICE file its text, an unusual POM an allow-list entry. `.claude/rules/build.md` says what to do.
- A cold Gradle cache with failing POM downloads now fails the check instead of shipping incomplete notices; running the build again fixes it.
- The check relies on AboutLibraries' mapping from POM licence names to SPDX ids. That mapping is loose for some ids (any name containing "Apache" counts as Apache-2.0, any containing "MIT" as MIT), so a new dependency's licence is still read by hand when it's added, as ADR-0023 asks.
- A new licence text (for example a BSD variant) must be committed before a library under it passes, because nothing is downloaded.
- Amends ADR-0023 (its consequence that a licences screen or file is needed before the first Play release).
