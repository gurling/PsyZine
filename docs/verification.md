# PsyZine verification and acceptance

Record date: 11 October 2026. PsyLog target: `0c29a70fa1ced6106c8379b2c02541699d61f966`. See [the complete parity checklist](psylog-parity.md) for F01–F22 and D01–D12, and [data migration](data-migration.md) for storage/restore behavior. Unmerged PsyLog PR #6 is excluded.

The host/source results below were obtained during implementation, before the later build/signing/publication changes. They are not an APK build. See [the current actual Gradle and GitHub execution record](build-publication.md).

## Earlier host-only implementation results

| Check | Result and scope |
| --- | --- |
| Main Kotlin compilation | All 296 main Kotlin files compile using Kotlin 2.0.20, JVM 17, the Compose compiler and serialization plugins, SDK 35 `android.jar`, and a manually assembled cached dependency classpath. Generated resource-name and BuildConfig stubs were used. This is source compilation, not an AGP APK build or Hilt component-generation check. |
| JVM unit tests | 35 tests pass under JUnit 4.13.2 with real JSON-java, covering existing parsing/date/route/number behavior, 13 theme roles and domain contrast on raised surfaces, persistent preferences, timeline phases/fallbacks, newer JSON conversion, and backup path/stream limits. They were compiled/run directly; Gradle unit tasks could not start at that earlier stage. |
| Room annotation processing | Actual Room 2.6.1 KAPT processing passes; schema 8, DAO implementations, database implementation and automatic migrations 1→7 are generated. KAPT reports its normal Kotlin-2 fallback to language 1.9. No destructive migration fallback was added. |
| SQLite upgrade validation | All seven starting schemas pass: generated automatic migration SQL plus manual 7→8 preserve original columns/IDs, match version-8 table shapes, cascade attachment deletion, and prevent reuse of deleted experience IDs. This uses host SQLite, not Android's SQLite or MigrationTestHelper execution. |
| Original schemas | Schemas 1–7 are byte-for-byte unchanged from PsyZine HEAD and structurally identical to pinned PsyLog. |
| Android test source compilation | All 23 instrumentation test methods compile with the Compose compiler and Android/test dependencies. At that earlier stage none had executed on Android. |
| Resource compilation | Android build-tools 35.0.0 `aapt2 compile` succeeds for all app resources. No resource linking or merged-manifest validation is claimed. All 22 source XML files parse. |
| Privacy source checks | Source manifest declares only foreground-service, special-use foreground-service and notification permissions. FileProvider is non-exported and exposes only `staging/camera/`. Legacy, Android 12+ cloud and device-transfer rules exclude databases, attachments and staging while retaining DataStore preferences. Transitive merged permissions remain a build check. |
| Reference data | Bundled JSON exactly matches pinned PsyLog: 289 substances / 21 categories. Only mirror URLs changed from the original baseline; no delayed-release administration data was added. |
| Build configuration | Gradle 8.14 distribution checksum verified against the downloaded official ZIP; both workflow YAML files parse. At that earlier stage Nix, Gradle dependency resolution, actual variant builds and workflows had not run. |
| Patch hygiene | `git diff --check` passes. Existing uncommitted work was preserved; no reset was used. This historical source-check stage preceded signing and publication work. |

The direct compiler checks used temporary tools/classpaths in `/tmp`; they do not certify Gradle's resolved dependency graph. Coil 3.0.4 and Glance 1.1.1 APIs compile with the retained Kotlin/Compose toolchain. The standard checks below remain authoritative for dependency resolution, AGP/Hilt integration and installation.

## Actual Android verification — 11 October 2026

The prior Gradle/ADB socket restriction no longer reproduces. Actual wrapper builds now pass for Direct Debug, Direct Release, Play Debug and Play Release, including resource linking, Hilt/Room processing and release minification. Both flavor unit suites pass (35 each); all four lint tasks have zero errors. SDK tools verify ZIP integrity, IDs, min/target SDK, signatures, alignment and forbidden permissions. All four local development APKs installed and launched on a Pixel.

