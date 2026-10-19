# ADR-0039: Reflection flow: answers kept as typed, required questions can't be skipped, back leaves

**Status**: accepted
**Type**: product
**Date**: 2026-10-19

## Context

The reflection flow (#17, brief §4.3, §6.4) asks the questions from ADR-0009, one per screen, with a progress indicator. The user "can leave anytime (answers saved per step)", and required answers are what makes an item Ready. The issue doesn't say when an answer counts as given, what "Next" does on an unanswered required question, what back does, or where the flow opens on a second visit.

Alternatives considered:

- **Storing an answer only on "Next"**: a reason typed and then left with back would be lost, although the user wrote it. A draft that is discarded on back (as in edit, ADR-0035) has the same problem, and reflection is meant to be filled in a bit at a time (ADR-0009).
- **Required questions that can be skipped**: the item could never become Ready from the flow alone, and the brief says "skippable except required ones".
- **Back steps to the previous question**: leaving from question 8 would take eight presses. Since everything is already stored, leaving costs nothing.
- **Always opening at question 1**: a user who stopped after "why" would have to pass it again to continue.

## Decision

- **Every answer is stored as it changes**, trimmed, blank answers and blank reasons left out. Leaving in any way (back, ×, "Done", the app being closed) keeps what was typed. There's no draft and nothing to discard. Storing answers never changes the item: its waiting period stays as it is.
- **The stage follows the stored answers at once**: the moment why, how often and one reason against are there, the item is reflected (`isReflected`). Answers given while cooling off make it Ready the moment the wait ends.
- **Required questions can't be skipped**: "Next" is disabled until they're answered. Optional questions show "Skip" until answered, the last question "Done". Each question says "Required" or "Optional" next to the progress. For a need nothing is required (it skips reflection, ADR-0008); its answers can still be edited.
- **Back (the system gesture or ×) leaves the flow**; the "Back" button goes to the previous question.
- **The flow opens at the first required question still unanswered**; once all are answered, at question 1, to go through the answers again ("Edit answers" on item detail).
- Reasons against are a list of fields. "Add another reason" works once every field holds a reason; a field can be removed while there's more than one. "How often" can be changed but not cleared; the optional "Upgrade or new" is cleared by tapping the chosen answer again.

## Consequences

- An item can become Ready while the flow is still open; nothing is shown about it there (no celebration, ADR-0012).
- A half-typed answer is stored as it is. There's no "cancel" for changes made to existing answers; the user edits them back.
- The decision screen (#18) and the reflection nudge (#20) open the same flow (`ItemReflection` navigation key); it continues where the required answers stop.
