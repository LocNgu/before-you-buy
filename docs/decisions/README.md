# Decision records (ADRs)

Each file records one decision that shapes how future work is done. They exist so a fresh Claude session (or a future you) doesn't silently undo a decision whose reason isn't visible in the code.

## Rules

- **One sequence.** Files are `NNNN-kebab-title.md`, numbered in order regardless of type. The `Type:` line says whether it's a `product` or `technical` decision. Cite as `ADR-NNNN`.
- **Before working in an area, read the ADRs for it** (scan the index below).
- **If a request contradicts an ADR**, name the ADR and its reasoning and get the owner's confirmation before proceeding. If the decision changes, write a new ADR that supersedes the old one.
- **Accepted ADRs are not rewritten.** Only the Status line changes (`superseded by ADR-NNNN` or `amended by ADR-NNNN (<clause>)`), plus typo-level corrections.
- **New ADR when** a PR introduces a new default, a chosen library/pattern, or a non-obvious behaviour rule. Routine fixes don't need one. Copy `template.md`, take the next number, add it to the index.

## Index

| ADR | Type | Decision |
|---|---|---|
| [0001](0001-android-native-kotlin-compose.md) | technical | Android only, native Kotlin + Jetpack Compose |
| [0002](0002-local-only-no-account.md) | product | Local-only data; no account, server, bank connection or analytics |
| [0003](0003-no-internet-permission.md) | technical | No `INTERNET` permission, enforced by CI |
| [0004](0004-licence-and-contributions.md) | product | PolyForm Noncommercial; brand reserved; no outside code contributions |
| [0005](0005-price-tiered-cooling-off.md) | product | Price-tiered cooling-off; price edits only extend it |
| [0006](0006-soft-lock-early-purchase.md) | product | Soft lock: early purchase allowed with friction, always logged |
| [0007](0007-parallel-lifecycle-derived-stage.md) | product | Waiting and saving in parallel; Ready = cooled + reflected + funded; stage derived |
| [0008](0008-need-fast-path.md) | product | `NEED` items skip cooling-off and reflection, still logged |
| [0009](0009-reflection-after-capture.md) | product | Reflection after capture, required before Ready, includes reasons against |
| [0010](0010-monthly-allowance-earmarks.md) | product | Discretionary money = auto-accruing monthly allowance; savings are earmarks |
| [0011](0011-priorities-head-to-head.md) | product | 1–5 priority slots (default 3); new items win a slot head-to-head |
| [0012](0012-neutral-decided-against.md) | product | "Decided against" shown neutrally; no streaks, trophies or celebration |
| [0013](0013-reasons-before-images.md) | product | While cooling off: reasons first, image de-emphasized, no "open in shop" button |
| [0014](0014-notification-rules.md) | product | Max one notification a day; "Still want it / Let it go"; never money or deals |
| [0015](0015-capture-methods.md) | product | Capture via share sheet + quick add; widget later; no app interception |
| [0016](0016-languages-en-de.md) | product | English + German from day one |
| [0017](0017-tech-stack.md) | technical | Hilt, Room, Navigation 3, WorkManager, Spotless/ktlint, Roborazzi; pure domain module |
| [0018](0018-money-and-time.md) | technical | Money as `Long` minor units; time from an injected `Clock`, calendar-day arithmetic |
| [0019](0019-room-explicit-migrations.md) | technical | Room: explicit migrations only, hard crash if missing, schemas committed |
| [0020](0020-workflow-main-only-single-session.md) | technical | `main` only; one issue per PR; one session implements; human merges |
| [0021](0021-cloud-build-environment.md) | technical | Cloud builds: SDK in the environment setup script, project config in a SessionStart hook |
| [0022](0022-monetization-deferred.md) | product | Free for now; monetization decided later |
| [0023](0023-licence-rule-shipped-dependencies.md) | technical | Licence rule (Apache/MIT/BSD) covers shipped dependencies; test and build tools exempt |
| [0024](0024-branch-naming.md) | technical | Branches: session's assigned `claude/*` branch, else `claude/<N>-<kebab-title>` |
| [0025](0025-build-checks-in-build-logic.md) | technical | Project-specific build checks are tested Gradle plugins in the `build-logic` included build |
| [0026](0026-application-id.md) | technical | applicationId `io.github.locngu.beforeyoubuy` (scheme `io.github.locngu.<codename>`); changeable until the first Play upload |
| [0027](0027-money-formatting-and-input.md) | technical | Money formatting/parsing in `:core:domain`; input accepts `.` or `,` as decimal separator (display amended by ADR-0034) |
| [0028](0028-calm-fixed-theme.md) | product | Calm fixed palette, no dynamic colour, no urgency colours, identical Buy/Reject buttons |
| [0029](0029-github-releases-signed-apk.md) | technical | Signed APKs on GitHub Releases for tags `vX.Y.Z` on `main`; version from the tag; one release key, later also Play's app signing key |
| [0030](0030-release-button.md) | technical | Releases start from the Release workflow's "Run workflow" button on `main`; it tags only after a successful build |
| [0031](0031-tier-changes-apply-to-waiting-items.md) | product | Changed cooling-off tiers also apply to items still cooling off, in both directions |
| [0032](0032-storage-layout-and-defensive-reads.md) | technical | Storage: reasons against as JSON, append-only decisions, compare-and-set item writes, preference defaults computed on read |
| [0033](0033-policy-in-room-currency-relabel.md) | product | Currency and tiers stored with the items (one transaction for tier changes); changing the currency relabels every amount |
| [0034](0034-whole-units-in-display.md) | product | Amounts shown in whole units, rounded (`20 €` for 19,99 €); storage, input and sums stay exact |
| [0035](0035-quick-add-form-behaviour.md) | product | Quick add: errors once a field held text, keyboard Done saves, closing discards the draft |
| [0036](0036-share-capture-over-the-sharing-app.md) | product | Share capture: quick add over the sharing app, toast on save, cursor in the price, shop-named fallback, tracking removed |
| [0037](0037-viewmodels-per-nav-entry.md) | technical | ViewModels are scoped to their Navigation 3 entry; per-item screens use assisted injection |
| [0038](0038-item-photos-in-app-storage.md) | technical | Item photos: Photo Picker, re-encoded JPEG copies (≤ 1600 px, no metadata) in app storage, unreferenced files swept at app start |
| [0039](0039-reflection-flow-behaviour.md) | product | Reflection flow: answers stored as typed, required questions can't be skipped, back leaves, opens at the first missing required question |
| [0040](0040-decision-screen-behaviour.md) | product | Decision screen: Buy/Reject pair + Wait when Ready or a need, else Reject + "Decide early…"; waited items in their own group; purchases dated back via `decidedAt`; reject note in `decisions.note` (Home's "To decide" amended by ADR-0043) |
| [0041](0041-history-behaviour.md) | product | History: one period filter for both tabs; "Want it again?" adds a fresh want (same facts, no answers, never a need); delete only on purpose, with a confirm |
| [0042](0042-notification-behaviour.md) | product | Notifications: waiting periods before nudges; per-item log in Room; daily check around 10:00 via WorkManager; permission asked once after the first item; "Let it go" removes the notification silently |
| [0043](0043-home-behaviour.md) | product | Home: "To decide" = due for decision + waits ended without answers; Wishlist/History linked from Home (no bottom bar); no Settings entry until #26; M2 sections as quiet placeholders |
