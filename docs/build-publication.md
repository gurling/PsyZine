# Android builds, signing and GitHub publication

Repository: https://github.com/gurling/PsyZine. The Android project is the repository root. The local checkout is `/home/izzy/codex/PsyZine/android`; upstream history and GPL attribution are retained.

## Recorded execution result — 11 October 2026

Actual Gradle 8.14 execution was attempted for all four APK variants, both flavor unit suites and all four lint tasks, using JDK 17. The first native-library error came from the read-only default `/home/izzy/.gradle` cache. Moving `GRADLE_USER_HOME` to a writable directory resolved that error. The wrapper also lacked its executable bit; this is now corrected and committed with the scripts. The checked-in wrapper was then executed using its checksum-verified official distribution cache.

Both the Gradle binary and the real wrapper stop before project evaluation:

```text
Could not create service of type FileLockContentionHandler.
Could not determine a usable wildcard IP for this machine.
java.net.SocketException: Operation not permitted (Socket creation failed)
```

The sandbox denies INET socket creation, including Gradle's local file-lock communication. This is an execution-policy restriction, not an Android compilation result. No source, packaging, dependency-resolution or lint pass is inferred. The all-variant attempt is retained in the ignored `build-logs/gradle-build.log`. No APK was generated; APK sizes/checksums and installation results are unavailable. The packaging command was checked to reject failed Gradle evidence and create no downloadable APK output.

Git origin and main tracking are configured exactly as requested, preserving upstream. The public GitHub API confirms `gurling/PsyZine` exists with default branch `main`. A subsequent check found its license-only initialization commit `e42537dd1d1dae20c0f2c63b66db0f8c8a208b94`. Its GPL license, tree and signed commit were retrieved through the public HTTPS API, verified against their exact Git object hashes, and merged locally with both histories retained. This makes the local branch a descendant of the observed remote branch; no force push is needed. SSH initially rejected invalid permissions on the system SSH configuration. Using `ssh -F /dev/null` bypassed that broken configuration without changing host-key verification. Resolving GitHub's address through HTTPS then exposed the underlying `socket: Operation not permitted` failure. SSH authentication and push cannot proceed in this execution environment. No HTTPS GitHub credential or authenticated browser is available. An Actions dispatch attempt returned HTTP 401, and the public Actions API reported no runs. The actual `git push -u origin main` attempt failed at socket creation before authentication. The remote initialization commit is verified, but none of the PsyZine implementation commits is published. The latest Actions API check reports zero runs; no successful workflow or downloadable workflow artifact exists.

Git's configured GPG signing also failed because its keybox service needs writes under the read-only `/home/izzy/.gnupg`. The local initialization commit uses a per-command `commit.gpgsign=false` override; global signing preferences and private GPG keys are untouched. This Git signature limitation is separate from APK signing.

An actual `adb devices -l` attempt also failed: the daemon could not install its TCP listener (`Operation not permitted`), and `/dev/bus/usb` was unavailable. No device installation or instrumentation execution is claimed.

Completed work is committed locally; its hash and any failed push are reported separately. A Git bundle and corresponding source archive can carry the committed project to a host where SSH and Gradle sockets are permitted. This record does not claim publication has completed.

## Build all installable variants

Install JDK 17, Android SDK platform 35, build-tools 35.0.0 and Android command-line tools. Set `JAVA_HOME` and `ANDROID_HOME` to those installations. If the default Gradle cache is unwritable, set `GRADLE_USER_HOME` to a writable directory. Use an environment that permits Gradle's local sockets and dependency downloads:

```bash
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
bash tools/build-apks.sh
```

This runs the actual wrapper, builds Direct/Play Debug/Release, runs both flavor unit suites and all four lint tasks, then verifies every APK. A failed Gradle task prevents publication of the downloadable artifact set. `verify-apks.py` also requires successful task evidence, nonempty passing JUnit XML and zero-error lint XML; it does not substitute source compilation for Gradle.

Verification checks ZIP integrity, dex/manifest presence, application IDs, version code 1, version 1.0.0 or the explicitly labeled 1.0.0-dev release, min/target SDK 26/35, forbidden permissions, APK signatures with SDK `apksigner`, and 16 KiB native-library alignment with `zipalign`. It records certificate digests, byte sizes and SHA-256 checksums. Direct/Play certificates must agree for each build type. Actual device installation remains a separate test.

Expected outputs **after a successful build**, not artifacts already produced here:

