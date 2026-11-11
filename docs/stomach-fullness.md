# Stomach-fullness recording

Oral ingestion creation and editing now offer an optional stomach-fullness
selector: Not recorded, Empty, Quarter full, Half full, Full and Very full.
New records default to Not recorded (`null`), which remains distinct from Empty.
No value is inferred from earlier ingestions.

The selected value uses the existing ingestion column and existing oral timeline
projection. This change adds no delay calculation and does not move the recorded
ingestion time or change its interval length. The selector follows the recorded
ingestion route; it does not introduce separate choices for individual ingredients.

Editors load the existing value and preserve it during note-only edits. A user
can explicitly clear it by choosing Not recorded. Previously imported values on
other routes remain visible and editable, with an explanation that they do not
affect that route's timeline. Recorded non-null values also appear in ingestion
details.

The fullness choice is retained in SavedState under the existing journal-session
generation checks. An unsupported recovered choice shows an actionable error
instead of silently becoming Empty or Not recorded. Changes use the existing
guarded save and error handling; selecting a value alone does not write a record.

Existing Room storage, schema history and backup wire values remain unchanged.
No migration, preference default, dose/uncertainty rewrite or photo change is
required.

## Focused checks — 11 November 2026

Twelve JVM tests passed: three selection/serialization tests and nine existing
component-presentation tests. They cover unrecorded versus Empty, every saved
choice, readable rejection of unsupported recovered values, wire round trips,
exact dose/uncertainty retention and the existing oral-delay projection rules.
The Direct debug APK built and instrumentation sources compiled, including the
extended note-only editor preservation case. Diff checks passed.

The selector, SavedState recreation, actual editor/database saves and backup
round trips have not been exercised on a device for this batch. The extended
instrumentation case was compiled, not executed.
