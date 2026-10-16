---
description: Theme, shared components, money/date formatting and locales in the UI
paths:
  - "app/src/main/kotlin/**/ui/**/*"
  - "app/src/main/kotlin/**/feature/**/*"
  - "app/src/test/kotlin/**/ui/**/*"
  - "app/src/test/kotlin/**/feature/**/*"
  - "app/src/main/res/**/*"
  - "core/domain/src/**/money/**/*"
---

# UI basics (ADR-0027, ADR-0028, ADR-0034)

## Theme (`ui/theme/`)

- Wrap every screen, preview and screenshot in `BeforeYouBuyTheme`. Use `MaterialTheme.colorScheme`/`typography`/`shapes`; never hard-code colours or text sizes in features.
- Fixed palette, no dynamic colour. To change it, regenerate *every* role from a seed (Material "tonal spot", 2021 spec) and update `res/values*/colors.xml` (window background) to match.
- No red/orange for countdowns, prices or amounts. `colorScheme.error` is for input errors only.

## Shared components (`ui/components/`)

- `MoneyText(money)`: the only way to show an amount (whole units, rounded up). `CountdownText(daysLeft)`: "23 days left" / "Cooling-off over" (≤ 0).
- `SectionHeader` (a semantic heading), `EmptyState(title, body, action)`.
- `PrimaryButton`/`SecondaryButton` look identical on purpose; put decision pairs in `ButtonPair` so both get the same width and height. Never make the "buy" side louder (ADR-0012).
- Reuse these before writing a new one; a new shared component gets screenshot tests like the others.

## Money, dates, plurals

- Money: `Money(minorUnits, currency)` in `:core:domain`. Show it with `Money.format(locale)` / `MoneyText`, read input with `parseMoney(text, currency, locale)` and map each `MoneyParseResult` failure to a message. Never `Double`, never `String.format("%.2f")`.
- **Display rounds up, edit fields are exact, sums round once** (ADR-0034). `format` shows whole units rounded away from zero (`20 €` for 19,99 €). Prefill an input field with `Money.formatForInput(locale)` (`19,99`), never `format`, so saving an unchanged form keeps the price. Totals: `amounts.total(currency).format(locale)`, never a sum of rounded or formatted parts. Nothing is rounded when stored.
- Locale: `currentLocale()` (`ui/format/`), the configuration's locale, which follows the per-app language. Never `Locale.getDefault()` in UI code.
- Dates: `formattedDate(date)` (medium style). Day counts: `durationInDays(n)`, `daysAgo(n)`, `CountdownText`. They're all `plurals`, never "day(s)" or string concatenation.
- Every string and plural in `values/` **and** `values-de/` (ADR-0016). New languages also go into `res/xml/locales_config.xml` (per-app language setting).

## Tests

- Component screenshots: abstract `ComponentScreenshotTest` with one subclass per variant (en/de × light/dark), each with fixed `@Config(qualifiers = …)`; images are `…ComponentScreenshot<Lang><Theme>Test.<test>.png`. Add a test method per new component to the base class. Set qualifiers with `@Config`, not `RuntimeEnvironment.setQualifiers` between tests.
- Text and behaviour per language: Robolectric with `@Config(qualifiers = "de")` on the test method.
- Robolectric doesn't read `android:localeConfig`; `LocaleConfigTest` checks the resource instead. Switching the per-app language in system settings can only be checked on a device (Android 13+).
