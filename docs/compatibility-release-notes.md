# PsyZine Compatibility 1.0.2-compat.3

Testing compatibility pre-release, signed with the retained PsyZine identity. The owner-approved testing exception does not establish content-rights clearance. Production retains its rights gate.

- Preserve dose and uncertainty precision when editing ingestions.
- Reject stale journal edits after restore or deletion and keep failed saves open.
- Validate negative, non-finite and overflowing numeric input safely; preserve unknown doses.
- Correct midnight/DST calendar boundaries and calendar-month/year statistics; count records with missing companion metadata.
- Add six indexes in schema 9 without rewriting records or purging legacy orphans.
- Load photo rows lazily, bound thumbnail decoding to two operations and recover from allocation failures.
- Load reference data asynchronously, cache search names and suspend UI clocks/subscriptions while stopped.
- Add accessible chart summaries, bounded timeline cursor actions and theme-aware calendar gradients.
- Repair upgrade-history checkout, verify explicit schema versions and wait for completed backup exports.
- Verify downloaded draft assets before making the release public.

Obtainium selects exactly one Direct Compatible Release APK through the README channel configuration. It retains `app.psyzine.debug`, minification and `debuggable=false`. Matching package and retained certificate are both required for an in-place update. Never uninstall, clear data or use a downgrade flag to bypass rejection.

Publication requires all five variants, build/unit/lint/APK checks, the full ordinary API matrix, two independent clean signed builds, six code1 → code3 → unpublished code4 upgrade checks, an additional API35 code2 minified baseline, matching source/checksums and historical version-code review. Evidence accompanies the Actions run and BUILD_RESULTS.json. Code4 is never published as an empty follow-up release.

Full owner-supervised phone/Obtainium, camera, reboot, TalkBack, large-font, RTL and tablet acceptance remain separate. Verify two complete backups and settings omitted by portable backups before the scheduled Pixel upgrade. Rollback uses a forward fix with a higher versionCode and schema-9 support.
