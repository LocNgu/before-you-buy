# Research notes

Collected 2026-10-04 during the planning session. Each finding lists what it means for the product. Not legal advice — the legal/store notes are a starting point for your own verification.

## 1. Behavioural science

### Delay works — if it's long enough, or short but reflective

Carol Moser (University of Michigan) tested delay as a self-control strategy for online impulse buying. A **25-hour delay** lowered both the felt urge to buy and purchase intent. A **10-minute delay failed** — 100 % of participants kept shopping during the wait. Short delays did work when spent **listing reasons for and against** buying (~3.5 min) or on a distracting task.

- Source: [Moser, delay dataset/paper, Deep Blue](https://deepblue.lib.umich.edu/data/downloads/8g84mm54r)
- **Implications:** Long, price-scaled cooling-off periods are right. Never encourage browsing during cooling-off (no prominent "open in shop" button). Reflection includes an explicit *reasons against* list.

### Consumers ask for exactly these tools

A content analysis of 200 top e-commerce sites found 192 use social-influence features and 69 % use urgency (countdowns). A survey of 151 frequent online impulse buyers found they want tools that **make costs salient, encourage reflection, enforce spending limits, increase checkout effort, and postpone purchases.**

- Source: [Moser, Schoenebeck, Resnick — *Impulse Buying: Design Practices and Consumer Needs*, CHI 2019](https://doi.org/10.1145/3290605.3300472)
- **Implication:** The feature set matches stated user needs. The app must be the calm opposite of shop UX: no urgency, no countdown-as-pressure, no social proof.

### An explicit "dismiss" option is the strongest element of a friction intervention

The *one sec* field experiment (280 participants, 6 weeks, PNAS 2023): users dismissed 36 % of attempts to open a target app, and attempted to open it 37 % less often. **The option to dismiss was the most effective feature**; the time delay also contributed.

- Source: [Grüning et al., PNAS 2023 (PMC)](https://pmc.ncbi.nlm.nih.gov/articles/PMC9974409)
- **Implications:** Every cooling-off notification offers **Still want it / Let it go** actions. On the decision screen "Reject" is as prominent as "Buy". App interception (one-sec style) was considered and **not** chosen for scope (policy/technical risk); the share-sheet capture is the app's "interception" moment.

### Opportunity cost neglect

People rarely think of what else the money could buy. Simply describing the "don't buy" option as *"keeping the money for other purchases"* reduced interest in buying a $15 DVD by ~20 %. Cues to consider opportunity costs reduce purchase rates.

- Source: [Frederick, Novemsky, Wang, Dhar, Nowlis — *Opportunity Cost Neglect*, JCR 2009](https://ideas.repec.org/a/oup/jconrs/v36y2009i4p553-561.html)
- **Implications:** Strongest evidence for the opportunity-cost feature. Label the alternative concretely: *"Keep 1,200 € for your Camera (#2)"*.

### Earmarking money increases saving

Labeling money for a specific purpose and adding a visual reminder of the goal increased saving; partitioning earmarked money increased it further.

- Source: [Soman & Cheema, *Earmarking and Partitioning*, JMR 2011](https://www.russellsage.org/sites/default/files/u137/jmr-C-s014-s021-online.pdf)
- **Implication:** Per-item savings goals ("pots") are well-founded.

### Imagining the product increases desire

Exposure to product cues and mentally simulating consumption increase desire and purchase intention; visuals that facilitate more mental simulation raise purchase intent.

- Source: [Overview, Frontiers in Psychiatry 2015](https://www.frontiersin.org/journals/psychiatry/articles/10.3389/fpsyt.2015.00048/pdf)
- **Implication:** During cooling-off, lead with the user's own reasons; keep the product image small/collapsed. (Tension with earmarking's "visual reminder": a goal image may help *saving*. Resolution: image visible in the savings view once Ready/Saving, de-emphasized while cooling off. Revisit with real usage.)

### Moral licensing

Prior virtuous behaviour — including *restraining from impulsive shopping* — can license later indulgence.

- Source: [overview, Korean Journal of Consumer & Advertising Psychology](https://accesson.kr/kscap/v.15/4/665/15326)
- **Implications:** Show the "decided against" total neutrally; no streaks, trophies, "you saved!" celebrations; don't place it next to Ready-to-buy items. Watch for users inflating the number with fake wishes.

### Side-by-side comparison changes choices

Options evaluated jointly vs separately produce preference reversals; in separate evaluation people over-weight easy-to-evaluate attributes.

- Source: [Hsee et al., *Preference reversals between joint and separate evaluations*](https://www.cmu.edu/dietrich/sds/docs/loewenstein/PrefRevJoint.pdf)
- **Implication:** Head-to-head priority comparison; decision screen shows the item next to other priorities.

### Retention is the main product risk

Budgeting/finance apps lose most users within weeks; industry sources put day-30 retention around 7–18 % and name **manual entry burden** and notification fatigue as main reasons. (Figures vary by source; treat as directional.)

- Sources: [financialaha.com](https://www.financialaha.com/articles/why-budgeting-apps-fail-after-3-months/), [eMarketer](https://www.emarketer.com/content/finance-apps-keep-users-engaged-longer-than-any-other-industry)
- **Implications:** Capture ≤ 10 s; allowance accrues automatically; the app must deliver value even if only opened from notifications; max one notification per day.

## 2. Competitors (from store listing snippets — Play pages could not be opened from the research environment)

| App | Platform | What it does | Gap vs. this product |
|---|---|---|---|
| Pause Buy | Android | 24 h cooling-off, wishlist categories, offline | Short fixed delay, no money/priorities |
| Holdoff | Android/iOS | 72 h cooling, daily desire tracking, cost in hours worked; subscription | Short delay, subscription |
| ColdCart | Android | 24 h – 7 d cooldown, reflections, habit picture | Short delays, no savings/priorities |
| Stop Impulse Buying Save Money | Android | Waitlist timers, savings tracker | — |
| Need It? / Hold That! | iOS | Wishlist pause (24–72 h), manual savings tracker | iOS only |
| Goalino | iOS | Wish → savings goal | No cooling-off/decision |
| GreenStash | Android (FOSS) | Savings goals with priority & reminders | No cooling-off/decision |
| Icebox (finder.com) | Chrome ext. | Replaces "Buy" with "Put it on ice", 30-day default | Desktop browser only |
| YNAB | All | Envelope budgeting ("give every dollar a job") | Full budgeting, subscription, not purchase-focused |

**Positioning:** none found combine *price-scaled cooling-off + required reflection + competing priorities (head-to-head) + per-item savings + opportunity cost*, offline and source-available. "Cost in hours worked" is a common competitor feature → added as a Later idea.

## 3. Google Play & legal (verify before acting)

- **Closed test requirement:** personal developer accounts created after 13 Nov 2023 must run a closed test with **≥ 12 testers opted in for 14 consecutive days** before production access. Organization accounts are exempt. ([overview](https://www.testerscommunity.com/blog/google-play-closed-testing-requirements-2026))
- **Developer verification:** rolling out to all developers via Play Console; from 30 Sep 2026 enforced in BR/ID/SG/TH, broader later. ADB installs are exempt (so build-from-source keeps working); an "advanced flow" with a 24 h wait exists for unverified apps; a free limited-distribution hobbyist account (≤ 20 devices) exists. ([Android Developers Blog](https://android-developers.googleblog.com/2026/03/android-developer-verification-rolling-out-to-all-developers.html), [Android Authority](https://www.androidauthority.com/google-android-advanced-flow-sideloading-rollout-begins-3700073/))
- **Target API:** since 31 Aug 2026 new apps/updates must target **Android 16 (API 36)**. ([Google](https://developer.android.com/google/play/requirements/target-sdk))
- **Financial features declaration:** mandatory for every app, including apps with no financial features (declare none — this app moves no money). ([Play Console Help](https://support.google.com/googleplay/android-developer/answer/13849271?hl=en))
- **EU Digital Services Act — trader status:** a developer who monetizes is a "trader"; stores must display the trader's **address, phone and email** publicly. Relevant once the app makes money. ([RevenueCat explainer](https://revenuecat.com/blog/growth/am-i-a-trader-and-other-existential-questions-for-developers))
- **Germany:** selling apps likely requires a business registration (Gewerbe) and has tax implications (Kleinunternehmerregelung). Check before monetizing.

## 4. Naming & trademark

- EU trade mark law refuses marks that are descriptive (Art. 7(1)(c) EUTMR) or lack distinctiveness (Art. 7(1)(b)). Examples: MAXPLAY, INSTASITE, SMARTSURFACE refused. ([MAXPLAY T-400/16](https://www.bailii.org/eu/cases/EUECJ/2017/T40016.html), [INSTASITE T-375/16](https://eu.vlex.com/vid/679621729))
- **"Before You Buy"** describes the app's function → likely hard to register as a *word* mark for software in class 9. A distinctive **logo (figurative mark)** is more realistic. Search visibility is also crowded by the phrase "try before you buy" (incl. a Google Play feature of that name).
- Options: keep the descriptive name + protect the logo, or choose a slightly more distinctive name with a descriptive subtitle (e.g. "<Name> — before you buy"). Decide before the Play listing.

## 5. Licensing

| Licence | Commercial use by others | Forks/redistribution | Converts to open source | Fit |
|---|---|---|---|---|
| **PolyForm Noncommercial 1.0.0** | No | Yes, under the same terms (non-commercial) | No | **Chosen** |
| PolyForm Strict | No | No distribution of changes | No | Too closed |
| PolyForm Shield | Yes, except competing | Yes | No | Too open |
| Functional Source License | Yes, except competing | Yes | Yes, after 2 years | Conflicts with "only I commercialize" |
| Business Source License | Configurable | Yes | Yes, after change date (≤ 4 y) | Conflicts |
| CC BY-NC | — | — | — | Not designed for software |

Sources: [PolyForm licences](https://polyformproject.org/licenses), [FSL](https://fsl.software/).

Notes:

- The licence covers code; **name, logo, icon, screenshots and store assets are reserved** (`TRADEMARKS.md`).
- As sole copyright holder you can always sell/publish the official app regardless of the public licence.
- Accepting outside code would make those parts non-commercially licensed to you too → **no outside code contributions for now**; if that changes, add a CLA first.
- Third-party dependencies must allow use in a non-open-source/commercial app (Apache-2.0, MIT, BSD are fine; avoid GPL/AGPL libraries).
- Do not call the project "open source"; use **"source available — free for personal and non-commercial use."**
- F-Droid only lists FOSS apps, so it's not a distribution channel. Channels: Google Play (official) + build from source.

## 6. Android technical notes

- **Share capture:** register an activity for `ACTION_SEND` with `text/plain`. Shopping apps and browsers put a URL (often with a title) into `EXTRA_TEXT`; some also set `EXTRA_SUBJECT`/`EXTRA_TITLE`. Parse offline: first `http(s)` URL + remaining text as title candidate. Collect real samples from Amazon, eBay, Zalando, Chrome, Firefox as test fixtures. ([Android: receive simple data](https://developer.android.com/training/sharing/receive))
- Fetching page metadata (Open Graph) would need `INTERNET` and is unreliable on Amazon → opt-in, later.
- **Background work:** WorkManager periodic daily check for cooling-off ends / nudges; no exact alarms needed. `POST_NOTIFICATIONS` runtime permission on Android 13+.
- **Navigation 3** is stable (Nov 2025). ([blog](https://android-developers.googleblog.com/2025/11/jetpack-navigation-3-is-stable.html))
- **Images:** Photo Picker (no storage permission), copy into app-private storage.
- **Money:** store `Long` minor units; format with locale-aware currency formatting. Single currency per installation in MVP.
- **Cloud dev environment:** `dl.google.com` (Android SDK downloads) is blocked by the current Claude environment network policy; `maven.google.com` works. Needs an allowlist change (see issue on environment setup).
