---
description: Priorities and the head-to-head — rules in :core:domain, screens and view models in :app
paths:
  - "core/domain/src/**/priorities/**/*"
  - "app/src/main/kotlin/**/feature/priorities/**/*"
  - "app/src/test/kotlin/**/feature/priorities/**/*"
---

# Priorities (brief §4.7, ADR-0011, ADR-0047)

## Rules (`:core:domain` `priorities/`)

- `Item.prioritySlot` is the rank (1 first). `prioritiesOf(items)`: active **wants** with a slot, by slot, then `addedAt`, then id. A need or ended item with a stored slot isn't a priority. `canBePriority`: active want without a slot.
- Slots: `MIN_PRIORITY_SLOTS`..`MAX_PRIORITY_SLOTS` (1–5), `DEFAULT_PRIORITY_SLOTS` (3), stored in `Policy.prioritySlots`; `clampPrioritySlots` for stored values.
- `promote(item, active, slots)`: `Promoted(rank, ranking)` with a free slot (lowest free rank), `Compare(HeadToHead)` with full slots, `NotEligible` otherwise. Full slots are only reachable through a won comparison (ADR-0011).
- `HeadToHead`: challenger vs the full priorities, from the lowest rank up. `opponent` / `opponentRank` / `comparisonsLeft`; `choose(winner)` accepts only the challenger or the opponent and throws after `finished`. `outcome` (null until finished): `before`/`after` ids and `lines` (`OutcomeLine(item, before, after)`, by new rank, then moved back, then the challenger if it stayed out). No win: nothing changes (`changed == false`). Otherwise the challenger takes the rank of the last one it beat and whatever no longer fits the slots goes back.
- A slot also starts the wait (ADR-0049): `withSlots(active, changes, at, zone)` applies slot changes, starting the wait of a want that gets a slot and pausing one that loses it (`startWait` / `pauseWait`, `.claude/rules/domain.md`); moving rank keeps it running. Store its items, never bare slots, when a slot is gained or lost.
- Writes are rankings: `slotChanges(active, ranking)` (only changed slots), `compactedSlots(active)` (ranks 1…n again, slots off non-wants), `demotedBySlots(active, slots)` (who goes back on a reduction), `rankingWithout(active, id)` ("Remove from priorities"). `changeKind` to a need clears the slot.
- Wishlist and Home views: `prioritiesIn(groups)` / `wishlistSectionsOf(groups)` (`wishlist/Wishlist.kt`, `.claude/rules/wishlist.md`), `HomeOverview.priorities` (`.claude/rules/home.md`).

## Storage (`:core:data`)

- `ItemRepository.setPriorities(expected, ranking, at, zone)`: one transaction, compare-and-set on the stored ranking; refuses (false) a non-want or ended id, a duplicate, or more than the policy's slots. Writes `withSlots` with targeted `ItemDao.setSlotAndWait` (`setSlotsAndWaits`, ADR-0032).
- `RoomItemRepository.update` and `recordDecision` compact the ranks in their transaction (a priority bought, rejected or made a need) with slot-only writes: every priority keeps its slot, so no wait starts or pauses. "Wait" keeps the rank.
- `PolicyRepository.changePrioritySlots(slots, expected, at, zone)`: compare-and-set on the priorities the dialog listed from; stores the count and demotes `demotedBySlots` in one transaction, their waits paused, false if they changed. Compaction in `update`/`recordDecision` runs only when the item held a slot. `policy.priority_slots` since schema version 6.

## App (`:app` `feature/priorities/`)

- `makePriority(id, items, policies, clock)` / `removePriority(id, items, clock)` (`PriorityEvent.kt`) are shared by item detail and the wishlist (each view model runs one priority write at a time, so a double tap doesn't report its own change as a conflict); they return a `PriorityEvent` (`Promoted`, `Removed`, `Compare` → open `HeadToHead`, `ChangedMeanwhile`), shown with `priorityMessage`.
- `HeadToHeadViewModel` (assisted id, `HeadToHead` nav key, the injected `Clock` for the write): reads active items, answers, savings and slots once, then plays the `HeadToHead` locally and stores the outcome once at the end with `setPriorities(outcome.before, outcome.after)`; on false `changedMeanwhile`. `choose(winner, opponent)` ignores a tap for a comparison already answered. A slot freed meanwhile promotes without a comparison; an item that can't be a priority is `gone`. Back leaves without writing.
- `HeadToHeadContent` (stateless, screenshots): "Which would you rather have?", two `OutlinedCard`s side by side (rank or "Not a priority yet", name, why / how often / reasons against or "No answer yet", price, `SavingsProgressBar` with fun money; never a photo), the two names as a `ButtonPair` of equal buttons; the summary with `outcomeText` lines and "Done".
- `PrioritySlotsDialog(current, active, onSave, onDismiss)` with `PrioritySlotsChoice` (radio 1–5, then the priorities that go back). Save is the confirmation. The wishlist opens it; #26's Settings will reuse it.

## Tests

- Rules: `PrioritiesTest`, `HeadToHeadTest` (JVM). Storage: `RoomPrioritiesTest`, `MigrationTest` 5 → 6 and 6 → 7. View models: `HeadToHeadViewModelTest`, priority cases in `WishlistViewModelTest`, `ItemDetailViewModelTest`, `ItemEditViewModelTest` (`FakeItemRepository.setPriorities` with the real checks, `withSlots` and compaction, `conflictOnNextPriorities`, `priorityWrites`; `FakePolicyRepository.items` for the demotion). Screenshots: `HeadToHeadScreenshotTest` (comparison with and without fun money, summaries won/lost, changed meanwhile, slots dialog content) × en/de × light/dark.
