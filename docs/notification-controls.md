# Live notification controls

The live notification provides **Stop notification**. This action saves
the existing notification-enable setting as false before removing the foreground
notification. The automatic coordinator observes that choice; re-enable
**Ongoing notification** in Settings to resume automatic presentation.

The action uses an explicit, immutable PendingIntent targeting the existing
non-exported service. Duplicate requests share a pending save. A failed save
retains the choice and presents a notification with a readable error and a retry
action. The normal update loop retains that error until a successful retry.
Stopping checks the latest service start and current preference before removal,
preserving the existing protection against concurrent service starts.

Journal records, widget privacy, and widget updates remain independent. The
existing permission/channel/foreground restrictions still apply. No new
permission, exported component, database schema or backup format is introduced.

The notification also offers **Log ingestion**, using a separate immutable
activity PendingIntent bound to the originating experience ID and journal
generation. The activity and navigation model validate that origin before
opening the existing ingestion workflow. Stale cloned/last-ingestion time
presets are reset before a new form opens; a failed preference write produces a
readable dialog and retains the journal. An already-open ingestion workflow is
kept in focus without resetting its presets or discarding its draft.

This action opens the standard form: its entered time and normal experience
selection determine where the ingestion is saved. It does not force the new
record into the notification's originating experience.

## Select a specific experience

Experience details provide **Show this entry in live notification** and, while
selected, **Stop live notification**. Selection is saved locally with the entry
ID and journal generation. It can follow a remaining estimated timeline beyond
the automatic mode's twelve-hour ingestion window. Future-only entries, missing
timelines, expired estimates and stale/deleted entries cannot be selected.
Expiration uses rendered curve ends, excluding graph padding and minimum axis
width; it is an estimate, not a claim that effects have ended.

The manual selection overrides notifications only. Widgets continue selecting
the automatic recent experience. The prior automatic-enable choice is retained:
when a selection expires or becomes invalid, automatic selection resumes only
if that choice is enabled. Stopping disables automatic notification and clears
the manual selection atomically. Settings also provides **Use automatic
selection** and **Stop live notification** while a manual target is present.

Blocked notifications, unavailable timelines, failed preference writes and
foreground-start exceptions show readable errors. Busy state rejects repeated
screen taps. A saved selection identifies the requested entry; it does not
prove Android has displayed the notification. Targets are excluded from portable
backups and cleared when presentation settings are restored. Generation checks
prevent restored IDs from inheriting an old selection, and expiry cleanup cannot
clear a newer selection.

## Focused development checks — 9 November 2026

Ten preference tests and the Direct debug APK build passed. The added test checks
persisted notification disabling, widget-setting isolation and failed-write
retention. Diff checks passed. Notification taps, retry UI and concurrent service
starts have not been exercised on a device for this increment. Lint and the full
regression matrix were not run.

The Log ingestion follow-up passed 11 focused preference tests, the Direct debug
APK build and diff checks. The added case covers atomic time-preset reset and
failed-write retention. Notification tapping, cold/warm navigation, draft
preservation and restore races have not been exercised on a device for this
increment; source implementation and compilation do not establish those results.

## Manual-selection checks — 10 November 2026

Seventeen focused JVM tests passed: fourteen preference tests and three timeline
eligibility tests. They cover failed selection/stop writes, reentry, conditional
expiry cleanup, automatic-mode and restore resets, widget-setting isolation,
future/unknown timelines, exact curve-end boundaries and estimates longer than
twelve hours. The Direct debug APK and instrumentation sources compiled, and
diff checks passed. Manual notification selection, permission/channel errors,
service startup and expiry have not been exercised on a device for this batch.
