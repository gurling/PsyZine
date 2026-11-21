# PsyZine Compatibility 1.0.4-compat.5

Testing compatibility pre-release signed with the retained PsyZine identity.

- Add reusable custom definitions with nested components, route metadata, tags and library import/export.
- Improve journal and substance search, saved filters and navigation back to search results.
- Add advanced statistics, dose distributions, interval summaries and historical tolerance views.
- Add stomach-fullness recording, ingestion visibility and persistent journal display controls.
- Improve grouped timeline estimates and large-text graph captions. Grouped amounts remain unknown when any contribution lacks a dose or usable conversion ratio.
- Preserve editor drafts and dose precision, reject stale or changed records, and keep failed saves visible with readable recovery actions.
- Improve large-journal queries, lazy photo loading and lifecycle-aware relative-time updates.
- Add local support information and bundled release notes without automatically sharing private journal data.

The Direct Compatible Release APK retains `app.psyzine.debug`, the existing certificate, minification and `debuggable=false`. The additive schema-10 migration preserves existing records and schema history. Portable backups retain attachments and new definition/tag data; older supported backup formats remain readable. Obtainium's README channel filter selects the compatible APK.

Publication requires the linked workflow to pass five-variant build/test/lint checks, Android 17/API37 instrumentation and large-text checks, two independent clean signed builds, and code1/code2/published-code3/code4 → code5 → unpublished-code6 upgrade fixtures. Upgrade fixtures verify records, photos, preferences, UID, Keystore decryption and completed photo-inclusive backups. Code6 is not published.

This is a testing pre-release, not a claim that every manual acceptance check is complete. Full physical TalkBack/keyboard, landscape/tablet, photo acquisition and owner-supervised Obtainium acceptance remain separate. No phone installation is part of publication. Preserve important journals and settings with verified backups before upgrading; do not uninstall, clear data or downgrade.

The owner-approved testing exception does not establish content-rights clearance. Production retains its rights gate; GPL source and attribution accompany this testing release.
