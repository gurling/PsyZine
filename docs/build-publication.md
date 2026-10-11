# Android builds, signing and GitHub publication

Repository: https://github.com/gurling/PsyZine. The Android project is the repository root. The local checkout is `/home/izzy/codex/PsyZine/android`; upstream history and GPL attribution are retained.

## Recorded execution result — 11 October 2026

The former socket restriction no longer reproduces: IPv4 sockets, the actual Gradle wrapper, ADB and authenticated SSH now work. All four Direct/Play Debug/Release APKs were built with JDK 17, Gradle 8.14, SDK 35 and explicitly pinned build-tools 35.0.0. Both flavor JVM suites passed (35 tests each). All four lint tasks passed with zero errors; dependency/unused-resource warnings remain. Minification, resource linking, manifest merging, APK signatures, ZIP integrity, application IDs and alignment were checked. All four local APKs installed and launched on an isolated Pixel test installation.

Android instrumentation passed all 23 tests on each CI emulator at API 26/29/30/32/33/35 in [run 38136387190](https://github.com/gurling/PsyZine/actions/runs/38136387190). That run's separate build job failed, so it did not publish a release; the updated workflow limits workers, compiles Kotlin in process, and exposes final build-log errors. The initial idle Pixel/API 37 run passed 22/23 tests. Its editor-save test missed a touch: Android logged that the launching window was below its opacity threshold. The isolated editor test then passed with `am instrument --no-window-animation`; adding `testOptions.animationsDisabled = true` made the full actual Gradle instrumentation suite pass 23/23 with zero skips. Gradle restored the phone animation settings afterward. This includes Room migrations and photo/backup checks. Thirteen synthetic palette captures were generated; complete visual acceptance remains separate.

Source commits have been pushed without force to the requested SSH origin and verified through GitHub's API. The repository was not empty: its initial license commit and upstream history were preserved. Work remains on a local feature branch, pushed explicitly to remote `main` under the user's publication authorization. No Git remotes or credentials were changed. The broken system SSH configuration is bypassed per command with `ssh -F /dev/null -o BatchMode=yes -o StrictHostKeyChecking=yes`; known-host verification remains enabled. Git commits use a per-command GPG-signing override because the configured keybox directory is read-only.

The required release evidence is the successful complete [Actions run](https://github.com/gurling/PsyZine/actions) and its actual [testing pre-release assets](https://github.com/gurling/PsyZine/releases), rather than this document or the workflow configuration. The pre-release job runs only after both the build job and every required emulator job succeed. Production signing credentials, reference-content clearance and full manual acceptance remain outstanding. Development publication does not establish production readiness or resolve third-party content rights.

## Compatibility candidate and version policy

The compatibility remediation retains `app.psyzine.debug` and certificate SHA-256 `7a8752f2571342ec6b9b0bb767e0b880e682ca35006fff8f01b4891d0bbd86b5`. The version source is `version.properties`: initially code `2`, name `1.0.1`. The compatibility APK name/version is `1.0.1-compat.2`, tag `v1.0.1-compat.2`, release title `PsyZine Compatibility 1.0.1-compat.2`, canonical asset `PsyZine-Direct-Compatible-Release-v1.0.1-compat.2.apk`. Publication requires a code greater than every previously published code for every affected package; missing historical metadata or download failures block publication for review. Two clean builds of one candidate share a code; they produce only one release. Increment the code in source before the next publication.

`config/signing-policy.json` pins all four package IDs to this retained identity. Published historical CI certificates differed; matching package IDs alone cannot update those installations. No key rotation is proposed. The original local key files must remain unchanged.

## Build and inspect the five testing variants

Use JDK17, SDK35/build-tools35.0.0 and the wrapper. Set `JAVA_HOME`, `ANDROID_HOME` and a writable `GRADLE_USER_HOME`. An existing `.local/signing/psyzine-development.jks` can be used locally, or set all four protected-container variables below. Missing/unreadable keys, incomplete credentials and wrong certificates fail without replacement generation.

```bash
bash tools/build-apks.sh --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process
python3 tools/verify-obtainium.py --assets-dir artifacts/apks
```

| Variant | Gradle APK path under `app/build/outputs/apk/` | Application ID | Version |
| --- | --- | --- | --- |
| Direct Debug | `direct/debug/app-direct-debug.apk` | `app.psyzine.debug` | `1.0.1` |
| Direct Release | `direct/release/app-direct-release.apk` | `app.psyzine` | `1.0.1-dev` |
| Play Debug | `play/debug/app-play-debug.apk` | `app.psyzine.play.debug` | `1.0.1` |
| Play Release | `play/release/app-play-release.apk` | `app.psyzine.play` | `1.0.1-dev` |
| Direct Compatible Release | `direct/compatibleRelease/app-direct-compatibleRelease.apk` | `app.psyzine.debug` | `1.0.1-compat.2` |

The compatible build uses release minification/resource shrinking, `debuggable=false`, and `release` dependency fallbacks. Namespace, FileProvider authority, schema history, storage and all 13 themes remain intact. It cannot coexist with Direct Debug because they deliberately share the installed identity.

Packaging requires successful Gradle task evidence, nonempty passing XML for all three unit suites, zero-error lint for all five variants, expected versions/IDs/debuggable flags, SDK26/35, privacy permission checks, SDK signature verification, one retained signer and 16KiB alignment. `BUILD_RESULTS.json` records signing mode and publication eligibility explicitly. `SHA256SUMS` also covers metadata/signature reports. Installation is a separate check.

Untrusted CI uses `PSYZINE_SIGNING_MODE=unpublished`, an explicit disposable `PSYZINE_DEVELOPMENT_KEYSTORE` path and `prepare-development-signing.sh --unpublished-ci`. The disposable key is generated only at that separate path and never for publishable builds. No persistent secrets are provided to ordinary checks or pull requests. Disposable results are marked `publicationAllowed=false` and are rejected by the publication gate.

## Protected compatibility signing and approvals

Before any key re-protection/upload, obtain owner approval. Copy the existing private key and certificate into a strongly password-protected container without generating a key or changing the certificate, verify the fingerprint before/after, and retain two encrypted offline backups. Do not modify the original containers. Offline backup completion is an owner check; this agent cannot attest to it.

Configure a trusted GitHub Actions **compatibility** environment restricted to reviewed `main` code, with required owner review. This repository has one collaborator, so self-review is allowed for the owner to approve their own dispatch; admin bypass is disabled. Store these environment secrets:

- `PSYZINE_COMPAT_KEYSTORE_BASE64`
- `PSYZINE_COMPAT_KEY_ALIAS`
- `PSYZINE_COMPAT_STORE_PASSWORD`
- `PSYZINE_COMPAT_KEY_PASSWORD`

An approved owner can run `tools/reprotect-signing-key.py --approved-copy --source .local/signing/psyzine-development.jks --destination .local/signing/psyzine-compat.p12` after setting the three `PSYZINE_COMPAT_*` alias/password variables. Use a randomly generated password of at least 24 characters; PKCS12 requires matching store/key passwords. The helper checks the source/copy certificate and original container hash, uses an exclusive destination, and never generates a key. After explicit owner approval, this helper created `.local/signing/psyzine-compat.p12` on 11 October 2026. The retained certificate, exact private key and a signing challenge were verified in memory; both original containers remained unchanged. Protected-container credentials are retained in ignored owner-only `.local/signing/compatibility-credentials.json` and were not printed. Two independently AES-256-encrypted recovery archives in `.local/signing/offline-backups/` were fully decrypted and compared in memory. On owner instruction, archive 1 was copied to `/home/izzy/codex/PsyZine/signing-backups/` and archive 2 to `/media/ADATA-2TB/Backups/android-app-signing-backups/`, each with owner-only permissions and matching SHA-256. The first location is on the workstation; the ADATA copy becomes offline when the drive is disconnected. Separate recovery-password custody and offline storage remain owner checks. The recovery password is in ignored owner-only `.local/signing/backup-recovery-password.txt`, separate from the copied archives. Authenticated owner-directed setup created the `compatibility` environment on 11 October 2026, verified its `main`-branch restriction, required `gurling` review and disabled admin bypass, and uploaded all four named environment secrets. Uploads used locally encrypted GitHub CLI requests with values supplied in memory through stdin; only names were displayed.

Local protected-container signing uses `PSYZINE_COMPAT_KEYSTORE_FILE` (path), plus the same alias and password variables. The workflow restores an owner-only runner-temp file, checks its public pin and PrivateKeyEntry, and Gradle verifies private-key access. Missing secrets, wrong passwords or certificate mismatches stop the build. Runner files are removed at job completion; the persistent secret is retained. Never commit credentials or print them.

The owner-approved signing setup and local GitHub authentication authorized the environment/secret setup above. Source push, release publication and a phone upgrade remain separate approval gates. Signing-identity changes, production keys, destructive migration and key rotation remain outside scope. Re-protection/upload and the final owner-supervised phone upgrade require separate approval as specified in the plan.

## CI and explicitly approved publication

`Android checks` runs unpublished build/test/lint plus ordinary instrumentation on API26/29/30/32/33/35 for pushes, PRs and dispatch. Extra APIs36/37 remain optional. Pushes to `main` no longer automatically publish.

An approved dispatch with `publish_compatibility=true` on `main` additionally uses the protected environment to run two independent clean five-variant builds and six emulator-only code1 → candidate → next-code upgrade jobs. The code1 fixture comes from reviewed full commit `574831afb7ecd4c75ffab3f37b4c775f245e3596`, with only emulator test harness/dependency/signing overrides. The follow-up code3 is an unpublished fixture. Successful ordinary checks, the complete instrumentation matrix, both builds and all upgrade jobs are prerequisites for publication.

`verify-publication.py` checks downloaded candidate checksums and real manifests/certificates, compares both builds' identities/commit, downloads every previously published release APK/report/checksum, verifies their actual version metadata, rejects stale codes and existing candidate tags, and records historical evidence. Network/auth failures stop the job. `verify-obtainium.py` decodes the actual README deep link and checks it selects exactly one compatible APK among all five variants and supplementary assets. The publisher includes matching GPL source and re-downloads the actual release assets for checksum/signature/selection checks. Obtainium's actual consecutive phone updates remain a separate owner acceptance check.

Production uses the existing **release** environment and `PSYZINE_KEYSTORE_BASE64`, `PSYZINE_KEY_ALIAS`, `PSYZINE_STORE_PASSWORD`, `PSYZINE_KEY_PASSWORD`; credentials are unchanged. `PSYZINE_SIGNING_MODE=production` is explicit. Pre-release tags are excluded; versions/filenames are dynamic. The `CONTENT_RIGHTS_VERIFIED=true` gate is retained and package certificate pins still apply. A different production certificate cannot silently replace this existing identity; separately approve any policy/migration change. Manual dispatch builds a candidate; only a matching stable version tag publishes.

Before requesting a source push, show the branch/destination, commits, change summary and checks. Do not push or publish until explicitly approved. No release has been published by the source implementation itself. See [verification.md](verification.md) for current execution results and gaps.
