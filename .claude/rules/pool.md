---
description: Monthly fun money (discretionary pool) — accrual, ledger, balance and its screen
paths:
  - "core/domain/src/**/pool/**/*"
  - "core/data/src/**/pool/**/*"
  - "app/src/main/kotlin/**/feature/money/**/*"
  - "app/src/test/kotlin/**/feature/money/**/*"
---

# Fun money and savings per item (brief §4.5, §4.6, ADR-0010, ADR-0014, ADR-0018, ADR-0044, ADR-0045, ADR-0046)

## Rules (`:core:domain` `pool/`)

- `AllowanceSettings(monthlyAmount ≥ 0, accrualDay 1–28, startDate, wantsNeedFunding = true)`. `wantsNeedFunding` is the "Wants need their price set aside" switch (ADR-0046). `clampAccrualDay` fits stored values in; the UI offers only 1–28. `firstMonth` is the first credited month; `changed(amount, day)` keeps it (moving the start to that month's 1st if needed), so a day change before the first credit never skips or adds a month.
- `accrualsDue(settings, lastAccruedMonth, now, zone)`: every month after the last credited one whose accrual date is on or after `startDate` and not after today **in `zone`**. The first credit is the first accrual day on or after setup (the accrual day itself credits that day). `nextAccrualDate` is what the screen shows.
- `PoolEntry` is the ledger: `StartingBalance` (≥ 0, at most one; a setting, replaced when edited, ADR-0045), `Accrual(month)` (≥ 0, one per month), `Adjustment` (±, optional note), `Purchase(itemId?, paid)` (a want bought while fun money is set up, `poolPurchaseOf`). `change` is the signed effect; `pool` = sum of `change`. The last credited month is derived from the ledger (`lastAccruedMonth`), never stored separately. **No entry for needs, ever** (ADR-0044).
- `poolBalanceOf(entries, committed)`: `pool`, `committed`, `unallocated = pool − committed`, the one place the balance is computed; entries in another currency are relabeled to `committed`'s. `committed` is `Savings.committed`.
- `needsConfirmation(balance, change)`: a take-out that leaves `unallocated` below zero asks first; adding never does. Editing the starting amount never asks (ADR-0045).
- `funMoneyOf(allowance, entries, savings)` is what Home and the screen show: `NotSetUp` or `SetUp(allowance, balance, nextAccrual, startingBalance)`; the balance comes from `poolBalanceOf`. Entries in another currency (a half-read currency change, a stray row) are relabeled to the allowance's, never hidden or thrown on.

## Savings per item (`pool/Savings.kt`, ADR-0046)

- `Allocation(itemId, amount, at)`: + sets aside, − takes back; append-only. `savingsOf(allowance, allocations, active)` → `Savings(setUp, required, byItem)`: only **active wants** hold money, so a decision (reject, buy) releases it without a write; amounts relabeled to the item's currency; a total ≤ 0 counts as nothing. `required` = fun money set up **and** `wantsNeedFunding`. `Savings.NONE` without fun money.
- `Savings.fundingOf(item)` → `Funding(allocated, required)`, what `stageOf` / `isDueForDecision` / `applyDecision` take; `isFunded` = need, or not required, or `coversPrice(allocated, price)`: the amounts **as shown** in whole units (`Money.shownWholeUnits`, the rounding `format` uses), so the screen never shows the full price set aside on an item that isn't funded. `progressOf(item)` → `SavingsProgress(allocated, price)` (`complete` = `coversPrice`; `toGo` exact, 0 once complete; `shownToGo` = the difference of the shown amounts, what "… to go" says; `fraction` 0–1), null for needs.
- `Item.canBeSavedFor`: an active priority (ADR-0049); item detail's "Set aside" uses it. `setAside(item, amount, balance?, at)`: refused without fun money, for ended items, needs, wants without a slot (`NotAPriority`, ADR-0049; what such a want already holds stays, and `takeBack` still works), ≤ 0, or more than `unallocated` (`MoreThanNotSetAside`); **not** capped at the price (ADR-0046). `takeBack(item, amount, allocated, at)`: at most what it holds. Both move `withinShown(amount, limit)`: an amount that reads the same as the limit in whole units moves exactly the limit. Both return `AllocationResult`. The sheet's numbers: `setAsideLimit(balance, currency)` (`unallocated` ≥ 0; 0 disables "Set aside"), `restToSetAside(progress, limit)` (up to the price, at most the limit; null once covered), `quickAmounts(limit)` (`QUICK_AMOUNTS` 50 / 100 in whole units via `Money.ofWholeUnits`, those that fit) and `checkAmount(amount, limit)` (`AmountProblem`, via `withinShown`).
- `poolPurchaseOf(item, decision, funMoneySetUp)`: a want's purchase → `Purchase(actual price)`; null for a need, a non-purchase or without fun money. `leftAfterPurchase(balance, allocated, paid)` = `unallocated + allocated − paid`; `purchaseNeedsConfirmation` is true when `paid > allocated` and that **shows** below zero (`shownWholeUnits() < 0`, i.e. −1 € or less; a few cents below read "0 €" and don't ask). The dialog shows the same number. The decision and edit screens never skip their question while the money is still loading. One entry, no extra adjustment.
- `releaseWhenMarkedAsNeed(current, edited, allocated)`: the take-back written with an edit that makes a want with savings a need (ADR-0044), dated `markedAsNeedAt`.

