# Changelog

All notable user-visible changes. Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Versions follow [SemVer](https://semver.org/).

## [Unreleased]

### Added

- Home answers what you can decide on and what you're considering. "To decide" lists items that are ready and items whose waiting period is over but still need your reasons; tap one to decide. "Considering" shows how many items are on your wishlist and which waiting period ends next ("Road bike · 12 days left"), with a link to the wishlist. History opens from the top bar; back always returns to Home.
- Fun money: set a monthly amount for the things you want from Home ("Set up"). It's added by itself on the day of the month you choose, starting with the next one; add what you already have as a starting amount. Home shows "Fun money for wants", "Set aside for wants" and "Not yet set aside". Missed months are added when you next open the app or by the daily check, never twice. "Change or adjust" changes the amount or day (from the next monthly amount on) or the starting amount, or adds or takes out an amount with an optional note when the number and your real money drift apart; taking out more than is left asks first. It's only a number in the app: no real money moves. Needs never use fun money: buying one leaves it untouched.
- Savings per item: on a want, "Set aside" puts money from your fun money toward it ("650 € of 1,600 € set aside · 950 € to go"), from the day you add it; "Take back" returns it. Quick amounts: 50, 100 and the rest. You can only set aside what isn't set aside yet, and never more than the price. Not buying returns what was set aside; buying spends the price you paid from your fun money, and the app asks first if that would leave less than nothing (as shown). Marking a want as a need gives its money back after asking. "Wants need their price set aside" on the fun money screen (on by default): a want is ready to decide on once its price is set aside (in whole amounts, as shown); switch it off, or don't set up fun money, to go by the waiting period and your reasons alone. The wishlist shows how far each want is; Home's "Saving for" lists up to three wants with their progress; the decision screen shows what's set aside.

## [0.4.0] - 2026-10-21

### Added

- Reflection: tap "Start" on an item's "Why do you want this?" card to answer a few questions, one at a time: why you want it, how often you'll use it and what speaks against it (add as many reasons as you like), then five optional ones you can skip. Answers are kept as you type, so you can leave at any point and continue later. Once the first three are answered, the item can become Ready when its waiting period ends. Change them anytime with "Edit answers"; that never changes the waiting period. Needs don't need reasons, but you can add some with "Add your reasons".
- Decide: tap "Decide" on an item that's ready (or a need) to see your reasons, how long you've wanted it and what else the money could go to, then choose Buy, "Don't buy — keep 1,200 €" or Wait. Buy asks for the price you paid and the date. Wait lets you look again in 7, 14 or 30 days or on a date you pick; until then the item is under "Looking again later" on the wishlist. Not buying asks an optional "Why not?" and moves the item to "Decided against".
- Decide early: before an item is ready, "Decide now" offers not buying, or "Decide early…", which shows the days left and your reasons against again and asks why you want to buy it now before you can confirm. Early purchases are recorded as such.
- History: open it from Home. "Bought" lists what you bought with the price you paid, the date, how long you considered it and whether you bought it early or as a need; "Decided against" lists what you didn't buy with its price, date and your "Why not?", and the total at the top: "You decided against 3,420 € of purchases". Filter both by this month, this year or all. Tap an entry to see your reasons at the time. "Want it again?" puts a decided-against item back on your wishlist as a new item that waits again from today; the entry stays in your history. "Delete from history" in the ⋮ menu removes an entry after asking.
- Notifications, at most one a day: when an item's waiting period is over ("You've wanted Camera for 32 days. Your waiting period is over.") with "Still want it", which opens the decision, and "Let it go", which moves it to "Decided against" without opening the app. Two days after you add something, a one-time reminder to note why you want it, if you haven't yet. Several items on the same day come as one notification. On Android 13 and later the app asks once, after your first item, whether it may notify you; everything works without. "Waiting periods" and "Reflection reminders" can each be switched off in the system's notification settings.

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
