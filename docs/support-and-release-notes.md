# Support info and release notes

Settings → About and licenses links to two local screens:

- **Release notes** describes features included in the installed build and shows
  its actual version name/code. The content is bundled with PsyZine; it does not
  fetch release feeds or claim a separate release has been published.
- **Support info** previews a report before the user explicitly chooses Copy or
  Share. Share opens Android's chooser without selecting a recipient.

The report has a fixed allowlist: app version name/code, application ID, flavor,
build type, Android API level, font scale and display density. It reads no
journal database, photos, app preferences, logs, accounts, source URLs, filesystem
paths or device identifiers. Unexpected metadata tokens are replaced with
“unavailable”; invalid numeric scaling values get the same readable fallback.
Copy/share failures show a message and keep the preview available.

About retains its existing GPL attribution and local license notices. Asset
reads remain off the main thread; failed notice or license reads now show a
readable error and Retry action. Coroutine cancellation is propagated.

There are no new permissions, database changes, background tasks, automatic
uploads or external service dependencies.

## Focused checks — 11 November 2026

Four JVM tests passed for the fixed report fields, rejected paths/URLs/control
characters, invalid numeric values and locale-independent formatting. The
Direct debug APK built and instrumentation sources compiled. Diff checks passed.

Navigation, clipboard/share UI, missing-handler errors, license retry behavior,
large fonts and screen-reader use have not been exercised on a device for this
batch. Compilation and formatter tests do not establish those runtime results.
