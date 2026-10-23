---
description: Monthly fun money (discretionary pool) — accrual, ledger, balance and its screen
paths:
  - "core/domain/src/**/pool/**/*"
  - "core/data/src/**/pool/**/*"
  - "app/src/main/kotlin/**/feature/money/**/*"
  - "app/src/test/kotlin/**/feature/money/**/*"
---

# Fun money (brief §4.5, ADR-0010, ADR-0014, ADR-0018, ADR-0044, ADR-0045)

## Rules (`:core:domain` `pool/`)

- `AllowanceSettings(monthlyAmount ≥ 0, accrualDay 1–28, startDate)`. `clampAccrualDay` fits stored values in; the UI offers only 1–28. `firstMonth` is the first credited month; `changed(amount, day)` keeps it (moving the start to that month's 1st if needed), so a day change before the first credit never skips or adds a month.
- `accrualsDue(settings, lastAccruedMonth, now, zone)`: every month after the last credited one whose accrual date is on or after `startDate` and not after today **in `zone`**. The first credit is the first accrual day on or after setup (the accrual day itself credits that day). `nextAccrualDate` is what the screen shows.
- `PoolEntry` is the append-only ledger: `StartingBalance` (≥ 0, once, at setup), `Accrual(month)` (≥ 0, one per month), `Adjustment` (±, optional note), `Purchase(itemId?, paid)` (#23). `change` is the signed effect; `pool` = sum of `change`. The last credited month is derived from the ledger (`lastAccruedMonth`), never stored separately. **No entry for needs, ever** (ADR-0044).
- `poolBalanceOf(entries, committed)`: `pool`, `committed`, `unallocated = pool − committed`. Until #23, committed is zero.
- `needsConfirmation(balance, change)`: a take-out that leaves `unallocated` below zero asks first; adding never does.
- `funMoneyOf(allowance, entries)` is what Home and the screen show: `NotSetUp` or `SetUp(allowance, balance, nextAccrual)`. Entries in another currency (a half-read currency change, a stray row) are relabeled to the allowance's, never hidden or thrown on.

## Storage (`:core:data` `pool/`)

- `RoomPoolRepository`: `setUp` (false if already set up; an unreadable row is replaced; starting balance entry only if > 0; credits what's due at once), `change` (credits what's due under the **old** settings first, then stores `old.changed(…)`, then credits again — one transaction), `adjust`, `accrue`. Every amount must be in the policy currency.
- Accruals are idempotent twice over: the last month is read inside the transaction, and the unique `accrual_month` index makes `INSERT OR IGNORE` drop a second credit from anyone.
- Defensive reads (ADR-0032): an unknown entry type, unreadable month or invalid amount reads as an `Adjustment` with the stored amount, so the balance stays right; an unreadable start date reads as not set up, and setting up again replaces it.

## App (`:app` `feature/money/`)

- `PoolAccrual.run()` credits what's due with the injected clock. It runs at app start (`onAppStart`), in `onAppForeground` (called from `MainActivity.onStart`: a process that lived past the accrual day) and in `NotificationWorker` (via `NotificationsEntryPoint`; cancellation is rethrown, other failures never stop notifications). A failure retries next time.
- `FunMoneyViewModel` / `FunMoneyScreen` (`FunMoney` nav key, opened from Home; #26 links here too): not set up → monthly amount, day, optional starting amount, "First added on …"; set up → the three amounts and "Next monthly amount on …", the same form without starting amount, and "Adjust the amount" with equal "Add" / "Take out" buttons. Save closes; an adjustment stays and clears its fields. Taps check `busy` on the editor itself, not only `uiState` (which may lag a tap), so a double tap never writes twice. `FunMoneyAmounts` is the three amounts for Home and this screen. Errors show once a field held text (ADR-0035); amounts read with `parseMoney` and `priceErrorMessage`.
- Labels and terms: `.claude/rules/copy-and-tone.md` (never "available" or "spend").

## Tests

- Rules: `AllowanceTest`, `PoolBalanceTest`, `FunMoneyTest` (JVM). Storage: `RoomPoolRepositoryTest` (incl. concurrent accruals), `MigrationTest` 3 → 4, the currency relabel in `RoomPolicyRepositoryTest`. App: `FunMoneyViewModelTest`, `PoolAccrualTest` (`FakePoolRepository` uses the real `accrualsDue`), `FunMoneyFlowTest` (real app; no exact amounts, they depend on today's date), `FunMoneyScreenshotTest` setting up / set up / invalid amount × en/de × light/dark. The below-zero dialog is its own window, which screenshots miss.
