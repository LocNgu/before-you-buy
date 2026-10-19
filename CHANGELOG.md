# Changelog

All notable user-visible changes. Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Versions follow [SemVer](https://semver.org/).

## [Unreleased]

### Added

- Reflection: tap "Start" on an item's "Why do you want this?" card to answer a few questions, one at a time: why you want it, how often you'll use it and what speaks against it (add as many reasons as you like), then five optional ones you can skip. Answers are kept as you type, so you can leave at any point and continue later. Once the first three are answered, the item can become Ready when its waiting period ends. Change them anytime with "Edit answers"; that never changes the waiting period.

## [0.3.0] - 2026-10-19

### Added

- Photos: add a photo to an item when editing it ("Add photo"), picked from your gallery without giving the app access to it. While an item is cooling off, its photo is a small thumbnail ("Tap to view"); afterwards it shows larger. Items with a photo get a small thumbnail on the wishlist. The app keeps its own smaller copy, without the original's location data.
- Share to the app: share a product from a shop app or browser and choose "Before You Buy". Quick add opens over the app you came from with the name and link filled in, and the cursor in the price. Save or close it and you're back where you were. Works without internet: the app reads only what the share contains, and removes tracking from the link.
- Item detail: tap an item on the wishlist to see your reasons first, then its price, stage, days left and how long you've wanted it. While it's cooling off, the shop link is only in the ⋮ menu ("Copy link", "Open link"); afterwards "Open link" shows on the page. "Mark as need" ends the waiting period after asking.
- Edit an item: name, price, link, need, category, notes and target date. A lower price never shortens the waiting period; a higher one can extend it, and the form says so before you save ("Waiting period extended to …").

## [0.2.0] - 2026-10-18

### Added

- Quick add: tap "Add" on Home, type a name and a price, save. Optional link, "This is a need" (needs skip the waiting period) and more details (category, notes, target date). Prices can be typed as "1.234,56" or "1,234.56". After saving, the app says how long the item waits, e.g. "Added. You can decide in 30 days."

- Wishlist: your items grouped as Ready, Needs reflection and Cooling off, soonest first, each with its price and days left. Open it from Home; "Add" is there too. Groups update by themselves when a waiting period ends or the day changes.

### Changed

- Amounts are shown in whole units, rounded: "20 €" instead of "19,99 €", "120 €" instead of "120,00 €". The exact price is still stored and shown when editing.

## [0.1.1] - 2026-10-12

### Changed

- No changes in the app. This version checks that a new release installs as an update over 0.1.0 and keeps its data.

## [0.1.0] - 2026-10-12

### Added

- Project planning: product brief, research notes, architecture plan, decision records, licence.
- App skeleton: the app installs and opens a placeholder Home screen, in English and German.
- Calm look in light and dark mode, and the building blocks every screen will use: amounts, cooling-off countdowns, section headings, empty states and equal-weight decision buttons.
- Amounts and dates follow the app language (e.g. "1.234,56 €" in German, "€1,234.56" in English). Typing an amount accepts either "1.234,56" or "1,234.56".
- The app language can be chosen per app in Android settings (Android 13 and newer).
- Signed APKs on GitHub Releases for each version, which install on the phone and update in place with data kept (`docs/releasing.md`).

[Unreleased]: https://github.com/LocNgu/before-you-buy/compare/v0.3.0...HEAD
[0.3.0]: https://github.com/LocNgu/before-you-buy/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/LocNgu/before-you-buy/compare/v0.1.1...v0.2.0
[0.1.1]: https://github.com/LocNgu/before-you-buy/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/LocNgu/before-you-buy/releases/tag/v0.1.0
