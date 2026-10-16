# ADR-0027: Money formatting and input live in the domain; input accepts either decimal separator

**Status**: amended by ADR-0034 (formatting shows whole units, rounded)
**Type**: technical
**Date**: 2026-10-10

## Context

Prices are shown everywhere and typed into every capture form. Issue #10 left open whether formatting sits in `:core:domain` (pure) or `:app` (locale-aware). Typed amounts are ambiguous: German users type `12,50`, English users `12.50`, and many Android number keyboards only offer `.`, so a German user may type `12.50` too. A strict locale-only parser rejects valid input; a fully lenient one misreads `1.234` (thousands in German, a decimal in English).

## Decision

- `Money` (`Long` minor units + ISO 4217 code, ADR-0018), `Money.format(locale)` and `parseMoney(text, currency, locale)` live in `:core:domain` with JVM tests. They take the `Locale` as a parameter. The UI passes the configuration's locale (the per-app language), never `Locale.getDefault()`.
- Formatting always shows the currency's minor-unit digits (`1.200,00 €`), via `NumberFormat.getCurrencyInstance(locale)` with the currency set.
- Parsing accepts `.` and `,` as the decimal separator in every locale:
  - When both appear, the later one is the decimal separator.
  - A separator that appears more than once groups digits.
  - A single separator is the decimal separator, unless exactly three digits follow **and** either it isn't the locale's decimal separator or the currency has no minor units. Then it groups: `1.234` → 1234 in German, `1,234` → 1234 in English, `1,234` → ¥1234 anywhere.
  - Spaces and apostrophes may group digits. One number uses one kind of grouping separator, and groups after the first need three digits.
  - The currency code or the locale's sign may come before or after the number, never in the middle.
  - Negative amounts are invalid. More decimals than the currency has are rejected, never rounded.
- Failures are typed (`Empty`, `Invalid`, `TooManyDecimals`, `TooLarge`) so fields can explain them.

## Consequences

- One tested place for money text; screens stay thin.
- The one ambiguous case (`1.234` / `1,234`) follows the locale. In the "wrong" locale it reads as too many decimals and is rejected rather than misread.
- On devices `NumberFormat` is ICU-backed and in tests it's the JDK's CLDR data. Both use CLDR, so output matches (incl. the no-break space in `1.234,56 €`), but a future ICU/CLDR update could change spacing. Compare amounts, not formatted strings, in logic.
- Negative input (e.g. allowance corrections) needs an explicit extension later.
