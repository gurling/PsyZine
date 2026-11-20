# Journal editor recovery

The add/edit rating and timed-note wrappers now use one editor-session gate.
Loading keeps the existing progress dialog without exposing uninitialized form
actions. A failed load or restore-invalidated session shows **Entry unavailable**
and a Back action instead of inert Save/Delete controls.

The unavailable reason is retained separately from the dismissible error dialog.
Closing the dialog therefore keeps the explanation and Back action available.
A stale-session rejection during save also marks the form unavailable. Choosing
Back does not delete a record or apply the stale draft to a replacement journal.

Ordinary save failures remain recoverable in place: the valid draft stays open,
and the error retains its **Could not save changes** title. Repository mutation
checks, generation tokens, cancellation and data formats remain unchanged.

`JournalEditorRecoveryUiTest` covers failed-load dismissal, generation replacement
using an isolated in-memory Room database, and a failed save retaining controls.
It does not seed or clear the application's journal.

## Focused checks — 11 November 2026

The Direct debug APK build, instrumentation-source compilation and diff checks
passed. The three recovery regressions were compiled, not executed: Android MCP
`list_devices` returned no attached devices. No JVM tests or broad regression
suite were run for this batch. Actual error-dialog dismissal, Back navigation,
restore invalidation and failed-save draft retention remain runtime checks.

## Additional callers — 20 November 2026

Substance colors and both custom-substance creation flows now use the same gate.
Failed loading or journal replacement hides their mutation controls and leaves a
readable reason plus Back navigation. Ordinary save failures keep the form open.
Substance-color names wrap in the space beside the picker, and rows use stable
substance-name keys. No storage formats or mutation contracts change.

The Direct debug APK build and instrumentation-source compilation passed for
this increment, as did `git diff --check`. Existing recovery tests were compiled
but not executed. Android MCP reported no connected devices; Back navigation,
restore recovery and large-font wrapping still require runtime verification.

## Custom-substance draft recovery

New custom-substance forms now store name, units and description in SavedStateHandle,
with the existing journal-generation token. Reopening the composition initializes
the search suggestion only once, including when the user deliberately clears the
name. A restored draft cannot save into a replacement journal. Save captures all
three fields together before launching the mutation. Missing name and units show
field-level explanations; description remains optional.

Two additional instrumentation cases cover draft reconstruction, stale-draft
rejection and required-field explanations using an isolated in-memory database.

The Direct debug APK and instrumentation Kotlin compilation passed on
20 November 2026, along with `git diff --check`. The two new cases were compiled,
not executed: Android MCP found no connected devices. Actual process recreation,
field-error accessibility and keyboard/font behavior remain manual checks.