All 23 instrumentation tests passed on each CI emulator at API 26/29/30/32/33/35 in [run 38136387190](https://github.com/gurling/PsyZine/actions/runs/38136387190). Its separate build job failed and prevented publication; check the latest complete run and actual release links in [build-publication.md](build-publication.md). The first idle Pixel/API 37 run passed 22/23 because an injected editor tap was rejected while the launch window was translucent. The isolated editor test passed with window animations disabled, and the full Gradle suite then passed 23/23 with zero skips after adding `testOptions.animationsDisabled = true`. Migration, attachment, backup, presentation and UI tests all passed. Phone animation settings were restored automatically. Thirteen synthetic palette PNGs were generated, without claiming complete visual acceptance.

Nix evaluation, API 36 emulation, physical-camera acquisition, reboot/process recovery, tablet/accessibility and genuine private-version export fixtures remain unverified. Production signing credentials and reference-content redistribution clearance remain outstanding. Testing pre-releases are development signed and do not claim production readiness. See the manual matrix below before claiming complete feature parity.

## Standard build and automated checks

On a host allowing Gradle/adb sockets, use JDK 17 and SDK 35:

```bash
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
./gradlew --version
./gradlew :app:assembleDirectDebug :app:testDirectDebugUnitTest :app:lintDirectDebug
./gradlew :app:assembleDirectRelease :app:lintDirectRelease
./gradlew :app:assemblePlayDebug :app:testPlayDebugUnitTest :app:lintPlayDebug
./gradlew :app:assemblePlayRelease :app:lintPlayRelease
./gradlew :app:dependencies --configuration directDebugRuntimeClasspath
./gradlew :app:dependencyInsight --dependency coil-compose --configuration directDebugRuntimeClasspath
./gradlew :app:dependencyInsight --dependency glance-appwidget --configuration directDebugRuntimeClasspath
./gradlew :app:connectedDirectDebugAndroidTest
git diff --check
```

Inspect generated Room schema 8 and merged manifests. Check that no network fetcher, analytics, broad storage, camera or location permission arrives transitively. Run dependency insight for Kotlin/Compose if the resolved versions differ from the pins. Install only on an isolated test device:

```bash
adb install -r app/build/outputs/apk/direct/debug/app-direct-debug.apk
apkanalyzer manifest application-id app/build/outputs/apk/direct/debug/app-direct-debug.apk
apkanalyzer manifest permissions app/build/outputs/apk/direct/debug/app-direct-debug.apk
```

Expected debug identity is `app.psyzine.debug`; Play debug is `app.psyzine.play.debug`. Release IDs omit `.debug`. Provider authority follows each variant's actual application ID. The `.github/workflows/android-checks.yml` workflow runs these variant checks and AOSP instrumentation on API 26, 29, 30, 32, 33 and 35. Dispatch its optional newer-API job separately for 36 and 37 when AOSP x86_64 images are available. A missing image is a recorded matrix gap, not a pass.

## Automated Android coverage

| Test class | Acceptance covered |
| --- | --- |
| `ExampleInstrumentedTest` | Installed application identity matches generated BuildConfig for each variant. |
| `DatabaseParityTest` | Every schema 1–7 migrates to 8 with every original field/ID; deleted IDs are not reused; attachment order/FK cascade; insert conflicts retain photos; editable fields preserve favorites/dates and clear location coordinates; complete child deletion; future-only/12-hour active-query boundaries; replacement rollback; stale editor generation rejection and transactional write rollback. |
| `AttachmentBackupIntegrationTest` | All eight EXIF orientations, pixel layout and GPS/XMP removal; transparency/resize/thumb regeneration; corrupt/oversized inputs, cancellation and disk errors; full photo backup round trip and preferences; missing/traversal/extra/checksum failures preserve the current journal; reconciliation preserves references/active drafts; staged removal/discard/stale-save behavior. |
| `PresentationIntegrationTest` | Private/public notification versions and chronometer; distinct experience/generation PendingIntents; stable generation initialization for upgraded journals and invalidation on replacement; all 13 renderer palettes and bitmap bounds. |
| `PsyZineUiRegressionTest` | Theme selection/disabled preview behavior; accessible themed date selection retaining local time; viewer initial-page clamp/paging/close; synthetic captures for all 13 palettes. |
| `WidgetParityTest` | Glance row displays the expected formatted substance/dose/route/phase. |
| `ExperienceEditorUiTest` | Failed save retains draft and does not navigate; discard confirmation/keep-editing; successful save dismisses only after commit. |

## Manual acceptance matrix

Use synthetic journals/photos only. Retain a pristine export and record APK SHA, SDK/device/launcher, theme and result for every run. Repeat core checks on API 26/29/30/32/33/35/36/37, an AOSP device without Play services, a physical camera and a tablet. Check the minified release separately.

1. **Existing journal:** create through ingestion, combine nearby ingestions, choose another entry, clone, edit doses/custom units/substances/companions, timed notes/ratings, favorites, location, search, calendar, charts/statistics and reference links. Confirm existing data/preferences remain intact after migration and relaunch; inspect every tab and dialog offline. No delayed-release control or behavior should appear.
2. **Themes and accessibility:** apply all 13 themes and Follow system. Relaunch/reboot, change Android appearance while a fixed theme is active, switch themes during unsaved edits and navigation, and inspect date/time dialogs, charts, selections, disabled/error states and external presentations. Review normal text at 4.5:1 and meaningful controls/large text at 3:1. Use TalkBack, keyboard navigation, 200% font size and tablet layouts; verify labels, focus, clipping and 48dp controls. Host contrast checks do not replace composed-screen review.
3. **Photos:** camera capture/cancel, missing camera handler, Photo Picker multiple selection, explicit SAF files and automatic fallback on older/AOSP devices. Enforce 20 per batch even when SAF returns more; add further batches. Test JPEG/PNG/WebP, rotated/mirrored images, transparency, locally available HEIC/AVIF, unsupported/corrupt/oversized files and low storage. Successful selections survive a failed image, and retry/remove works. Delete originals, use airplane mode, relaunch/reboot and reopen photos. Kill the process while the external camera is open and during processing; recover the draft. Confirm discard leaves saved photos, save errors keep draft/no navigation, viewer paging/pinch/pan/close, and regeneration after thumbnail cache loss.
4. **Notifications:** accept onboarding then grant/deny permission; denied permission keeps the journal usable. Toggle presentation, revoke permission and disable only its channel. Verify no hidden service continues. Add/edit/delete/restore entries and observe names, formatted doses/routes, colors, estimated phases, timeline, extra-row count and elapsed time. Repeated starts create one notification/updater. Verify private lock-screen redaction and explicit detail opt-in, cold/warm taps, same-entry unsaved editor focus and stale intents after restore. Check expiry/empty restore removes the notification and clears widget state, including a new ingestion arriving during shutdown. Exercise Android 34+ foreground restrictions.
5. **Widgets:** add, resize to small/medium/large and tablet sizes, verify selected light/dark palettes, list/timeline/empty/error states, many/long rows, tap-to-entry and stale-generation rejection. Toggle detail privacy; deny notification permission and verify widget independence. Check edits/theme changes/expiry through an active session, service updates and Android's periodic refresh. OS scheduling need not be exact; data must be current on refresh.
6. **Import and backup:** import archived upstream JSON and a genuine newer-format export when available. Inspect nested quantity/uncertainty conversions and all components of mixtures. Resolve import warnings explicitly. Export `.psyzine`, clear only the synthetic journal, restore and compare entries, photos/order/checksums/themes/presentation. Also test labeled entries-only JSON export. Inject bad ZIP paths, duplicate/extra/missing images, altered checksums, malformed JSON, invalid references/cycles, oversized extraction, cancellation and low storage. Every failed restore must preserve the existing journal/photos; preference failure after commit is reported as a warning. Open editors and external intents from before restore must not write/open reused entry IDs. Check clear/delete cleanup on later reconciliation.
7. **Privacy and upgrade:** inspect the merged permissions and FileProvider exposure. Check cloud/device-transfer exclusions on API 30 and 32+; preferences are eligible but journal/photos/staging are excluded. Verify no automatic network request, telemetry, photo location metadata or private journal diagnostics. Install alongside the original app, then upgrade a signed PsyZine fixture using the same key without losing data. External picker/camera/provider styling and cloud availability are controlled by Android/providers.

## Release gates

All standard builds/lint, Android matrix results, physical-camera/process-recovery/reboot checks, visual/accessibility review, backup failure preservation and minified signed-upgrade smoke tests must pass before claiming verified feature parity. Review and resolve reference-content permissions, configure the real fork URL and independent signing secrets, retain the key securely and check full offline notices. Current source implementation is not a substitute for these gates.

The release workflow checks `CONTENT_RIGHTS_VERIFIED=true`, builds/lints/tests all variants, signs release APKs through Gradle using independent PsyZine secrets, verifies with `apksigner`/`zipalign`, and distributes corresponding source plus checksums. After producing a candidate:

```bash
apksigner verify --verbose --print-certs PsyZine-v1.0.0.apk
zipalign -c -p 4 PsyZine-v1.0.0.apk
sha256sum -c SHA256SUMS
```

Review actual screenshots captured from the accepted candidate according to [screenshots.md](screenshots.md). Development pre-release publication is separately gated on successful build and emulator jobs; verify actual release assets rather than inferring publication from configuration. Production credentials were not created.
