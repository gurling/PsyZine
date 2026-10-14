# Large-text layout fixes and verification

Implementation branch: `fix/custom-unit-validation`. Initial layout changes are in `c67a62b5`, with initial Android 17 evidence at `9d7fa21d627924c4b1fb10ae67521153966dc1ea`. The subsequent [physical Pixel check](pixel-large-text-verification.md) exposed and fixed a new-unit composition/Room initialization race in `87b5ad340664f8d0299d0af4ae95503bdc103f0a`. That source passes 81 Android 17 CI checks and was installed in place on the Pixel. Schema 9, application identities, versions, signing, journal data and all 13 palettes are retained. No release was published by this follow-up.

## Changes

- Dose reference tables measure their labels and values with the current text measurer. When the compact row cannot fit, the same ranges appear as wrapping labeled lines. The chosen font size is retained.
- Settings labels wrap within their available width while switches retain room. Journal titles reserve space for favorites, ingestion notes determine row height, and dates, locations, doses and statistics totals can wrap. Decorative side stripes follow the measured content height rather than determining it.
- Calendar cells retain their normal square minimum and grow vertically when enlarged dates need more room.
- Rating rows grow above their 56dp minimum to show complete descriptions. Enlarged descriptions previously exceeded the fixed row height; every rating remains selectable.
- Bottom navigation and statistics period tabs use scrolling when their complete labels cannot fit. Selection and keyboard focus bring the bottom navigation item into view. The ordinary navigation scaffold remains in use when labels fit and for the existing rail/hidden-navigation layouts.
- Toolbar titles stay on one line with an ellipsis, preserving room for navigation and action icons. Body text, validation errors and dialog titles continue to wrap.
- Custom-unit, ordinary-dose, custom-substance-dose and ingestion-editor inputs use short labels with the full measurement context in wrapping supporting text. Long unit names no longer occupy the number field's trailing slot. Unit buttons wrap onto additional rows. Save/Next buttons reserve space in the scrolling viewport and consume navigation insets before applying keyboard insets.

These changes preserve numeric values and existing warning expansion, navigation, theme and save behavior. Long single-line editable values retain horizontal scrolling. A deliberately collapsed dose warning still has an Expand action; verification expands it before checking the complete warning.

## Verification method

The current target is Android 17/API 37, as requested by the owner after identifying the Pixel's Android version. The isolated emulator uses `system-images;android-37.0;google_apis_ps16k;x86_64` revision 7 and Emulator 37.2.12, with 4GB RAM and software graphics. The project still builds against SDK 35. Earlier completed API-26/API-35 runs are retained as supplementary evidence; additional older-version runs were stopped.

Tests use a 480 × 1066 virtual display. Display densities of 195 and 240 produce approximately 393dp and 320dp portrait widths. This is a width/scale stress fixture, not an exact Pixel device simulation.

Platform runs set Android's `font_scale` and display density, then assert that the Compose density reflects that setting. Controlled runs keep the OS at 100%/160dpi and inject a Compose density for component stress tests. The dose-reference matrix checks 100%, 130%, 150% and 200% at 320dp/393dp in both LTR and RTL. This controlled matrix supplements actual platform scaling; it does not substitute for it. Android 14+ uses [nonlinear font scaling](https://developer.android.com/about/versions/14/features#non-linear-font-scaling).

`LargeTextLayoutTest` checks measured line bounds and retained text, switch/favorite visibility, calendar dates, all statistics tabs, long unit names, wrapping notes, all six rating choices, final scroll sections and save-button separation. Its keyboard case opens the real emulator IME and compares field/button window coordinates with the visible display frame. The ordinary-dose fixture also opens and awaits the IME before scrolling; the uncertainty field and its complete supporting description must both remain visible. Supporting-text assertions target the text node rather than its merged field. `MainActivityLargeTextTest` uses the actual activity, visits all five tabs, checks full navigation labels and scrolls to a settings control. The real-activity test changes synthetic preferences and generated capture folders; repository tests mutate only emulator fixtures.

`CustomUnitRegressionTest` repeats validation, guarded writes and draft recovery, and captures error states for all 13 palettes plus light/dark system appearances at 200% in RTL. `UiAccessibilityRegressionTest` checks chart summaries and bounded timeline cursor actions. These are Compose semantics checks; they do not establish actual TalkBack spoken feedback.

