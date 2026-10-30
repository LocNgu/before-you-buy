# ADR-0036: Share capture opens quick add over the sharing app; name and link read offline

**Status**: accepted; amended by ADR-0049 (the toast says "Added to your wishlist." and no longer matches the snackbar)
**Type**: product
**Date**: 2026-10-18

## Context

Sharing from a shop app or browser is the app's "intercept" moment (#15, brief §6.2, ADR-0015). The app has no `INTERNET` permission (ADR-0003), so all it gets is what the share carries: `EXTRA_TEXT` (a link, often with a title and phrases like "Check out this deal on Amazon") and sometimes `EXTRA_SUBJECT`/`EXTRA_TITLE`. The price is never in a form that can be trusted. The issue fixes the parser's main rules; open were where the sheet appears, what confirms the save, what an item with a link but no name is called, and which link parameters count as tracking.

Alternatives considered:

- Open the full app (`MainActivity`) with the sheet on Home: the user lands in this app instead of going back to the shop, and with a running app the share would bring the app's whole task forward.
- A snackbar after saving, as in the app: the activity finishes as the sheet slides away, so a snackbar would never be read.
- Leave the name empty when the share has none: the user has to type a name before saving, which breaks "≤ 10 seconds".

## Decision

- **A separate, translucent `ShareReceiverActivity`** (`ACTION_SEND`, `text/plain`, labelled with the app name) shows only the quick-add sheet over the app the share came from. Saving or closing it finishes the activity, so the user is back where they were. It has an empty task affinity and is excluded from recents, so it never brings the app's own task to the front.
- **A toast confirms the save** with the same words as the snackbar ("Added. You can decide in 30 days."); it stays readable after the activity is gone.
- **The cursor starts in the price** when the share filled in the name but not the price, so the shortest path is share → price → Done. The price is never filled in from the text.
- **A link without a name is named after the shop**: "amazon.de item" / "Artikel von amazon.de", from the link's host without `www.`/`m.`. The user can edit it like any name.
- **The name** is what's left of the text without links, boilerplate phrases (en/de) and the shop's name next to the title ("… | eBay", "Amazon.de: …"), on one line, at most 100 characters (cut at a word, ending with "…"). Without text, the subject (else the title) is used the same way.
- **Tracking is removed from the link**: `utm_*`, `ref`, `tag`, click ids (`fbclid`, `gclid`, …), Amazon's `/ref=…` path segment, search and affiliate parameters, and eBay's share parameters; every other parameter stays in order. Amazon's `/dp/<ASIN>` path stays intact. A Chrome text fragment (`#:~:text=…`) goes; other fragments stay.
- The parser (`parseSharedText` in `:core:domain`) never fails: unreadable text gives an empty draft and an empty sheet. Non-text shares (images) aren't offered the app.

## Consequences

- Fetching title, price or image from the page needs `INTERNET` and stays out of scope (opt-in, later).
- Boilerplate and tracking lists cover the shops tested in `SharedTextTest`; fixtures there are realistic but not captured from devices. New shops or phrases are added to the lists with a fixture.
- Returning to the sharing app depends on Android's task handling and can only be checked on a device.
- Shop names like "amzn.eu item" for short links are accepted; mapping short hosts to shop names would need a list to maintain.
