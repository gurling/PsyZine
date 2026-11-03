# Component timeline verification

Recorded 3 November 2026. This change projects custom definitions into timeline
curves and calculated ingredient rows while retaining one journal row per stored
ingestion. A complete custom duration supplies the parent curve; its component
curves follow the definition's visibility setting. Incomplete custom durations
fall back to component duration information. Hidden curves remain included in
ingredient accounting and interaction lookup.

Experience details, expanded timelines and external presentation use the same
projection. Definition/route edits invalidate their metadata snapshots. Typed
subject identities keep identically named definitions and catalog substances
separate. Cursor quantities use the relevant units independently of display-height
normalization. Saved stomach fullness delays oral presentation only; recorded
timestamps and interval lengths are retained.

Calculated ingredient rows show quantities, uncertainty and curve visibility.
Large definitions have a bounded preview and a lazy list exposing every component.
Unknown amounts and calculation overflow produce readable explanations rather
than partial cumulative totals. Classification converts explicitly supported mass
units; unrelated count/volume units stay separate. Existing stored values are
never rewritten by presentation.

## Executed checks

| Check | Result |
| --- | --- |
| Direct debug APK and both debug unit suites | Pass: 142 tests per flavor, zero failures/errors/skips. |
| Focused Android 17/API37 Play tests | Pass: 10 tests, zero failures/errors/skips. Covers coherent metadata refresh, shared external curves, all 13 bitmap palettes, oral timing, retained record counts, session invalidation, 500-component lazy access and controlled RTL text. |
| Host release-policy tests | Pass: 31 tests. |
| Five-variant build/test/lint/APK verification | Pass: all five APKs, 426 JVM tests (142 in each of three suites), zero lint errors. Existing warnings remain: 85 per debug flavor and 64 per release variant. Pinned signatures, identities, SDK/permissions, ZIP integrity and 16KiB alignment pass. |
| Full Android 17/API37 Play regression suite | Pass: XML records 152 cases: 149 passed, three protected upgrade-fixture skips, zero failures/errors. Includes the actual two-minute stopped-UI clock check. |
| Actual platform 200% component-window font check | Pass: two cases, zero failures/errors/skips; both debug lint tasks also pass. Runtime configuration is asserted as 2.0, and the original 1.0 setting is verified after restoration. |
| Obtainium asset selection | Pass: decoded README link selects exactly one compatible APK. |

The first focused text checks override composition density locally. Visual review
found that a bottom-sheet window uses platform density independently, so that
override alone does not qualify its large-font behavior. Its platform check sets
the owned emulator's real font scale to 2.0 and restores the exact previous value.
Synchronized compositor captures replace blank or underlying-window captures.
The real-font screenshots exposed a title squeezed into word fragments beside
the Close button. A full-width title and separate Close row fix it; text-layout
assertions check whole-word wrapping, and the corrected screenshots were reviewed.
These checks
are separate from physical-device inspection and TalkBack acceptance. Emulator
checks use synthetic data; no physical phone installation or data change is part
of this verification.

## Compatibility and remaining gates

No database schema, migration, entity, backup format, package identity, version,
dependency, signing policy or theme changes are included. Build results from a
dirty feature checkout qualify this source slice, not release publication.
The final compatibility checkpoint is code4, `1.0.3-compat.4`, SHA-256
`4856083d07d9467655dbab4ed176d228bef1f1ceb9da6fc8e1eb6c3196312167`.
All five variants and the complete regression suite passed again after the title
layout correction. The final source hashes match the packaged source; no app or
test changes were made during that final build-and-test run.
Protected clean builds, installed minified upgrades, real device accessibility,
large-journal performance measurements and owner-supervised Obtainium acceptance
remain separate gates. No performance speedup or complete feature coverage is
claimed by these checks.
