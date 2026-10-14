# Physical Pixel large-text check — 14 October 2026

Owner-authorized installation and checks used a connected Pixel 6a on Android 17/API 37, its existing 115% font setting, 420dpi density and 1080 × 2400 portrait display. The configured appearance and keyboard were retained.

## Installation and data preservation

The installed `app.psyzine.debug` code-1 APK and the candidate both match retained certificate SHA-256 `7a8752f2571342ec6b9b0bb767e0b880e682ca35006fff8f01b4891d0bbd86b5`. Direct Debug `1.0.2`/code 3 was installed in place. The final installed APK exactly matches the verified local build, SHA-256 `670051a12ee6066293a6c4421fd24d474cbfe14f24b5d64ca91fbd669a3c05cd`, from production fix commit `87b5ad340664f8d0299d0af4ae95503bdc103f0a`.

Before installation, two stopped-app private backups and two completed portable exports were verified. Both exports showed “Backup saved” before copying. ZIP integrity, manifest equality, numeric validity and declared archive/photo entries were checked. Private backups also retain preferences omitted by the portable format.

Schema 8 → 9 passes SQLite integrity. All journal table contents, IDs, generation and stored preference files match the pre-installation snapshot exactly; the active primary-profile UID is unchanged. No synthetic journal records were saved. A completed post-update portable export passes ZIP checks and has exactly the same manifest as the pre-update export.

The temporary test runner was removed. The temporary stay-awake setting was restored, font/display settings remain unchanged, and the journal screen was left open. Private screenshots, XML, database snapshots and backup material remain ignored and off GitHub under `artifacts/pixel-large-text-check/`; backups also remain in the phone's local Downloads.

## Screen checks and discovered fix

Native captures were inspected for all five tabs, an existing experience/timeline, calendar, custom-unit list and an unsaved new-unit form. Text, actions and navigation remain readable at the owner's configured scale in these samples. Intended toolbar ellipsis and partially visible scroll content are distinguished from clipping.

The physical check exposed a new-unit initialization race: selecting a valid bundled substance incorrectly produced “This substance no longer exists.” Hilt creates the editor during composition; a Room transaction can read the Compose-backed selection before its snapshot has applied. The load/save identity now comes from immutable navigation arguments, including the chosen administration route. Generation and existence checks remain in place.

A deterministic regression holds the constructor's mutable snapshot unapplied. It reproduced the exact missing-substance error before the fix and passes afterward on the Pixel using an isolated in-memory database. The actual selected-substance form also opens correctly after the fix.

Physical form checks show complete messages for a missing name, missing singular label, negative dose and overflowing/non-finite dose. Invalid submission stays in the form. Clearing the dose removes its error and retains an unknown-dose draft; that draft was discarded. WindowInsets and accessibility bounds verify that the complete Done button is above the visible keyboard and the focused field is above Done.

## Repeat check after the owner increased font size

The owner increased the system font setting by one step, from 115% to **130%**, before requesting a repeat. The same installed APK was checked again on the Pixel 6a/API 37 at 420dpi and 1080 × 2400. No reinstall or app-code change was needed.

Native captures were reviewed again for all five tabs, the calendar, an existing experience/timeline, the custom-unit list and an unsaved new-unit form. No new text clipping was observed in these samples. Required-name and singular-label errors, negative-dose errors and the complete two-line numeric-overflow explanation remain readable. Invalid submission stays in the form, and clearing the dose removes the error while retaining the unknown-dose draft.

With the real FUTO keyboard visible, the complete Done button has bounds `[732,1124][1038,1271]`; the visible IME starts at y=1523. The focused dose field ends at y=667, above the button. Scroll content remains reachable; partial content at the viewport edge is not counted as clipped text.

The draft was discarded. Stopped-app snapshots before and after the repeat have exactly matching schema-9 table contents, including IDs/generation, and both stored preference files. The new 130% font setting, density and display size were retained; temporary stay-awake was restored to its original value. The journal was left open. Sensitive evidence remains ignored under `artifacts/pixel-font-step-check/` and was not uploaded.

## Automated evidence and limits

All five APK variants build and pass signature/package/version/alignment verification; all lint reports have zero errors. Three JVM suites pass 65 tests each, **195 total**; all **25 release-policy tests** pass.

[Android 17 CI run 38601334240](https://github.com/gurling/PsyZine/actions/runs/38601334240) verifies the same source with **81 passes**, zero failures/skips: 14 each at 130%, 150% and 200% platform fonts, plus 39 controlled/custom-unit/accessibility checks. Downloaded summaries, individual success statuses, configurations and the source/APK hash record were checked. The artifact ZIP matches GitHub's SHA-256 `1b3c758e683c09e48c16a1d1eba3d17f7efadf320e1b803fdc1e0fa60712ee71`; ZIP integrity and all 152 extracted files pass verification.

TalkBack spoken feedback, hardware keyboard, landscape/tablet, physical photo acquisition/gallery acceptance and a complete screen/palette sweep remain unverified. This was an owner-authorized Direct Debug installation; no release was published. Code 3 remains a local verification version for these changes; another public release requires the version/history and protected release gates.