## Storage (`:core:data` `pool/`)

- `RoomPoolRepository`: `allocations` (every row, decided items' too; `savingsOf` filters), `setAside` / `takeBack` (read the item, allowance, entries, allocations and active items and write in one transaction, so two at once can't use the same money), `setUp` (false if already set up; an unreadable row is replaced; starting balance entry only if > 0; credits what's due at once), `change` (credits what's due under the **old** settings first, then stores `old.changed(…)`, credits again, and replaces the starting amount keeping its first date — one transaction; the starting-amount delete is the only delete in the ledger), `adjust`, `accrue`. Every amount must be in the policy currency.
- Accruals are idempotent twice over: the last month is read inside the transaction, and the unique `accrual_month` index makes `INSERT OR IGNORE` drop a second credit from anyone.
- Defensive reads (ADR-0032): an unknown entry type, unreadable month or invalid amount reads as an `Adjustment` with the stored amount, so the balance stays right; an unreadable allowance (start date, currency, negative amount) reads as not set up, and setting up again replaces it; a ledger or allocation row in an unknown currency is left out.
- Purchases and need releases are written by `RoomItemRepository` with the decision / edit (`.claude/rules/data.md`).

## App (`:app` `feature/money/`)

- `PoolAccrual.run()` credits what's due with the injected clock. It runs at app start (`onAppStart`), in `onAppForeground` (called from `MainActivity.onStart`: a process that lived past the accrual day) and in `NotificationWorker` (via `NotificationsEntryPoint`; cancellation is rethrown, other failures never stop notifications). A failure retries next time.
- `observeSavings(items, pool)` (`SavingsFlow.kt`): the one `Savings` flow every screen with a stage or the fun money uses. `SavingsProgressBar(progress)`: bar + "650 € of 1,600 € set aside · 950 € to go" (item detail, Home, decision).
- `FunMoneyViewModel` / `FunMoneyScreen` (`FunMoney` nav key, opened from Home and from item detail's "Set up fun money"; #26 links here too): not set up → monthly amount, day, optional starting amount, the "Wants need their price set aside" switch (whole row toggles), "First added on …"; set up → the three amounts and "Next monthly amount on …", the same form (starting amount prefilled and editable), and "Adjust the amount" with equal "Add" / "Take out" buttons. Save closes; an adjustment stays and clears its fields. Taps check `busy` and read the form on the editor itself, not `uiState` (which may lag a tap), so a double tap never writes twice and a save never uses stale text. `FunMoneyAmounts` is the three amounts for Home and this screen. Errors show once a field held text (ADR-0035); amounts read with `parseMoney` and `priceErrorMessage`.
- Labels and terms: `.claude/rules/copy-and-tone.md` (never "available" or "spend").

## Tests

- Rules: `AllowanceTest`, `PoolBalanceTest`, `FunMoneyTest`, `SavingsTest` (JVM). Storage: `RoomPoolRepositoryTest` (incl. concurrent accruals), `RoomSavingsTest` (limits, concurrent set-asides, reject/buy release, buy above/below, needs, mark as need, delete), `MigrationTest` 3 → 4 and 4 → 5, the currency relabel in `RoomPolicyRepositoryTest`. App: `FunMoneyViewModelTest`, `PoolAccrualTest` (`FakePoolRepository(items)` uses the real `accrualsDue`, `setAside` and `takeBack`), `NotificationWorkerTest` (the real worker credits once, raw SQL on the app's database), `FunMoneyFlowTest` (real app; no exact amounts, they depend on today's date), `FunMoneyScreenshotTest` setting up / set up / invalid amount × en/de × light/dark. The below-zero dialog is its own window, which screenshots miss.
