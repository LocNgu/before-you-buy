---
description: Reflection flow — steps and answer rules in :core:domain, the flow screen and view model in :app
paths:
  - "core/domain/src/**/reflection/**/*"
  - "app/src/main/kotlin/**/feature/reflection/**/*"
  - "app/src/test/kotlin/**/feature/reflection/**/*"
---

# Reflection flow (brief §4.3, §6.4, ADR-0009, ADR-0012, ADR-0039)

## Rules (`:core:domain` `reflection/`)

- `ReflectionStep` is the question order, one per screen: `WHY`, `USAGE`, `REASONS_AGAINST` (required), then `PROBLEM_SOLVED`, `CURRENT_ALTERNATIVE`, `REPLACES`, `WHY_NOW`, `UPGRADE_OR_NEW` (optional). `next`/`previous` are null at the ends.
- `ReflectionForm` holds the answers as typed; `reasonsAgainst` is a field per reason and never empty (one empty field without reasons). `withReason`, `withNewReason` (only if `canAddReason`: no blank field), `withoutReason` (the only field is emptied, not removed), `withUpgradeOrNewTapped` (tapping the chosen answer clears it).
- `reflectionFormOf(reflection)` fills it from what's stored; `reflectionOf(form)` is what gets stored: trimmed, blank answers null, blank reasons dropped.
- `isAnswered(step, reflection)`: non-blank text, a choice, or one non-blank reason against. `isReflected` (`lifecycle/`) is defined by it over the required steps: one definition of "reflected", never a second one.
- `isRequired(step, kind)`: required steps, for wants only; a need's answers are all optional (ADR-0008). `reflectionPageOf(step, form, kind)` is what a step shows: `required`, `canGoOn` and `onward` (`NEXT`, `SKIP` for an unanswered optional step, `DONE` on the last). `startStep(reflection, kind)`: the first required step unanswered, else `WHY`.

## Screen and view model (`:app` `feature/reflection/`)

- `ReflectionViewModel` (assisted id, ADR-0037) loads item and answers together, fills the form once, then **writes every change** with `ItemRepository.setReflection` (ADR-0039): one write at a time under a `Mutex`, in `NonCancellable`, looping until the newest answers are stored, so back (which clears the view model at once) never loses the last keystrokes. Stops once the item is ended (it keeps the answers it was decided with) or `setReflection` says it's deleted. `finish()` (last "Done", ×) runs once: it writes the newest answers, then emits `finished`. It never writes the item: the waiting period can't change here.
- Opening for an ended or deleted item shows `gone` and writes nothing; an item ended meanwhile turns `gone` too.
- `ReflectionContent` is stateless (screenshots) and renders `ReflectionUiState.page`. Top bar: × ("Close") and the item's name. "Question N of 8" + `LinearProgressIndicator` + "Required"/"Optional". Bottom bar (above the keyboard): "Back" from question 2, then the `onward` button ("Next", disabled while a required question is unanswered; "Skip"; "Done"). System back leaves the flow; it's not "previous question".
- Text answers: multi-line field labelled "Your answer". Choices: radio rows (`selectable` + `selectableGroup`); "How often" can't be cleared, "Upgrade or new" clears when the chosen answer is tapped again. Reasons: a field per reason ("Reason N", × to remove when there are several, which clears focus so the cursor can't land in the reason that moved up), "Add another reason" focuses the new field.
- Navigation: `ItemReflection(itemId)` in `AppNavigation`; `onDone` pops only if the flow is on top. Item detail opens it from the prompt card ("Start"/"Continue"), once reflected from "Edit answers", and for a need without answers from a quiet "Add your reasons". The decision screen ("Answer the questions") opens the same key; nudges (#20) will too.
- The choice labels (`usageLabel`, `upgradeOrNewLabel`) live in `ReflectionLabels.kt`, shared with item detail.
- Copy: gentle hints, no judgement, nothing on completion (ADR-0012, `copy-and-tone.md`). Hints for "why" don't ask the user to imagine using it (research §1: imagining increases desire).

## Tests

- Rules: `ReflectionStepsTest` (JVM). View model: `ReflectionViewModelTest` with `FakeItemRepository` (`setReflection`, `reflectionWrites`, `reflectionGate`, `delete`) and `MutableClock`; it drives `ItemDetailViewModel` on the same fake to check the stage change. A view model cleared mid-write: create it through a `ViewModelStore` and `clear()` the store. Real app: `ReflectionFlowTest` (Hilt, Room). Screenshots: `ReflectionScreenshotTest` per question type × en/de.
