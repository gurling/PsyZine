# Android builds, signing and GitHub publication

Repository: https://github.com/gurling/PsyZine. The Android project is the repository root. The local checkout is `/home/izzy/codex/PsyZine/android`; upstream history and GPL attribution are retained.

## Recorded execution result — 11 October 2026

The former socket restriction no longer reproduces: IPv4 sockets, the actual Gradle wrapper, ADB and authenticated SSH now work. All four Direct/Play Debug/Release APKs were built with JDK 17, Gradle 8.14, SDK 35 and explicitly pinned build-tools 35.0.0. Both flavor JVM suites passed (35 tests each). All four lint tasks passed with zero errors; dependency/unused-resource warnings remain. Minification, resource linking, manifest merging, APK signatures, ZIP integrity, application IDs and alignment were checked. All four local APKs installed and launched on an isolated Pixel test installation.

Android instrumentation passed all 23 tests on each CI emulator at API 26/29/30/32/33/35 in [run 38136387190](https://github.com/gurling/PsyZine/actions/runs/38136387190). That run's separate build job failed, so it did not publish a release; the updated workflow limits workers, compiles Kotlin in process, and exposes final build-log errors. A full idle Pixel/API 37 run passed 22/23 tests; the editor's injected-save-failure check timed out. This physical-device failure remains unresolved and is not counted as a pass. Room migrations, photo/backup tests and the other UI checks passed in that run. Thirteen synthetic palette captures were generated; complete visual acceptance remains separate.

Source commits have been pushed without force to the requested SSH origin and verified through GitHub's API. The repository was not empty: its initial license commit and upstream history were preserved. Work remains on a local feature branch, pushed explicitly to remote `main` under the user's publication authorization. No Git remotes or credentials were changed. The broken system SSH configuration is bypassed per command with `ssh -F /dev/null -o BatchMode=yes -o StrictHostKeyChecking=yes`; known-host verification remains enabled. Git commits use a per-command GPG-signing override because the configured keybox directory is read-only.

The required release evidence is the successful complete [Actions run](https://github.com/gurling/PsyZine/actions) and its actual [testing pre-release assets](https://github.com/gurling/PsyZine/releases), rather than this document or the workflow configuration. The pre-release job runs only after both the build job and every required emulator job succeed. Production signing credentials, reference-content clearance and full manual acceptance remain outstanding. Development publication does not establish production readiness or resolve third-party content rights.

## Build all installable variants

Install JDK 17, Android SDK platform 35, build-tools 35.0.0 and Android command-line tools. Set `JAVA_HOME` and `ANDROID_HOME` to those installations. If the default Gradle cache is unwritable, set `GRADLE_USER_HOME` to a writable directory. Use an environment that permits Gradle's local sockets and dependency downloads:

```bash
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
bash tools/build-apks.sh --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process
```

This runs the actual wrapper, builds Direct/Play Debug/Release, runs both flavor unit suites and all four lint tasks, then verifies every APK. A failed Gradle task prevents publication of the downloadable artifact set. `verify-apks.py` also requires successful task evidence, nonempty passing JUnit XML and zero-error lint XML; it does not substitute source compilation for Gradle.

Verification checks ZIP integrity, dex/manifest presence, application IDs, version code 1, version 1.0.0 or the explicitly labeled 1.0.0-dev release, min/target SDK 26/35, forbidden permissions, APK signatures with SDK `apksigner`, and 16 KiB native-library alignment with `zipalign`. It records certificate digests, byte sizes and SHA-256 checksums. Direct/Play certificates must agree for each build type. Actual device installation remains a separate test.

Verified development output names:

| Variant | Gradle output | Downloadable development-signed copy | Application ID |
| --- | --- | --- | --- |
| Direct Debug | `app/build/outputs/apk/direct/debug/app-direct-debug.apk` | `artifacts/apks/PsyZine-Direct-Debug-v1.0.0.apk` | `app.psyzine.debug` |
| Direct Release | `app/build/outputs/apk/direct/release/app-direct-release.apk` | `artifacts/apks/PsyZine-Direct-Release-v1.0.0-dev.apk` | `app.psyzine` |
| Play Debug | `app/build/outputs/apk/play/debug/app-play-debug.apk` | `artifacts/apks/PsyZine-Play-Debug-v1.0.0.apk` | `app.psyzine.play.debug` |
| Play Release | `app/build/outputs/apk/play/release/app-play-release.apk` | `artifacts/apks/PsyZine-Play-Release-v1.0.0-dev.apk` | `app.psyzine.play` |

`artifacts/apks/BUILD_RESULTS.json`, `BUILD_RESULTS.md`, signature reports and `SHA256SUMS` accompany the APKs. Production release copies use version 1.0.0 without `-dev`. Source URLs default to the actual fork; `-PpsyzineSourceUrl=https://github.com/OWNER/REPO` overrides them for a fork.

## Signing and upgrades

Ordinary release builds use development signing and version suffix `-dev`, while retaining their installation IDs. The build script generates/reuses `.local/signing/psyzine-development.jks` (ignored, owner-only), with alias `androiddebugkey` and the standard development-only password `android`. Set `PSYZINE_DEVELOPMENT_KEYSTORE` to use a different external location. This is not a production signing credential. The local development key signs all four verified APKs. Each CI run uses its own key unless a stable development key is configured.

For production signing set all four environment variables: `PSYZINE_RELEASE_KEYSTORE_FILE`, `PSYZINE_KEY_ALIAS`, `PSYZINE_STORE_PASSWORD`, `PSYZINE_KEY_PASSWORD`. The keystore must already exist outside version control. Partial configuration fails rather than silently falling back. Gradle signs both release distributions with that key and uses version 1.0.0. Never commit keys, credential files or passwords.

An update requires the same application ID and signing certificate. A development-signed release cannot upgrade a production installation with a different certificate, or later be upgraded in place to a differently signed production release. Export test data before switching signing identities; use isolated test installations. Retain the local development key for repeatable local testing. CI generates a fresh ephemeral development key unless the optional `PSYZINE_DEVELOPMENT_KEYSTORE_BASE64` secret supplies a stable key with the standard alias/password. Without that secret, APKs from different CI runs may require reinstalling. Production signing continuity requires secure retention of the independent production key.

## Continuous integration

`Android checks` runs on push, pull request and manual dispatch. It builds/tests/lints all four signed variants, verifies them, uploads `PsyZine-development-APKs-<commit>` with checksums/results and corresponding source, and separately uploads build/test/lint logs. The workflow summary shows APK IDs, byte sizes and SHA-256 values only after actual successful Gradle execution and verification. Fork pull requests do not receive repository secrets; they can use ephemeral development signing. The production key is never used by this workflow.

The AOSP instrumentation matrix covers API 26/29/30/32/33/35; dispatch 36/37 separately once their images are available. Reports and synthetic palette captures are separate per-device artifacts. Camera hardware, reboot/process recovery, tablet and accessibility checks follow [verification.md](verification.md).

`PsyZine signed release` decodes the independent production key into an owner-only runner-temp file, uses Gradle signing, verifies the production candidate, packages corresponding source/checksums and publishes only on a matching version tag. It uses the release environment's `PSYZINE_KEYSTORE_BASE64`, `PSYZINE_KEY_ALIAS`, `PSYZINE_STORE_PASSWORD`, and `PSYZINE_KEY_PASSWORD` secrets. The existing reference-content publication gate `CONTENT_RIGHTS_VERIFIED=true` remains. Signing keys are removed after the job.

For authorized pushes to `main`, a successful complete `Android checks` run creates a testing pre-release with all four APKs, signature reports, SHA-256 checksums, build results and the corresponding source archive. Download links are on https://github.com/gurling/PsyZine/releases. Failed build or emulator jobs prevent this publication.

## Reproduce and verify publication

Use the existing configured SSH origin. Push the reviewed feature-branch commit to `main` only with explicit authorization and without force. Verify the remote hash, every required Actions job and the actual release assets. Download the published files and run `sha256sum --check SHA256SUMS`; inspect their signing certificates before installation. A missing or failed workflow is not completion.
