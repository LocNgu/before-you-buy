# ADR-0034: Amounts are shown in whole units, rounded up; storage and input stay exact

**Status**: accepted
**Type**: product
**Date**: 2026-10-16

## Context

ADR-0027 showed every amount with the currency's minor-unit digits (`19,99 €`, `1.200,00 €`). That has two problems:

- **It keeps the shop's framing.** Shops price at `.99` because of the *left-digit effect*: people read `19,99 €` as "19-something" and judge it as noticeably cheaper than `20 €` (Thomas & Morwitz 2005, "Penny Wise and Pound Foolish: The Left-Digit Effect in Price Cognition", *Journal of Consumer Research*). An app meant to help people buy consciously shouldn't pass that framing on (ADR-0014).
- **It looks like a budgeting tool.** For deciding whether to buy something, cents are noise, and `120,00 €` reads heavier than `120 €`.

Alternatives considered (issue #62):

- **Always show cents (ADR-0027):** uniform, but it keeps the `.99` effect.
- **Show cents only when non-zero:** mixed styles in lists, and it keeps the `.99` effect.
- **Round half-up:** can make a price look cheaper (`19,49 €` → `19 €`).
- **Round when storing:** throws the exact amount away for good. Prices from shops and shared links arrive with cents anyway, and storing them costs nothing.

## Decision

- **Display shows whole units, rounded up.** `Money.format(locale)` (and so `MoneyText`) rounds away from zero to whole units, with the locale's grouping and sign: `19,99 €` → `20 €`, `19,01 €` → `20 €`, `120,00 €` → `120 €`, `€1,235`. The app never shows an amount smaller than the real one. This applies everywhere an amount is displayed: list rows, detail, history (incl. the actual price paid) and cooling-off tier limits. Currencies without minor units (JPY) look as before.
- **Sums are exact and rounded once.** Add the stored minor units (`Iterable<Money>.total(currency)`) and format the total. The shown total can be smaller than the rounded parts added up (`0,01 € + 0,01 €` shows `1 €`, not `2 €`), never larger than the next whole unit above the exact sum.
- **Storage stays exact** (ADR-0018 unchanged): `Long` minor units, nothing rounded on save.
- **Input stays exact.** `parseMoney` is unchanged (ADR-0027). Edit fields prefill the exact stored amount with `Money.formatForInput(locale)` (`19,99`, `120`; no sign, no grouping), which `parseMoney` reads back to the same amount, so saving an unchanged form never changes a price.

This supersedes ADR-0027's clause "Formatting always shows the currency's minor-unit digits". The rest of ADR-0027 stays.

## Consequences

- A price that changes by a few cents may show the same; a price edit from `19,99` to `20,00` looks like no change. Cooling-off and every other rule still use the exact amount.
- Rounding up overstates an amount by less than one unit. That's harmless for prices. Amounts where overstating could mislead (money saved towards an item, pool balances in M2, #22/#23) need their own look when those screens are built; this ADR doesn't decide them.
- Never add up formatted or rounded amounts; tests and screens compare `Money`, not strings.
- Display and input now use different formatters. Input fields must never use `format`, or saving would round the price.
