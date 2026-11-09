# Journal display controls

The Settings screen now controls ingredient names on experience cards and quantity
details on journal timelines. Direct named ingredients can replace a definition's
name in a card summary; nested definitions keep their parent name. Journal,
calendar and search cards react to changes without changing search filters or
stored records.

Quantity details can also be shown or hidden from an experience. The choice is
global. Hidden details retain the chart, time cursor and keyboard/accessibility
cursor actions. Expanded landscape timelines omit quantity details without
changing the saved portrait preference. When the current time is visible, the
chart can show its existing per-ingestion estimates without a drag gesture.
Readout rows are lazy and support dragging from the panel.

Both new choices persist in DataStore and optional portable-backup metadata.
Old backups and missing fields preserve current choices. Preference write errors
are readable; a pending experience action rejects duplicate taps and supports
retry. Cancellation does not produce a false failure message. No database schema
or application identity change is included.

Settings also provides independent switches to hide interactions and tolerance
on experience screens. Both default to visible. Hiding the experience summaries
does not alter records, statistics, or the interaction checks during ingestion
entry. Hiding tolerance removes its inline composition and active collection.
The choices persist and travel in optional backup fields; absent fields in older
backups retain the current values. Preference failures use the existing readable
Settings error and retain previous choices.

Timeline quantity panels also offer an optional “Group by subject” view. Groups
use persistent subject identity and administration route. Peak percentages come
from the combined raw curve, independently of the chart's height normalization.
The equivalent amount is the sum of existing per-ingestion estimates, labeled
separately from the combined-peak percentage. Unknown contributions keep totals
unknown; incompatible quantity units are not added. Compatible mass equivalents
may still be shown in mg. The original per-ingestion view remains the default.
Group curves are cached on demand, and hiding quantities skips grouped readouts.

## Focused development checks — 9 November 2026

- Direct debug APK build passed; Direct and Play JVM suites passed 150 tests each.
- 27 focused Android 17 checks passed for the chart controls, card relations,
  existing cursor interactions and attachment/backup paths.
- 13 navigation/mutation checks passed, including failed preference writes,
  duplicate taps, cancellation and settings across both timeline screens.
- Ten checks passed with the emulator's actual font scale set to 2.0; its previous
  setting was restored. The long-list test uses a direct lazy-list index jump.

These are focused development results. Full regression, lint, five-variant builds
and release qualification are reserved for a later milestone.

The section-visibility follow-up passed 11 focused tests (nine preference and
two backup metadata cases), the Direct debug APK build and diff checks. These
cover persistence, partial restore, absent fields, round trips and failed writes.
Device interaction, lint and full-regression checks were not run for this batch.

The grouped quantity follow-up passed ten focused cursor tests, the Direct debug
APK build and diff checks. Cases cover combined peaks, separated ingestions,
typed identities/routes, unknown quantities and the existing individual cursor
behavior. New panel interaction and large-font device checks were not run.
