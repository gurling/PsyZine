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

Manual per-experience notification selection and an Add ingestion notification
action remain pending.

## Focused development checks — 9 November 2026

Ten preference tests and the Direct debug APK build passed. The added test checks
persisted notification disabling, widget-setting isolation and failed-write
retention. Diff checks passed. Notification taps, retry UI and concurrent service
starts have not been exercised on a device for this increment. Lint and the full
regression matrix were not run.
