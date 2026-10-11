# PsyZine for Android

PsyZine is an independent GPL-3.0-or-later journal based on PsychonautWiki Journal and PsyLog. It keeps the existing ingestion workflow, offline reference library, search, calendar, favorites, timed notes, ratings, timelines, custom substances/units, and statistics. PsyZine adds 13 persistent terminal palettes, private experience photos, photo-inclusive portable backups, and PsyLog’s active-experience notification and home-screen widget.

The PsyLog port is pinned to [`0c29a70fa1ced6106c8379b2c02541699d61f966`](https://codeberg.org/PsyLog/PsyLog-android/commit/0c29a70fa1ced6106c8379b2c02541699d61f966). The PsychonautWiki Journal baseline is [`8224449a62e26ffd448aade0a7079b005fdd9d37`](https://github.com/isaakhanimann/psychonautwiki-journal-android/tree/8224449a62e26ffd448aade0a7079b005fdd9d37). Features were ported individually. PsyLog’s unmerged delayed-release PR #6 is excluded.

## Install testing builds

Download a development-signed APK from [GitHub pre-releases](https://github.com/gurling/PsyZine/releases). For physical-device testing, install only **Direct Debug** (`app.psyzine.debug`); keep the other variants for build verification and emulator testing.

[Add PsyZine to Obtainium](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22app.psyzine.debug%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fgurling%2FPsyZine%22%2C%22author%22%3A%22gurling%22%2C%22name%22%3A%22PsyZine%22%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Atrue%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22%5EPsyZine-Direct-Debug-.%2A%5C%5C%5C%5C.apk%24%5C%22%2C%5C%22versionDetection%5C%22%3Afalse%7D%22%7D) imports the GitHub source with pre-releases enabled and filters APKs to Direct Debug. It tracks release tags because these testing APKs retain version `1.0.0`. Review the import in Obtainium before installing. Manual setup: add `https://github.com/gurling/PsyZine`, enable **Include pre-releases**, set **Filter APKs by regular expression** to `^PsyZine-Direct-Debug-.*\.apk$`, and disable **Version detection**.

These are testing builds, not production releases. CI signing certificates may differ between runs and from local builds; matching package IDs alone do not make an update compatible. Preserve a backup before changing signing identities. A rejected update must not be worked around by clearing journal data.

## Build

Use JDK 17, Android SDK platform 35, build tools 35.0.0, and the checked-in Gradle 8.14 wrapper. AGP remains 8.7.3, Kotlin 2.0.20, Compose BOM 2025.01.00, Room 2.6.1, and Hilt 2.52. New runtime dependencies are Glance 1.1.1, Coil Compose 3.0.4 without network fetchers, ExifInterface 1.4.2, and Lifecycle Runtime Compose 2.8.7. Coil is pinned to the Kotlin 2.0.20 integration target; changing it requires checking the resolved Kotlin/Compose graph again.

```bash
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
bash tools/build-apks.sh
./gradlew :app:connectedDirectDebugAndroidTest
```

The direct release installs as `app.psyzine`, direct debug as `app.psyzine.debug`, Play release as `app.psyzine.play`, and Play debug as `app.psyzine.play.debug`. All variants use the retained internal Kotlin namespace and Room schema path. Versions start at `1.0.0` / code `1`. Keep the direct ID and independent signing certificate stable for upgrades. Never install a different signing key over an existing journal.

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

[Feature parity checklist](docs/psylog-parity.md) records all verified PsyLog behavior and the defects corrected during the port. [Verification](docs/verification.md) separates passing host/source checks from device checks still required. All four APK variants have passed actual local Gradle builds, both JVM suites and zero-error lint. Local signed APKs installed and launched. Six CI emulator suites passed; all 23 physical API 37 tests passed after disabling test-window launch animations. Physical-camera and full manual acceptance are still outstanding.

CI builds both distributions, checks permissions and IDs, runs unit tests/lint, and runs Android instrumentation on API 26, 29, 30, 32, 33, and 35. Dispatch the optional newer-API job for 36/37 when the selected AOSP image is available. Physical camera, process-death, reboot, TalkBack/large-text/keyboard, and tablet checks remain manual acceptance steps. Synthetic palette captures are produced by instrumentation for visual review; [screenshot instructions](docs/screenshots.md) describe release listing captures.

Ordinary builds produce signed testing APKs; release versions are labeled `1.0.0-dev` unless production signing is configured. `tools/build-apks.sh` runs actual Gradle builds/tests/lint and creates verified APKs, sizes and checksums only on success. Read [build and publication results](docs/build-publication.md) for signing setup, expected paths, current execution evidence and GitHub publication behavior. Development and production certificates cannot upgrade each other in place.

The signed-release workflow uses only independent `PSYZINE_KEYSTORE_BASE64`, `PSYZINE_KEY_ALIAS`, `PSYZINE_STORE_PASSWORD`, and `PSYZINE_KEY_PASSWORD` secrets in the `release` environment. It signs through Gradle, verifies signatures and alignment with Android SDK tools, and packages corresponding source and SHA-256 checksums. Keep a secure offline copy of the signing key. Signing/publication requires `CONTENT_RIGHTS_VERIFIED=true` after the bundled reference-content redistribution review; attribution alone does not establish permission. Production publication remains gated. The separate `Android checks` workflow publishes a development-signed testing pre-release only after successful builds and the complete required emulator matrix. See [Actions](https://github.com/gurling/PsyZine/actions) and [downloadable releases](https://github.com/gurling/PsyZine/releases) for actual status. Source has been pushed to the specified SSH origin; development keys and APK binaries remain outside version control.

## License and privacy

Copyright © 2022–2025 Isaak Hanimann and upstream contributors; © 2025–2026 PsyLog contributors. PsyZine modifications are dated 11 October 2026. PsyZine is free software under GNU GPL version 3 or, at your option, a later version, and is distributed without any warranty. See [COPYING](COPYING), [NOTICE](NOTICE.md), and [third-party notices](THIRD_PARTY_NOTICES.md). Full GPL, palette, dependency, and privacy notices are packaged offline in Settings → About and licenses.

The application has no analytics, ads, accounts, cloud synchronization, or network fetcher. Database/photos/staging are excluded from Android cloud backup and device transfer; preferences remain eligible, including entry-workflow timestamps. Explicit backups may be written to the provider you select and are not encrypted by PsyZine. External reference links open only when tapped. See [privacy policy](PRIVACY.md). Reference content has separate provenance from PsychonautWiki, TripSit, and DIZ/saferparty.ch.
