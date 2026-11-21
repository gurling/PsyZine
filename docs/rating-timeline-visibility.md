# Rating timeline visibility

Timed-rating creation and editing provide **Show in timeline**, using the
existing stored rating flag. New ratings default to shown; imported hidden
ratings retain their choice when the rating value changes. A user can explicitly
show a hidden marker again without changing its timestamp or rating value.

Overall ratings have no timestamp and therefore no timeline marker. Their forms
explain this and omit the switch; existing stored visibility metadata remains
preserved. Timed hidden ratings are labelled **Hidden from timeline** in the
experience list and remain available for editing.

The choice uses SavedState under the existing journal-generation checks. Save
continues through the existing guarded repository operations and error dialog.
The shared accessible switch is also reused by ingestion visibility controls.
No schema, backup format, rating scale or timeline calculation changes are needed.

## Focused checks — 11 November 2026

Four timeline-content JVM tests passed, including showing a hidden rating again
without changing its marker time or rating value. The Direct debug APK built and
instrumentation sources compiled. The new editor case covers retaining an
imported hidden choice through a rating-value change, then explicitly showing
the marker; that instrumentation case was compiled, not executed. Diff checks
passed.

Switch interaction, SavedState recreation, actual editor/database saves,
large-text layouts and screen-reader behavior have not been exercised on a
device for this batch.

## Overall-rating conflict recovery

If another overall rating is saved while the add form is set to Overall, the
switch remains available so the user can return to timed entry. A readable
message explains the conflict. The selected rating, timestamp and visibility
choice are retained; the existing transactional duplicate check still prevents
writing a second overall rating. The row is a single accessible switch with a
48dp minimum target and is disabled while the editor is loading or saving.

The focused `RatingConflictUiTest` simulates the availability change and checks
that timed entry remains reachable without resetting the entered rating/time.

The recovery fix passed the Direct debug APK build, instrumentation-source
compilation and diff checks on 11 November 2026. Android MCP `list_devices`
returned no attached devices, so the focused UI regression was not executed.
No JVM tests were rerun for this UI-only fix.
