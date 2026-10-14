# PsyZine Compatibility 1.0.3-compat.4

Testing compatibility pre-release signed with the retained PsyZine identity.

- Add clear, field-specific custom-unit errors for missing labels, invalid numbers, unsafe calculations and storage failures.
- Preserve custom-unit drafts and plural labels; guard edits, archive actions and final ingestion saves against restored or changed records.
- Fix new-unit initialization so valid substance/route selections load reliably.
- Improve large-text layouts for settings, journal rows, dose fields, rating descriptions and navigation; keep Done/Next clear of the keyboard.
- Keep the complete “Per-ingestion timeline estimate” caption visible while dragging the graph, including near its edges.
- Correct tolerance-label and experience-title/date wrapping in substance statistics details.

The Direct Compatible Release APK retains `app.psyzine.debug`, the existing certificate, minification and `debuggable=false`. Schema 9 is unchanged from the previous compatibility release. Obtainium's README channel filter selects this APK.

Release verification uses Android 17/API 37, including 130%, 150% and 200% platform fonts, ordinary instrumentation, two independent clean signed builds and code1/code2/published-code3 → code4 → unpublished-code5 upgrade fixtures. The fixtures check journal rows/IDs/generation, photos, preferences, UID, Keystore decryption and completed photo-inclusive backups. Code5 remains an unpublished test fixture.

Physical Pixel 6a checks verify the reported large-text and graph/statistics issues. Full TalkBack, physical photo acquisition, landscape/tablet and Pixel 10 Pro XL/Obtainium acceptance remain separate. No phone installation is part of this publication. Preserve important journals and settings with verified backups before an owner-supervised upgrade.

The owner-approved testing exception does not establish content-rights clearance. Production retains its rights gate; GPL source and attribution accompany this testing release.
