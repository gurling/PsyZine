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

## Editing custom substances

The existing custom-substance editor now loads its record and generation in one
guarded session. SavedStateHandle retains name, units and description; blank
values remain blank after recovery instead of being replaced by database values.
The shared gate hides the form until loading succeeds and supplies a reason and
Back action when loading fails or the journal changes. Missing required fields
use the same guidance as creation.

Save captures one draft snapshot, checks the target inside the guarded mutation,
and navigates only after success. Delete uses the same session and target check.
Stale actions cannot change reused IDs after journal replacement. Ordinary write
failures keep the draft open. No schemas, attachments or backup formats change.

`CustomSubstanceEditorRecoveryTest` adds isolated in-memory tests for draft
reconstruction, captured save values, duplicate taps and reused IDs in a
replacement journal.

On 20 November 2026 the Direct Debug APK build, instrumentation-source compilation
and diff check passed. The two new tests were compiled, not executed: Android MCP
reported no connected devices. Actual process recreation, error recovery, deletion
and keyboard/font behavior remain device checks.

## Timed-note draft recovery

Add and edit timed-note forms now retain text, selected local time, color and
timeline visibility in SavedStateHandle, under the existing journal-generation
check. Blank text and an explicit hidden marker survive recovery. New-note color
and visibility defaults are applied only when there is no recovered value.

Time selection updates synchronously, so Save reads the most recent choice. Each
save captures the complete form before starting its mutation; busy forms reject
additional field changes and duplicate submissions. Note-only edits keep the
existing instant-preservation rule, including repeated local times at DST changes.
No schema, backup, notification or palette behavior changes.

`TimedNoteDraftRecoveryTest` covers both forms with isolated in-memory Room data:
reconstructed draft fields, immediate time selection followed by Save, duplicate
taps, and an edited note retaining its unmodified recorded instant.

On 20 November 2026, the Direct Debug APK build, instrumentation-source compilation
and diff check passed. The two new cases were compiled, not executed. Android MCP
found no connected device; actual process recreation, form interaction and DST
behavior still require runtime verification.
