# Rating and timed-note editor recovery

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