The earlier API-35 software emulator used the emulator-only `ro.hw_timeout_multiplier=5` after a framework restart, because its instrumentation runner initialization can exceed the default framework timeout on this unaccelerated host. Application/coroutine test timeouts are unchanged. Results are functional evidence, not startup or performance measurements.

## Recorded results, 14 October 2026

- Latest source `87b5ad34` passes all five APK builds/verifiers, 195 JVM tests and 25 release-policy tests. [CI run 38601334240](https://github.com/gurling/PsyZine/actions/runs/38601334240) passes **81 UI checks**: the original 42 platform checks plus 39 controlled checks including the new snapshot regression. The downloaded artifact and all extracted file hashes were verified. The final protected-key local Direct Debug APK hash is `670051a12ee6066293a6c4421fd24d474cbfe14f24b5d64ca91fbd669a3c05cd`; the installed Pixel APK matches exactly. Private physical evidence and remaining limits are summarized [here](pixel-large-text-verification.md).
- The following detailed record retains the initial 80-check run and its original APK hashes; it predates the physical initialization-race fix.
- All five variants build and pass lint with zero errors. Package/version/debuggable flags, permissions, SDK levels, certificate pins, signatures, ZIP integrity and alignment pass APK verification. Lint warnings remain: 84 per debug variant and 64 per release variant.
- JVM suites: 65 Direct Debug, 65 Play Debug and 65 Direct Compatible Release tests, **195 total**, zero failures/errors/skips. All **25 release-policy tests**, workflow lint, shell lint and diff checks pass.
- [Android 17 CI run 38583953357](https://github.com/gurling/PsyZine/actions/runs/38583953357) passes **80 UI tests**, zero failures/skips. The downloaded runner summaries and individual success status counts agree with all four profile totals below. API 37, 16KB pages and KVM acceleration were checked before installation. Fifteen controlled error-state captures cover all 13 palettes and both system-theme appearances in RTL.
- Earlier completed emulator runs: API 26 has 13 maximum-platform-font checks, 37 controlled/custom-unit/accessibility checks and one focused rating regression; API 35 has 13 maximum-platform-font checks and one focused rating regression. **65 passing UI tests** are recorded. The core runs preceded the final isolated rating-row change; that new regression passed separately on both APIs. The original rating reproduction failed with five clipped descriptions and is excluded from passing totals.
- The incomplete API-35 130% run was stopped when the owner requested newer Android versions. It is excluded from passing totals, and no further older-version runs were started.
- Earlier unaccelerated local Android 17 launches failed: three attempts with Emulator 37.2.12 and the API-37 revision-7 16KB image crashed with exit 139 before Android booted or ADB became available. Attempts used software graphics, SwiftShader with Vulkan disabled, and a single-core SwiftShader configuration. `/dev/kvm` is absent (`emulator -accel-check` returns 8). The app was never installed in those local attempts; the accelerated CI run above supplies Android 17 runtime evidence.

The final local Direct Debug APK SHA-256 is `7f0d59fd5752e94c1d8afbe4d82552e8239ffeeafa35f761cd6310d64d54a25d`. The staged app matches the final all-variant build. Staged test hashes, source hashes, raw logs, configurations and captures are retained in ignored artifacts. Subsequent test-host and CI changes do not change production code.

## Android 17 CI route

[Android 17 large-text checks](../.github/workflows/large-text-checks.yml) uses an accelerated fresh API-37/16KB emulator and builds unpublished test APKs against SDK 35. It runs [run-large-text-checks.sh](../tools/run-large-text-checks.sh), which refuses physical serials, requires an emulator on API 36+, and refuses an existing PsyZine installation before installing or mutating fixtures.

| Profile | Platform font/display setting | Passing checks |
| --- | --- | ---: |
| Maximum | 200%, 240dpi, 320dp portrait width | 14 |
| Intermediate | 130%, 195dpi, about 393dp portrait width | 14 |
| Larger | 150%, 240dpi, 320dp portrait width | 14 |
| Controlled | OS 100%/160dpi, component density overrides | 38 |

The verified total is **80 tests**. The downloaded artifact contains 26 PNGs for each platform profile and 52 for the controlled profile. Its ZIP SHA-256 matches GitHub's digest `9d12d09801fd5c92352d6880e421f4bad2fe4b6f457f826374f484d2732d4232`; ZIP integrity and hashes for all 152 extracted files pass. Representative native/component captures were visually inspected: the 200% keyboard layout, 130% ordinary-dose description, 150% rating descriptions, 200% main journal and journal row, and light/dark RTL custom-unit errors. Partially visible scroll content is distinguished from text clipping. These samples supplement the measured assertions; they are not a visual review of every screen.

The CI artifact's `verification.json` records these unpublished, disposable-key APK hashes:

- Direct Debug: `a971b57d423a13fd77b1c847b0f01fd7bac55a0ba61841c049470812f905d901`.
- Instrumentation: `f5fc1cef89a5fb1f11e4687220857a04441647caf233268f4c788ee0fe62339d`.

The harness rejects skipped cases and failed/missing runner summaries, records APK hashes/source commit and saves native/component captures with each profile's settings. CI uses explicit SDK executable/AVD paths, installed emulator runtime libraries and a settled, checked KVM device. The fresh-install check lists packages, so an expected absent package does not terminate the script as `pm path` does. Six simulated guard checks verify rejection of physical serials, older APIs, existing installations and ADB errors, and progression for fresh emulators with either QEMU marker. Earlier failed startup/test runs are retained separately and excluded from passing totals. No physical-device acceptance or release publication runs in this workflow.

## Reproduction

Use JDK 17, SDK 35 and build-tools 35.0.0. Build and install Direct Debug plus its test APK on a synthetic emulator; do not change a phone's settings or installation for these commands.

```bash
./gradlew :app:assembleDirectDebug :app:assembleDirectDebugAndroidTest
adb -s emulator-5594 install -r app/build/outputs/apk/direct/debug/app-direct-debug.apk
adb -s emulator-5594 install -r app/build/outputs/apk/androidTest/direct/debug/app-direct-debug-androidTest.apk
adb -s emulator-5594 shell wm size 480x1066
adb -s emulator-5594 shell wm density 240
adb -s emulator-5594 shell settings put system font_scale 2.0
adb -s emulator-5594 shell settings put secure show_ime_with_hard_keyboard 1
```

The platform fixture accepts runner arguments `-e platformFontScale true -e expectedFontScale 2.0`. Run the thirteen individual `LargeTextLayoutTest` methods other than `doseReferenceAdaptsAcrossFontsWidthsAndDirections`, plus `MainActivityLargeTextTest`. For the controlled run, reset the OS font scale to 1.0/density to 160, omit those arguments, and select the three component/custom-unit/accessibility test classes. Leave the real activity test to the platform runs.

Use `bash tools/build-apks.sh` for all five APKs, JVM suites, lint and signed APK verification. Run `git diff --check`. Record actual test summaries and captures rather than inferring passes from a successful install or build.

## Limits

The [physical Pixel check](pixel-large-text-verification.md) verifies sampled rendering, validation and the real keyboard at the owner's original 115% and subsequently increased 130% settings. TalkBack, hardware keyboard navigation, landscape/tablet layouts and a complete visual sweep of every screen remain unverified. Single-line toolbar truncation and partially visible offscreen tabs are intentional; those tabs remain scrollable and selectable. No claim is made that every UI surface is free of clipping.

Synthetic logs/captures stay ignored under `build-logs/` and `artifacts/text-scaling/`. Native screenshots are preferred; if the software emulator cannot capture one, the test saves a Compose-root image. Root images exclude system chrome/keyboard, so keyboard acceptance comes from the independent visibility/coordinate assertions. Actual ADB before/after captures also confirm the main journal toolbar retains all four action icons.

VersionCode 3 is unchanged and already published. The initial dirty-source build was marked `publicationAllowed=false`; the subsequent clean build passes signing/source eligibility, while version-history and protected publication gates still apply. Publishing these fixes requires a new reviewed version. Source pushes for CI do not publish a release; the physical installation was separately authorized by the owner.
