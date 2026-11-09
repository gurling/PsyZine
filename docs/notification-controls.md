# Live notification controls

The live notification provides **Stop automatic notification**. This action saves
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
record into the notification's originating experience. Manual per-experience
notification selection remains pending.

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
