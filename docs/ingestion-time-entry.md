# Ingestion time entry

The final ingestion form offers **Recent times** and **Calendar** modes. The
choice is shared with Settings → **Use recent-time presets** and persists across
new forms and app restarts. Existing installations retain calendar mode until
they choose otherwise. Switching modes does not reset the entered timestamps,
notes, title, consumer or experience selection. Existing ingestion editors keep
their calendar controls.

Recent times provides Now, 5/15/30 minutes ago, 1/2 hours ago and In 30 minutes.
Each tap captures an exact instant from the injected clock. Saving later uses
that captured instant rather than recalculating the offset. The displayed date
and time confirm the selection. Database storage keeps its existing second
precision. Point and range entry remain available; ranges
have separate Start and End preset groups.

The existing endpoint adjustments retain valid ordering: moving start past end
can move end to thirty minutes after start; choosing end before start moves
start to thirty minutes before end. The form now explains these adjustments.
Changing end so that start moves also refreshes the suggested experience and
automatic title. Exact captured instants survive ambiguous local DST times.

Time updates cancel superseded experience lookups. Save remains unavailable
during the latest lookup and after a failed update until a time is chosen
successfully. Preference failures show a readable error and preserve entered
times. Changing only end retains a manually selected experience when start is
unchanged and its lookup is current. Cancellation remains cancellation.

Format-2 portable backups include the nullable `recentTimePresets` display
option. Backups without it retain the current choice. No Room migration, journal
rewrite or new permission is needed.

## Focused checks — 10 November 2026

Twenty-one JVM tests passed: sixteen preference tests, two backup-metadata tests
and three preset-time tests. They cover persisted mode selection, failed writes,
absent backup fields, fixed capture times, year rollover and both Prague DST
transitions. After the final ViewModel adjustment, the Direct debug APK and
instrumentation sources compiled successfully. The unchanged JVM tests were not
repeated for that adjustment. Diff checks passed.

Preset tapping, mode switching, range adjustments, lookup cancellation/recovery
and the extended photo-inclusive backup test have not been exercised on a
device for this batch.
