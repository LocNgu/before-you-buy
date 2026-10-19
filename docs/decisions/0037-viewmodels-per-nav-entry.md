# ADR-0037: ViewModels belong to their navigation entry; per-item screens use assisted injection

**Status**: accepted
**Type**: technical
**Date**: 2026-10-18

## Context

Until now every `hiltViewModel()` was scoped to the activity: Navigation 3 entries had no ViewModel store of their own. That worked for Home and the wishlist, which exist once. Item detail and edit (#16) exist once per item: with activity scope, every detail screen would get the same ViewModel (showing the previous item while a new one loads), and ViewModels would never be cleared while the activity lives.

Alternatives considered:

- **Activity scope with a key per item** (`hiltViewModel(key = "item-$id")`): separate instances, but none is ever cleared, so each visited item keeps its flows and state for the activity's lifetime.
- **One activity-scoped ViewModel with a `load(id)` method**: shows the old item until the new one loads, and two entries on screen during a transition share one state.

## Decision

- `AppNavigation` adds `rememberViewModelStoreNavEntryDecorator()` (`androidx.lifecycle:lifecycle-viewmodel-navigation3`, Apache-2.0) after the default saveable-state decorator. Each entry gets its own ViewModel store, cleared when the entry leaves the back stack.
- Screens for one item take the id as a navigation-key argument and pass it to a `@HiltViewModel(assistedFactory = …)` ViewModel: `hiltViewModel<VM, VM.Factory> { it.create(itemId) }`. The assisted parameter is the raw `Long`, not the `ItemId` value class (Dagger sees the mangled type).
- App-wide ViewModels stay outside the entries: `QuickAddViewModel` is obtained in `AppNavigation` itself, so it is still the activity's, composed once (`.claude/rules/capture.md`).

## Consequences

- Leaving a screen clears its ViewModel; coming back starts a fresh one (state that must survive goes into the database, as before).
- A ViewModel needed by several entries must be obtained outside `NavDisplay`, like quick add, or passed down; obtaining it inside an entry now gives that entry its own instance.
