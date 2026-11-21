# Per-ingestion timeline visibility

The Time section in ingestion creation and editing provides **Show in timeline**.
New ingestions default to shown. Existing records load their stored choice,
including imported hidden values; a note-only edit preserves that choice. Draft
selection uses SavedState under the existing journal-generation checks and is
committed only by the existing guarded save.

Turning the switch off hides that ingestion's curves from the shared timeline
projection. The ingestion remains in the journal, dose totals, interaction
inputs and statistics. Subject/definition visibility settings still apply when
the switch is on. Hidden records are labelled in ingestion details so the
setting can be found and reversed.

Visible notes and ratings remain drawable when all ingestion curves are hidden.
Experience and fullscreen views now share one visible-content gate, which also
filters hidden or untimed markers before deciding whether a chart is useful.
The existing global timeline-hide control still takes precedence in experience
details, and fullscreen consumer ownership remains unchanged.

This uses the existing `isHiddenInTimeline` column and backup field. No Room
migration, backup-format change, quantity rewrite or new calculation is needed.

## Focused checks — 11 November 2026

Fourteen focused JVM cases passed across two checks: ten component-presentation
cases, one hidden-record statistics case and three visible-content gate cases.
They cover hidden plain/native ingestions retaining amounts and totals, record
deduplication, marker-only charts, hidden/untimed markers and global hiding.
The final Direct debug APK built and instrumentation sources compiled, including
the extended note-only editor preservation case. Diff checks passed.

The unchanged accounting tests were not repeated after extracting the shared
marker gate. Switch interaction, SavedState recreation, actual editor/database
saves and marker-only rendering have not been exercised on a device for this
batch. Instrumentation sources were compiled, not executed.
