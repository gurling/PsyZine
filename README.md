# PsyZine for Android

PsyZine is an independent GPL-3.0-or-later journal based on PsychonautWiki Journal and PsyLog. It keeps the existing ingestion workflow, offline reference library, search, calendar, favorites, timed notes, ratings, timelines, custom substances/units, and statistics. PsyZine adds 13 persistent terminal palettes, private experience photos, photo-inclusive portable backups, and PsyLog’s active-experience notification and home-screen widget.

The PsyLog port is pinned to [`0c29a70fa1ced6106c8379b2c02541699d61f966`](https://codeberg.org/PsyLog/PsyLog-android/commit/0c29a70fa1ced6106c8379b2c02541699d61f966). The PsychonautWiki Journal baseline is [`8224449a62e26ffd448aade0a7079b005fdd9d37`](https://github.com/isaakhanimann/psychonautwiki-journal-android/tree/8224449a62e26ffd448aade0a7079b005fdd9d37). Features were ported individually. PsyLog’s unmerged delayed-release PR #6 is excluded.

## Install testing builds

The default Obtainium testing channel is **Direct Compatible Release**: a non-debuggable, minified build that keeps package `app.psyzine.debug` and the retained local signing identity. The current candidate is `1.0.3-compat.4` / code `4`, asset `PsyZine-Direct-Compatible-Release-v1.0.3-compat.4.apk`. Check [GitHub pre-releases](https://github.com/gurling/PsyZine/releases) for published builds.

[<img src="docs/images/get-it-on-obtainium.png" alt="Get it on Obtainium" height="80">](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22app.psyzine.debug%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fgurling%2FPsyZine%22%2C%22author%22%3A%22gurling%22%2C%22name%22%3A%22PsyZine%22%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Atrue%2C%5C%22filterReleaseTitlesByRegEx%5C%22%3A%5C%22%5EPsyZine%20Compatibility%20%5C%22%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22%5EPsyZine-Direct-Compatible-Release-v%5B0-9%5D%2B%5C%5C%5C%5C.%5B0-9%5D%2B%5C%5C%5C%5C.%5B0-9%5D%2B-compat%5C%5C%5C%5C.%5B0-9%5D%2B%5C%5C%5C%5C.apk%24%5C%22%2C%5C%22versionDetection%5C%22%3Afalse%2C%5C%22releaseTitleAsVersion%5C%22%3Afalse%7D%22%7D)

[Add PsyZine to Obtainium](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22app.psyzine.debug%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fgurling%2FPsyZine%22%2C%22author%22%3A%22gurling%22%2C%22name%22%3A%22PsyZine%22%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Atrue%2C%5C%22filterReleaseTitlesByRegEx%5C%22%3A%5C%22%5EPsyZine%20Compatibility%20%5C%22%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22%5EPsyZine-Direct-Compatible-Release-v%5B0-9%5D%2B%5C%5C%5C%5C.%5B0-9%5D%2B%5C%5C%5C%5C.%5B0-9%5D%2B-compat%5C%5C%5C%5C.%5B0-9%5D%2B%5C%5C%5C%5C.apk%24%5C%22%2C%5C%22versionDetection%5C%22%3Afalse%2C%5C%22releaseTitleAsVersion%5C%22%3Afalse%7D%22%7D) imports the compatibility channel. It enables pre-releases, tracks release tags with **Version detection** disabled, filters release titles with `^PsyZine Compatibility `, and filters APKs with `^PsyZine-Direct-Compatible-Release-v[0-9]+\.[0-9]+\.[0-9]+-compat\.[0-9]+\.apk$`. Review these settings on an existing Obtainium entry too; an imported source cannot repair a certificate mismatch. See [Obtainium tracking](https://wiki.obtainium.imranr.dev/app_tracking/) and [configuration](config/obtainium.json).

The channel is compatible with the recovered certificate `7a8752f2571342ec6b9b0bb767e0b880e682ca35006fff8f01b4891d0bbd86b5`. Historical CI releases used other certificates and cannot update an installation signed with this key. Preserve those installations and their data; do not uninstall or clear data to bypass rejection. The other four testing variants remain available for isolated testing. Agent-run phone tests remain **Direct Debug only**. A compatibility upgrade on the phone through Obtainium requires separate explicit approval and owner-supervised data verification.

## Build

Use JDK 17, Android SDK platform 35, build tools 35.0.0, and the checked-in Gradle 8.14 wrapper. AGP remains 8.7.3, Kotlin 2.0.20, Compose BOM 2025.01.00, Room 2.6.1, and Hilt 2.52. New runtime dependencies are Glance 1.1.1, Coil Compose 3.0.4 without network fetchers, ExifInterface 1.4.2, and Lifecycle Runtime Compose 2.8.7. Coil is pinned to the Kotlin 2.0.20 integration target; changing it requires checking the resolved Kotlin/Compose graph again.

```bash
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
bash tools/build-apks.sh
./gradlew :app:connectedDirectDebugAndroidTest
```

The direct release installs as `app.psyzine`, direct debug as `app.psyzine.debug`, Play release as `app.psyzine.play`, and Play debug as `app.psyzine.play.debug`. All variants use the retained internal Kotlin namespace and Room schema path. Versions come from [version.properties](version.properties), currently `1.0.3` / code `4`. Direct Compatible Release also uses `app.psyzine.debug`; there is no Play compatibility variant. Keep the direct ID and independent signing certificate stable for upgrades. Never install a different signing key over an existing journal.

Source, issue and sharing links default to `https://github.com/gurling/PsyZine` through generated BuildConfig. A fork can override them with `-PpsyzineSourceUrl=https://github.com/OWNER/REPOSITORY`; an explicitly blank property shows a development notice in About. No upstream support address is presented as PsyZine support. The retained donation page explicitly supports the original author.

Optional Nix shells, adapted from PsyLog’s pinned flake, provide JDK 17 and SDK 35: `nix develop` for Android Studio/emulator/scrcpy/adb-sync, or `nix develop .#ci` for the smaller build shell. Use `./gradlew` in either shell. Nix evaluation has not been run in this environment.

## Journal, themes, and photos

Settings → Appearance applies a preview palette immediately and saves it in the existing preferences DataStore. Follow system uses Catppuccin Latte/Mocha; named variants remain fixed. Headings use restrained monospace, while journal and editor text remain proportional. Chart and domain colors resolve the original stored enum identities against the active palette.

Photos belong to experiences. The editor supports the device camera, Photo Picker, and image files, up to 20 images per batch with no fixed total per experience. Images are copied privately, normalized for rotation/mirroring, resized to a maximum 2560-pixel long edge, and re-encoded without image location metadata. Opaque photos use JPEG quality 85 and transparent photos use PNG. Failed images have retry/remove controls; completed images remain staged. Save commits entry fields and attachment records together after durable file promotion. Discard preserves saved attachments. Draft recovery and startup reconciliation handle interrupted operations. See [data migration and backups](docs/data-migration.md).

## Active experience

An active experience is the most recent entry, by sort date and ID, with an ingestion after the previous 12-hour boundary and at or before now. Future-only entries are excluded. Shared calculation supplies the timeline and per-ingestion name, dose, route, color, and estimated phase to notifications and widgets. Partial reference duration data falls back to an estimated dotted total when a more specific timeline cannot be drawn. Existing saved height preferences are retained; new installations default to independent substance heights.

The ongoing notification has a compact summary, expanded timeline and ingestion rows, an elapsed-time chronometer, and a tap destination. One update job refreshes approximately every 30 seconds while active and also reacts to journal and preference changes. It starts from a visible activity after onboarding and permission, resumes on app launch, and stops when no qualifying entry remains or notifications are disabled. Android may suspend/stop work; the app does not promise exact background scheduling. Lock-screen details are redacted by default. Settings also controls widget details and opens Android’s notification settings.

The Glance widget is resizable, uses the selected PsyZine palette, supports an empty/error state, and opens the corresponding entry. It observes fresh data inside active Glance sessions, receives active-service updates, and has Android’s normal 30-minute periodic update when the service is off. Lists and bitmap allocations are bounded; additional records remain accessible in the full journal. Phases and timelines are reference estimates, not measurements of a person’s condition.

## Verification and release

[Feature parity checklist](docs/psylog-parity.md) records all verified PsyLog behavior and the defects corrected during the port. [Verification](docs/verification.md) separates passing host/source checks from device checks still required. The audit patch passed two independent clean local five-variant builds, with 56 tests in each of three JVM suites and zero-error lint for all five variants. Local signed upgrades passed code1 → code3 → code4 on API26 and minified code2 → code3 → code4 on API35. [Merged audit CI](https://github.com/gurling/PsyZine/actions/runs/38407068240) passed the build and six ordinary emulator suites. Protected release verification and owner-supervised Obtainium upgrades are separate checks. Physical-camera and full manual acceptance remain outstanding.

CI builds both distributions, checks permissions and IDs, runs unit tests/lint, and runs Android instrumentation on API 26, 29, 30, 32, 33, and 35. Dispatch the optional newer-API job for 36/37 when the selected AOSP image is available. Physical camera, process-death, reboot, TalkBack/large-text/keyboard, and tablet checks remain manual acceptance steps. Synthetic palette captures are produced by instrumentation for visual review; [screenshot instructions](docs/screenshots.md) describe release listing captures.

`tools/build-apks.sh` builds/tests/lints five testing variants and verifies pinned certificates, versions, non-debuggable release manifests, signatures, alignment and checksums. Existing retained signing is required by default. Untrusted CI explicitly uses disposable signing for unpublished checks; its outputs cannot pass publication gates. Compatibility publication is an explicitly requested workflow dispatch on `main`, protected by the `compatibility` environment, two independent clean builds, the complete emulator matrix, consecutive signed-upgrade checks and historical version-code checks. Read [build and publication](docs/build-publication.md) for credentials, approval boundaries and current verification gaps.

Production publication remains separate and requires the existing `release` environment credentials plus `CONTENT_RIGHTS_VERIFIED=true`. Its workflow excludes pre-release tags and enforces the package certificate policy. A different certificate for an existing package requires a separately approved migration or rotation. No private key is generated, uploaded or rotated by this remediation.

## License and privacy

Copyright © 2022–2025 Isaak Hanimann and upstream contributors; © 2025–2026 PsyLog contributors. PsyZine modifications are dated 11 October 2026. PsyZine is free software under GNU GPL version 3 or, at your option, a later version, and is distributed without any warranty. See [COPYING](COPYING), [NOTICE](NOTICE.md), and [third-party notices](THIRD_PARTY_NOTICES.md). Full GPL, palette, dependency, and privacy notices are packaged offline in Settings → About and licenses.

The application has no analytics, ads, accounts, cloud synchronization, or network fetcher. Database/photos/staging are excluded from Android cloud backup and device transfer; preferences remain eligible, including entry-workflow timestamps. Explicit backups may be written to the provider you select and are not encrypted by PsyZine. External reference links open only when tapped. See [privacy policy](PRIVACY.md). Reference content has separate provenance from PsychonautWiki, TripSit, and DIZ/saferparty.ch.
