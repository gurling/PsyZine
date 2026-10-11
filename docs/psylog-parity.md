# Pinned PsyLog parity and integration record

Implementation target: public PsyLog main [`0c29a70fa1ced6106c8379b2c02541699d61f966`](https://codeberg.org/PsyLog/PsyLog-android/commit/0c29a70fa1ced6106c8379b2c02541699d61f966), 14 February 2026. This is released v1.2 plus the build-flake commit. The full fetched history contains 1,403 commits; 22 fork-specific commits were reviewed after the common ancestor. A package-normalized source comparison was made against PsychonautWiki Journal `8224449a62e26ffd448aade0a7079b005fdd9d37`. PsyLog has 265 main Kotlin files, a single Android app module, the same seven Room schemas, and the same 289 substances / 21 categories with reference URL changes. No later branch, private upstream release, or unmerged proposal is included.

All rows below have source implementation in PsyZine. This does **not** certify device acceptance: the APK build, Android runtime tests, lint, visual review, minification, camera, and release signing remain unverified because of the environment limitations in [verification.md](verification.md).

## Functional parity checklist: F01–F22

| ID | Verified pinned functionality | PsyZine integration and acceptance |
| --- | --- | --- |
| F01 | Seven estimated phases: onset, comeup, peak, offset, afterglow, ended, unknown | `IngestionPhase.kt`; clock supplied explicitly. Unit tests cover exact boundaries, midpoint units, missing/invalid ranges, and future timestamps. Check labels on device. |
| F02 | Estimated dotted total from partial duration data | `TotalTimeline.kt` and `GroupDrawable.kt`; existing full/total/comeup/onset priority retained. Unit tests verify sums and reject non-finite/negative/reversed/zero estimates. Review graph fallback visually. |
| F03 | Independent substance heights enabled by default | Existing DataStore key defaults to true; a saved false is retained. Preference test passes. Check settings and overlapping timelines. |
| F04 | Journal-row crash fix for missing substance companions | Existing enum identity retained with RED fallback; brush also keys on active palette. Open mixed entries with no companions under every theme. |
| F05 | Latest experience with an ingestion in the previous 12 hours | Room query retains sort-date priority and adds deterministic ID tie-breaking and a now ceiling. Instrumentation tests exclude future-only and boundary entries. |
| F06 | Shared active-experience data provider | `ActiveExperienceRepository` and `TimelineModelFactory`; transaction reads relationship plus generation; common dose formatting, durations, colors, and model. Raw-query invalidation avoids loading the entire journal for refresh signals. Verify edits, custom units, and restored IDs. |
| F07 | Shared bitmap timeline rendering | `TimelineBitmapRenderer.kt`; explicit palette/density/clock and pixel budget, no notes/photos/ratings in external surfaces. Android tests cover every palette and allocation bounds. |
| F08 | Ongoing foreground notification and low-importance channel | `ActiveExperienceService`, coordinator, manifest service/specialUse subtype. Only visible-activity starts; catches lifecycle races. Test API 26 and 34+ foreground restrictions and channel behavior. |
| F09 | Compact notification summary | Themed collapsed RemoteViews show names and phases, with bounded summary plus count of extra ingestions. Check compact layouts and accessibility. |
| F10 | Expanded notification timeline | Shared 60dp renderer, selected palette, timeline hidden when reference duration is unavailable. Verify notification tray and missing duration data. |
| F11 | Expanded name/dose/route/color/phase rows | Shared formatted values and palette enum resolver; Android notification height bounds four rows with a clear remainder link. Full data remains in the journal. Check unknown doses/units and long names. |
| F12 | Elapsed-time chronometer | Starts at the first ingestion timestamp. Notification builder instrumentation verifies time and chronometer flag; inspect running updates. |
| F13 | Tap-to-experience intent | Immutable identity/generation-specific PendingIntent, cold launch and `onNewIntent`, typed navigation. Missing/stale generations and removed IDs ignored; same-entry editor stays in focus; other editors remain on the back stack. Android tests cover identity/generation uniqueness, initialization on upgraded journals and replacement invalidation. Cold/warm navigation requires device checks. |
| F14 | Live refresh of active presentation | One guarded update job; 30-second ticks and Room/preferences/theme changes. Single shared snapshot policy supplies both surfaces. Check repeated starts, edits, phase boundaries, palette changes, and worker/session reuse. |
| F15 | Resume tracking on launch and after adding an ingestion | Visible Activity observes Room changes, including successful ingestion insertion. Preserved generated parent-ID workflow. No unconditional Application foreground start. Verify capture/external-picker return and permission grant. |
| F16 | Stop presentation when no active experience remains | Service removes its foreground notification and updates widgets before stopping; disabling preference/channel/permission also stops it. Check expiry, deletion, restore to empty, and widget clearing. |
| F17 | Android 13+ notification permission | Asked once after accepted onboarding; denying it keeps journal fully usable and prevents a hidden foreground service. Explicit Android settings link allows later changes. Test grant, denial, revocation, and channel-only disable. |
| F18 | Registered home-screen timeline widget and empty state | Non-exported Glance receiver, Hilt entry point, app-widget descriptor/loading resources, 180×80 / 240×160 / 320×240 responsive sizes; empty and recoverable-error states. Check AOSP launcher and tablet resize. |
| F19 | Widget timeline image | Shared bounded renderer sized to widget bounds; fixed chosen palette rather than forced dark styling. Smaller widgets keep a readable ingestion list. Review all palette variants and host dimensions. |
| F20 | Widget dose/route/phase list and updates | Lazy list with remainder indication; Glance composition observes fresh snapshots, Room, preferences and 30-second active-session ticks. Android 30-minute periodic update works without service/notification permission. Glance row assertion test checks displayed values. Verify independent widget use, privacy toggle, expiry and launcher tap. |
| F21 | Android Journal newer-format JSON import conversion | `JournalImportAdapter`; source detection, stable string-ID remapping, recursive nested units, quantity/uncertainty retention with inherited measurement units, missing/cyclic/ambiguous-reference rejection. Custom products cannot collide with bundled reference identities; unknown measurement units leave doses unknown. Existing custom substances and repeated legacy definitions preserved. Multi-ingredient products retain **all** component definitions as a custom product with full JSON notes, without inventing a single-ingredient dose. Synthetic fixture unit tests pass; authoritative private-release exports remain unavailable. |
| F22 | Persistent import failure dialog and diagnostics | Settings retains dismissible detailed validation errors and busy state. Debug log is limited to safe error code/class; no journal text, file paths, or URI logging. Validation/staging precede atomic replacement. Unit path/limit checks and Android rollback/invalid-archive tests cover failure preservation. |

## Applicable improvements: D01–D12

| ID | Improvement | Integration |
| --- | --- | --- |
| D01 | Direct/Play distributions | `direct` retains `app.psyzine`; `play` adds `.play`; debug adds `.debug`. The namespaces, provider authority, typed navigation, and source schema path remain common. CI checks install IDs. |
| D02 | Gradle wrapper upgrade | Exact PsyLog Gradle **8.14**, not 8.14.3; official distribution SHA-256 retained. Java 17 / AGP 8.7.3 / Kotlin 2.0.20 kept. Gradle 8.14 starts but task execution is blocked by socket restrictions here. |
| D03 | Pinned Nix build/development shells | PsyLog `flake.nix`/lock adapted to PsyZine, SDK/build-tools 35, JDK17, wrapper-based commands, optional emulator/NDK/CMake/Studio/scrcpy/adb-sync. Evaluation awaits a Nix-enabled host. |
| D04 | Fork identity and launcher/splash artwork | PsyZine page/cursor assets, app name, application class, project name, and generated version retained. PsyLog eye artwork/namespace/IDs are not imported. |
| D05 | Adaptive monochrome icon | Existing PsyZine adaptive/legacy/monochrome assets retained; new notification symbol uses PsyZine’s own cursor/page artwork. Resource compilation passes. |
| D06 | Published privacy policy | `PRIVACY.md` and offline policy cover local data, explicit provider exports, preference backup metadata, device camera grants, lock-screen/widget visibility, diagnostics, and absence of analytics/network fetchers. Backup exclusion XML retained and source-checked. |
| D07 | Fork GPL/provenance notices | Complete COPYING and upstream notices retained; dated PsyZine and pinned PsyLog attribution added. Offline full palette/dependency licenses include Tokyo Night Apache-2.0. Reference-content rights remain a separate publication gate. |
| D08 | Fork support/source/share and donation treatment | Configured PsyZine source/issues/share URLs via BuildConfig; unconfigured local builds route to About. Donation page explicitly supports the original author. No PsyLog/upstream contact masquerades as PsyZine support. |
| D09 | FOSS/offline distribution metadata | Listing includes journal, medications/substances, photos, themes, notifications/widgets and portable backups; no paid features/analytics. Instrumentation captures 13 palette previews. Actual current screenshots require a device run; old upstream screenshots are not presented as PsyZine. |
| D10 | Ignore local assistant files | `.claude/` and `CLAUDE.md` ignored, along with private signing files. No inherited private assistant configuration added. |
| D11 | Independent release versions and build constants | PsyZine 1.0.0 / code1 retained; displayed version comes from BuildConfig. Signed workflow uses independent secrets, alignment, certificate verification, checksums and corresponding source. Workflow has not run. |
| D12 | Reference mirror links | All dataset, administration-route, dose/reagent/responsible-use, volumetric dosing, and preview article links use `psy.st/wiki/` as pinned PsyLog. Dataset exactly matches PsyLog after this URL change; factual content/counts are otherwise unchanged. Canonical ownership/provenance remains recorded. |

## Corrected upstream and prerequisite defects

- Kept original schemas 1–7 and generated schema 8 with Room’s actual processor. Manual 7→8 adds `photo_attachment` and `journal_state` and repairs the schema-1 AUTOINCREMENT omission with a conditional, row-preserving parent-table rebuild; PsyLog needs no additional schema changes. Generated migration SQL passes SQLite preservation/shape/cascade checks for every start version.
- Found that Room’s original generated 1→2 migration was empty despite schema 1 lacking AUTOINCREMENT. The conditional repair prevents deleted entry IDs from being reused on those installations; all other starting schemas keep their parent table.
- Removed experience REPLACE insertion, allocate new parent IDs through Room, preserve concurrent favorites/dates when editing, and include timed notes and attachment rows in complete deletion. Clear-journal generation invalidation is in the same database transaction.
- Restore is a validated file-staging plus replacement transaction. Failed imports preserve the current database/photos; legacy repeated custom-substance names remain valid because upstream never enforced uniqueness. Journal-generation tokens invalidate old editors and external intents after replacement.
- Fixed illegal composable color reads in drawing code and missing viewer opt-in. Empty/zero-count bar charts do not divide by zero. Cached timeline paints and row brushes follow theme changes.
- Limited bitmap sizes, notification binder text/row height, and widget lists; made Glance observe current data inside its session; fixed stale widget exit behavior and selected-theme rendering.
- Foreground startup comes from visible activity after consent/permission. Repeated starts share one job; stop decisions recheck fresh data and use the observed start ID so a suspended widget update cannot cancel a newer start; cancellation is rethrown. Channel-only disabling is respected. Lock-screen details default to private, with explicit opt-in.
- Newer-format import does not drop existing custom substances, discard nested units, select only the first mixture ingredient, or accept cycles/missing references. Complete source definitions remain reviewable in notes; product doses retain their original counts.
- Bounded photo decode to eight million pixels before orientation copies, recover from allocation failures, block overlapping acquisitions, release URI grants after saving, and collect orphan thumbnails as well as images. Saved photos survive discard/failure.
- First journal navigation waits for onboarding preference resolution. Preference-save failures are visible instead of silently navigating or accepting. Notification navigation checks generation again before opening an entry.
- Custom-unit/substance and ingestion editors capture a saved journal generation and check it inside a guarded database transaction. Restoring while one is open cannot write through its old IDs; substance editing uses an update rather than REPLACE and dismisses only after success. Save/delete errors remain visible.
- Domain and Material action text contrast is checked on raised card surfaces as well as the window base; the least contrasting neutral surface supplies palette adjustment.

## Architecture and integration sequence

1. Stabilize source compilation, schema 8, existing persistence and backup validation; preserve prior working changes.
2. Port null-companion, independent-height and partial-duration changes through existing journal/theme abstractions.
3. Extract shared timeline factory; add explicit-clock phase calculation and active-experience query/provider.
4. Add themed notification renderer, single-job service, permission/foreground coordinator and generation-aware typed navigation.
5. Add Glance widget with a Hilt entry point and fresh state observation; add presentation/privacy settings to the existing DataStore.
6. Normalize legacy/newer JSON before validated restore; extend optional backup manifest presentation settings without changing format version or restoring permission/consent.
7. Integrate mirror references, variants/wrapper/Nix, notices/privacy/metadata, CI checks and independent signed-release packaging.
8. Run host checks and prepare the full Android/device acceptance procedures. Build/runtime limits are recorded rather than treated as passes.

## Acceptance procedures and blockers

Run the commands and device matrix in [verification.md](verification.md). Acceptance requires every existing journal screen and ingestion workflow to work, all 13 themes and settings to persist, accurate privacy/permission behavior, correct phase/timeline updates and navigation, photo recovery and backup round trips, migration retention, rollback after injected failures, accessible controls, and stable signed upgrades. Check API26/29/30/32/33/35/36/37, an AOSP launcher without Play services, a physical camera, and a tablet.

Technical blockers are environmental: Gradle cannot create required sockets, so full build/lint/manifest merging/minification and Android execution cannot run here; no emulator/physical device or Nix is available. Authoritative private v12+ export fixtures and content redistribution permissions are external evidence gaps. A configured GitHub origin/source URL and independent signing key are release provisioning requirements. Source is implemented, but verified complete device parity and production readiness are not claimed.
