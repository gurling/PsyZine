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

## Graph cursor and substance-statistics follow-up

The owner identified two additional interaction paths after the sampled tab sweep: holding/dragging on the experience graph and opening a substance's statistics detail. On the same Pixel at 130%, the transient readout visibly clipped the last line of “Per-ingestion timeline estimate”; the statistics tolerance card split “zero:” across lines. The earlier tab captures did not cover these paths.

Commit `8360ca6b7d206789bd41212db46af9cbbe7aec53` replaces the readout's fixed 140dp height cap with the available graph height and keeps its estimate caption outside the scrolling ingestion list. Tolerance labels use their natural text width while descriptions wrap in the remaining space. Experience titles and dates flow onto separate rows when they cannot share a line.

Four isolated synthetic component regressions fail against the previous installed app at 200% component fonts. All four pass after the fix at the real 130% platform setting and again at 200% component fonts, **eight passing checks**, without changing system font settings or writing journal data. They check dragging to the edge, complete caption visibility, multiple ingestions, unsplit tolerance labels and long experience titles/dates.

Native before/after captures verify the actual center/left/right graph readout, expanded timeline and statistics detail. The caption is complete, the readout stays within the graph's horizontal edges, releasing touch hides it, and the tolerance label remains intact. Two fresh stopped-app private snapshots were verified before the update. After installation and testing, schema-9 table contents, IDs/generation, both stored preference files and the primary-profile UID still match exactly. The temporary test package was removed; the 130% font and display settings were retained and stay-awake restored.

The installed Direct Debug `1.0.2`/code 3 APK matches the clean five-variant build at this commit: SHA-256 `98d57eba04d7623cb48e999883e05381293c904192394443ed8afbdfcbc1358e`. All five variants pass build/lint/APK verification, all 195 JVM tests pass and all 25 release-policy tests pass. Private evidence stays ignored under `artifacts/pixel-graph-detail-check/`. No release was published.

[Android 17 CI run 38610648415](https://github.com/gurling/PsyZine/actions/runs/38610648415) passes **97 checks**, zero failures/skips: 18 each at 130%, 150% and 200% platform fonts, plus 43 controlled checks. Downloaded runner summaries, individual success counts, settings and source records agree. The artifact ZIP matches GitHub SHA-256 `db619618cb9d715934dcf0f2636c0483d1a16c74d79d4e91949bf9a2241167b1`; ZIP integrity and all 172 extracted files were checked. The downloaded 200% caption and long-title/date captures were reviewed. The test activity toolbar obscured the top of the tolerance capture. Test-only follow-up `8a1d03ad` hides that toolbar; all four cases pass again at each font setting. Toolbar-free 200% caption, tolerance and title/date captures were reviewed, and another before/after snapshot confirms unchanged journal tables/preferences. The application APK is unchanged by this fixture correction.


## Automated evidence and limits

All five APK variants build and pass signature/package/version/alignment verification; all lint reports have zero errors. Three JVM suites pass 65 tests each, **195 total**; all **25 release-policy tests** pass.

[Android 17 CI run 38601334240](https://github.com/gurling/PsyZine/actions/runs/38601334240) verifies the same source with **81 passes**, zero failures/skips: 14 each at 130%, 150% and 200% platform fonts, plus 39 controlled/custom-unit/accessibility checks. Downloaded summaries, individual success statuses, configurations and the source/APK hash record were checked. The artifact ZIP matches GitHub's SHA-256 `1b3c758e683c09e48c16a1d1eba3d17f7efadf320e1b803fdc1e0fa60712ee71`; ZIP integrity and all 152 extracted files pass verification.

TalkBack spoken feedback, hardware keyboard, landscape/tablet, physical photo acquisition/gallery acceptance and a complete screen/palette sweep remain unverified. This was an owner-authorized Direct Debug installation; no release was published. Code 3 remains a local verification version for these changes; another public release requires the version/history and protected release gates.
