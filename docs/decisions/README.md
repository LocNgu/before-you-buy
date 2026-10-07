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
