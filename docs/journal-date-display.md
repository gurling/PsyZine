# Journal date display

The Journal toolbar's timer control and Settings → **Show relative dates** save
the same preference. It survives navigation and app restart and applies to shared
experience cards, including search and statistics links. Calendar browsing keeps
calendar dates. The existing main Journal default remains calendar dates.

Relative labels distinguish past and future entries, including **in less than a
minute**, and use singular units where appropriate. These are elapsed-time
labels; long ranges retain approximate week/month/year formatting. They do not
change stored timestamps, sorting, calendar boundaries or statistics periods.

One shared presentation clock updates visible relative labels once per minute.
It refreshes immediately on resume, suspends while the activity is stopped, and
does not run periodic updates in calendar-date mode. Standalone previews and
component hosts use the same lifecycle-aware clock as a fallback.

Failed Journal-toggle writes show a readable error, retain the stored choice and
allow retry. Duplicate taps are ignored while that write is pending. Settings
uses its existing preference-error snackbar.

Format-2 portable backups include the nullable `relativeDates` display option.
Older backups without the field retain the current selection. Existing schema
history, journal records, photos and other display choices are unchanged.

## Focused checks — 10 November 2026

Twenty-one JVM tests passed: fifteen preference tests, two backup-metadata tests
and four relative-date tests. They cover persistence, failed writes, absent
backup fields, past/future direction, exact minute/hour/day boundaries, year
rollover and Prague DST transitions. The Direct debug APK built successfully;
instrumentation sources, including the extended photo-backup preference case,
compiled. Diff checks passed. Device interaction, restart, lifecycle timing and
the photo-inclusive backup test were not executed for this batch.