| Variant | Gradle output | Downloadable development-signed copy | Application ID |
| --- | --- | --- | --- |
| Direct Debug | `app/build/outputs/apk/direct/debug/app-direct-debug.apk` | `artifacts/apks/PsyZine-Direct-Debug-v1.0.0.apk` | `app.psyzine.debug` |
| Direct Release | `app/build/outputs/apk/direct/release/app-direct-release.apk` | `artifacts/apks/PsyZine-Direct-Release-v1.0.0-dev.apk` | `app.psyzine` |
| Play Debug | `app/build/outputs/apk/play/debug/app-play-debug.apk` | `artifacts/apks/PsyZine-Play-Debug-v1.0.0.apk` | `app.psyzine.play.debug` |
| Play Release | `app/build/outputs/apk/play/release/app-play-release.apk` | `artifacts/apks/PsyZine-Play-Release-v1.0.0-dev.apk` | `app.psyzine.play` |

`artifacts/apks/BUILD_RESULTS.json`, `BUILD_RESULTS.md`, signature reports and `SHA256SUMS` accompany the APKs. Production release copies use version 1.0.0 without `-dev`. Source URLs default to the actual fork; `-PpsyzineSourceUrl=https://github.com/OWNER/REPO` overrides them for a fork.

## Signing and upgrades

Ordinary release builds use development signing and version suffix `-dev`, while retaining their installation IDs. The build script generates/reuses `.local/signing/psyzine-development.jks` (ignored, owner-only), with alias `androiddebugkey` and the standard development-only password `android`. Set `PSYZINE_DEVELOPMENT_KEYSTORE` to use a different external location. This is not a production signing credential. A valid development certificate was generated and checked with `keytool`; it has not signed an APK in this environment.

For production signing set all four environment variables: `PSYZINE_RELEASE_KEYSTORE_FILE`, `PSYZINE_KEY_ALIAS`, `PSYZINE_STORE_PASSWORD`, `PSYZINE_KEY_PASSWORD`. The keystore must already exist outside version control. Partial configuration fails rather than silently falling back. Gradle signs both release distributions with that key and uses version 1.0.0. Never commit keys, credential files or passwords.

An update requires the same application ID and signing certificate. A development-signed release cannot upgrade a production installation with a different certificate, or later be upgraded in place to a differently signed production release. Export test data before switching signing identities; use isolated test installations. Retain the local development key for repeatable local testing. CI generates a fresh ephemeral development key unless the optional `PSYZINE_DEVELOPMENT_KEYSTORE_BASE64` secret supplies a stable key with the standard alias/password. Without that secret, APKs from different CI runs may require reinstalling. Production signing continuity requires secure retention of the independent production key.

## Continuous integration

`Android checks` runs on push, pull request and manual dispatch. It builds/tests/lints all four signed variants, verifies them, uploads `PsyZine-development-APKs-<commit>` with checksums/results and corresponding source, and separately uploads build/test/lint logs. The workflow summary shows APK IDs, byte sizes and SHA-256 values only after actual successful Gradle execution and verification. Fork pull requests do not receive repository secrets; they can use ephemeral development signing. The production key is never used by this workflow.

The AOSP instrumentation matrix covers API 26/29/30/32/33/35; dispatch 36/37 separately once their images are available. Reports and synthetic palette captures are separate per-device artifacts. Camera hardware, reboot/process recovery, tablet and accessibility checks follow [verification.md](verification.md).

`PsyZine signed release` decodes the independent production key into an owner-only runner-temp file, uses Gradle signing, verifies the production candidate, packages corresponding source/checksums and publishes only on a matching version tag. It uses the release environment's `PSYZINE_KEYSTORE_BASE64`, `PSYZINE_KEY_ALIAS`, `PSYZINE_STORE_PASSWORD`, and `PSYZINE_KEY_PASSWORD` secrets. The existing reference-content publication gate `CONTENT_RIGHTS_VERIFIED=true` remains. Signing keys are removed after the job.

Workflow status and actual downloadable artifacts, once a push succeeds, are under https://github.com/gurling/PsyZine/actions. No successful run exists at the recorded execution time.

## Complete the blocked transport on an unrestricted host

The checkout already uses main, the specified SSH origin, and retained upstream. Push the committed tree without rewriting history:

```bash
cd /home/izzy/codex/PsyZine/android
git push -u origin main
git ls-remote origin refs/heads/main
```

If the host has the same broken system SSH configuration, use `GIT_SSH_COMMAND='ssh -F /dev/null -o StrictHostKeyChecking=yes'` after confirming GitHub's host key is in `known_hosts`. Do not disable host-key verification. Verify the remote hash equals local HEAD, monitor the actual `Android checks` run, resolve any real Gradle/lint/runtime failures, then download and verify its APK artifact checksums. A failed or absent workflow is not completion.
