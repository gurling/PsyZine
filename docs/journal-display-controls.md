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
