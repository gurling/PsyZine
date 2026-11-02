# Journal search and data handling

## Changes

- Build search results from one coherent journal snapshot, with cancellable filtering off the main thread and deterministic ordering.
- Search substances, definitions, experiences, ingestions and favorites. Preserve the search context when opening or editing a result.
- Invalidate open journal screens after a restore so stale IDs cannot select replacement records.
- Store reusable definitions with nested components, explicit quantities, tags and primary memberships. Reject invalid graphs and retain readable records when metadata is missing.
- Keep legacy name ambiguity visible without silently changing stored subject identities.
- Exchange versioned definition libraries and journal backups, including tolerance-period preferences.
- Show numeric tolerance intervals and missing-information states, with an independently saved period selection.
- Keep errors readable and forms open after failed writes. Preserve large-text wrapping and existing theme roles.

These changes include functional and reliability improvements; no measured speedup is claimed.

## Data compatibility

Room migration 9 to 10 adds definition components, tags and presentation metadata while retaining earlier schemas, record IDs and existing attachments. Legacy custom-unit rows remain readable and editable. No destructive migration fallback is introduced.

Portable backups use format 2. Restore continues to accept format 1 and supported legacy JSON. Validation finishes before replacing journal data; interrupted or invalid imports must preserve the existing journal. Definition-library exchange remains separate from full-journal restore.

Application IDs, signing identity, storage paths, offline operation and all 13 themes remain unchanged. A source push does not qualify or publish a release.

## Verification

Run the repository's standard checks with JDK 17 and Android SDK 35:

```sh
./gradlew :app:assembleDirectDebug
./gradlew :app:testDirectDebugUnitTest :app:testPlayDebugUnitTest
./gradlew :app:lintDirectDebug :app:lintPlayDebug
bash tools/build-apks.sh
python3 -m unittest discover -s tools/tests
git diff --check
```

Use an explicitly selected isolated emulator for instrumentation. Cover schema migration and exact field retention, backup failure rollback, definition cycles and missing subjects, tag filters, search ordering, stale navigation, failed writes, large text and RTL. Protected installed-upgrade tests and physical-device acceptance remain separate checks.
