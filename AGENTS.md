# Repository Guidelines

## Project Structure & Architecture

The Android Git checkout is `android/`, containing one `:app` module. Kotlin sources are under `app/src/main/java/com/isaakhanimann/journal/`: `ui/` contains Compose screens and themes, `data/` contains Room, preferences, attachments and backups, and `di/` contains Hilt wiring. Keep the internal namespace separate from the `app.psyzine` application identity.

Resources live in `app/src/main/res/`, offline data/licenses in `app/src/main/assets/`, and versioned Room schemas in `app/schemas/`. JVM tests use `app/src/test/`; device tests use `app/src/androidTest/`. Consult `docs/psylog-parity.md`, `docs/verification.md` and `docs/build-publication.md` before changing established behavior.

## Build, Test & Development Commands

Run commands from `android/`. Use JDK 17, Android SDK 35 and build-tools 35.0.0; configure local SDK paths outside version control.

- `./gradlew :app:assembleDirectDebug`: build the Direct debug APK.
- `./gradlew :app:testDirectDebugUnitTest :app:testPlayDebugUnitTest`: run both flavor unit suites.
- `./gradlew :app:lintDirectDebug :app:lintPlayDebug`: run Android lint.
- `./gradlew :app:connectedDirectDebugAndroidTest`: run instrumentation on a connected device/emulator.
- `bash tools/build-apks.sh`: build all five testing variants, including Direct Compatible Release, test, lint and verify pinned signed APKs.
- `adb install -r app/build/outputs/apk/direct/debug/app-direct-debug.apk`: install locally.

Socket-restricted environments may prevent Gradle and ADB startup. Record failures explicitly; standalone compilation does not establish APK build success.

## Coding Style & Naming

Use four-space indentation and existing Kotlin conventions: PascalCase classes/composables, camelCase functions/properties, and descriptive `Screen`, `ViewModel`, `Repository` and `Dao` suffixes. Follow existing Compose, coroutine/Flow and Hilt patterns. Keep disk work off the main thread and resolve UI colors through theme roles. Android lint is configured; no dedicated Kotlin formatter is configured. Run `git diff --check`.

## Testing Guidelines

Use JUnit 4 for JVM tests and AndroidJUnit4, Compose testing, Espresso and Room testing for instrumentation. Name files `*Test.kt` and methods after observable behavior. Cover migrations, atomic restore, attachment failures and theme behavior when affected. No numerical coverage threshold is configured. Report automated and manual checks separately.

## Commits & Pull Requests

History uses imperative subjects such as “Preserve GitHub initialization history.” Keep commits focused. PRs should explain behavior, linked issues, validation and blockers; include screenshots for UI changes and migration notes for schema changes.

## Git safety policy

- Read-only Git commands may run automatically.
- Work on a feature branch, never directly on main.
- Do not push, force-push, delete remote branches,
  or merge pull requests without explicit approval.
- Before requesting approval to push, show:
  - Current branch and destination
  - Commits that will be pushed
  - Summary of changes
  - Test results
- Never alter Git remotes or credentials without approval.
- Never disable SSH host-key verification.
- Never rewrite published history without approval.
- Stop and report any unexpected Git authorization
  or connectivity errors.

## Data, Security & Licensing

Preserve all 13 themes, photos, preferences and journal data. Retain schema history and additive migrations. Keep keystores, credentials, backups and generated outputs ignored. Preserve offline privacy protections, GPL attribution and third-party notices; production publication requires the documented content-rights gate. PsyLog PR #6 remains outside scope.
