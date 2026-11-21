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

## Rating draft recovery

Add and edit rating forms now retain the selected rating, local time and timeline
visibility in SavedStateHandle. New-rating drafts also retain the overall-rating
choice. Restoring a draft keeps the existing journal-generation check, and the
existing overall-rating conflict recovery remains available. Editing an overall
rating still keeps its stored overall status.

Time changes update synchronously before Save, and saves capture a single form
snapshot. Busy or unavailable forms reject further changes and duplicate saves.
Unchanged timed ratings keep their original recorded instant through the existing
preservation rule. No schema, backup or counting rules change.

`RatingDraftRecoveryTest` adds isolated in-memory cases for both forms, including
reconstruction, overall-to-timed selection, immediate time selection followed by
Save, duplicate taps, and retaining an unedited recorded instant.

On 21 November 2026 the Direct Debug APK build, instrumentation-source compilation
and diff check passed. The two new cases were compiled, not executed: Android MCP
reported no connected devices. Actual process recreation, overall-conflict
recovery and time-picker behavior still require device checks.

## Ingestion timing controls

The ingestion editor now updates start, end and point/range mode synchronously,
so an immediate Save sees the selected timing. Moving start past end still moves
end forward by 30 elapsed minutes; moving end before start still moves start back
by 30 elapsed minutes. The paired adjustment is retained together in SavedState.

Timing recovery runs after the existing generation and definition checks. Original
loaded timestamps remain separate, preserving unchanged instants when editing.
The later ingestion draft increment below extends recovery to the remaining form
fields. No schema or backup format changes.

`IngestionTimingRecoveryTest` covers range reconstruction, paired adjustments,
immediate time/mode selection followed by Save, duplicate taps and precise dose
retention using an isolated in-memory Room database.

On 21 November 2026, the Direct Debug APK build, instrumentation-source compilation
and diff check passed. The two new cases were compiled, not executed. Android MCP
found no connected devices; actual time-picker interaction, process recreation
and DST behavior remain runtime checks.

## Remaining ingestion draft fields

The ingestion editor now retains note, dose and uncertainty text, known/estimate
flags, units, consumer and selected experience alongside timing and visibility.
Recovery preserves blank fields and exact numeric text under the journal-generation
check. Selecting a custom unit verifies its identity in a guarded read before
changing the draft; restoration rechecks the selected unit's fingerprint and any
native definition composition fingerprint. Missing or changed selections require
reopening instead of silently switching units. Original missing-unit records stay
readable under the existing save validation.

The screen now uses the shared recovery gate so unavailable reasons remain visible
after the error dialog closes. Two added in-memory cases cover scalar draft
reconstruction with a valid unit, precise save values, and a changed selected-unit
fingerprint preventing recovery. No Room schema or backup format changes.

On 21 November 2026 the Direct Debug APK build, instrumentation-source compilation
and diff check passed. The two additional cases were compiled, not executed.
Android MCP reported no connected devices; process recreation, unit-selection
interaction and error recovery still require device verification.

## Unit changes while saving

The ingestion editor passes its captured unit fingerprint to the repository. The
repository compares it with the current unit inside the existing save transaction,
after acquiring the journal mutation lock and before updating the ingestion.
A changed unit rejects the save with a readable explanation and leaves the draft
open. The current unit remains available in the picker for explicit reselection
and review. Native-definition graph validation also remains active.

For records opened with an existing unit, its fingerprint is retained across
recreation as well. A changed original unit cannot silently refresh an old draft's
meaning during recovery. No schema or backup changes are required.

A queued-save regression holds the mutation lock, changes the unit before the
save acquires it, checks that the ingestion remains unchanged, then explicitly
reselects the updated unit and retries successfully.

On 21 November 2026 the Direct Debug APK build, instrumentation-source compilation
and diff check passed. The new queued-save case was compiled, not executed.
Android MCP reported no connected devices; transactional contention and picker
reselection still require device execution.

## Recovering explicitly reviewed definition changes

When an ingestion draft contains an explicitly selected definition, recovery now
validates that selected record and composition fingerprint. The original record's
graph token is used only when no explicit selection exists. This lets a reviewed,
reselected composition survive recreation, while a later unreviewed graph change
still rejects the draft. Final transactional unit and graph checks remain active.

Two additional isolated in-memory cases exercise composition-only changes with
an unchanged parent unit: reviewed selection recovery followed by Save, and a
subsequent composition change rejecting recovery without changing the ingestion.
No schema, backup or quantity calculation changes are required.

On 21 November 2026 the Direct Debug APK build, instrumentation-source compilation
and diff check passed. The two new native-graph cases were compiled, not executed.
Android MCP reported no connected devices; reviewed-selection recreation and
unreviewed-change rejection still require device execution.
