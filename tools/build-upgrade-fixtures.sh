#!/usr/bin/env bash
# Build only emulator fixtures from reviewed history. PsyZine, GPL-3.0-or-later.
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"
[[ "${PSYZINE_SIGNING_MODE:-compatibility}" == compatibility ]] || { echo 'Upgrade acceptance requires retained signing.' >&2; exit 1; }
fixture_dir="$(mktemp -d "${TMPDIR:-/tmp}/psyzine-upgrade-build.XXXXXX")"
trap 'rm -rf "$fixture_dir"' EXIT
mkdir -p artifacts/upgrade
# No worktree or history mutations. Pin the full reviewed object, not a moving branch.
git archive 574831afb7ecd4c75ffab3f37b4c775f245e3596 | tar -x -C "$fixture_dir"
cp app/src/androidTest/java/com/isaakhanimann/journal/psyzine/InPlaceUpgradeTest.kt "$fixture_dir/app/src/androidTest/java/com/isaakhanimann/journal/psyzine/"
python3 - "$fixture_dir" <<'PY'
from pathlib import Path
import sys
p = Path(sys.argv[1]) / 'app/build.gradle.kts'
s = p.read_text()
for old, new in [('storePassword = "android"', 'storePassword = System.getenv("PSYZINE_COMPAT_STORE_PASSWORD") ?: "android"'),
                 ('keyAlias = "androiddebugkey"', 'keyAlias = System.getenv("PSYZINE_COMPAT_KEY_ALIAS") ?: "androiddebugkey"'),
                 ('keyPassword = "android"', 'keyPassword = System.getenv("PSYZINE_COMPAT_KEY_PASSWORD") ?: "android"')]:
    if old not in s: raise SystemExit('Reviewed baseline signing configuration has drifted')
    s = s.replace(old, new)
s = s.replace('keyPassword = System.getenv("PSYZINE_COMPAT_KEY_PASSWORD") ?: "android"',
              'keyPassword = System.getenv("PSYZINE_COMPAT_KEY_PASSWORD") ?: "android"\n                storeType = java.security.KeyStore.getInstance(checkNotNull(storeFile), checkNotNull(storePassword).toCharArray()).type')
s += '\ndependencies { androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0") }\n'
p.write_text(s)
PY
baseline_key="${PSYZINE_COMPAT_KEYSTORE_FILE:-${PSYZINE_DEVELOPMENT_KEYSTORE:-$project_dir/.local/signing/psyzine-development.jks}}"
(
    cd "$fixture_dir"
    PSYZINE_DEVELOPMENT_KEYSTORE="$baseline_key" ./gradlew --no-daemon --console=plain \
        :app:assembleDirectDebug :app:assembleDirectDebugAndroidTest "$@"
) 2>&1 | tee artifacts/upgrade/baseline-build.log
cp "$fixture_dir/app/build/outputs/apk/direct/debug/app-direct-debug.apk" artifacts/upgrade/baseline-code1.apk
cp "$fixture_dir/app/build/outputs/apk/androidTest/direct/debug/app-direct-debug-androidTest.apk" artifacts/upgrade/upgrade-test.apk
./gradlew --no-daemon --console=plain :app:assembleDirectCompatibleRelease "$@" 2>&1 | tee artifacts/upgrade/candidate-build.log
cp app/build/outputs/apk/direct/compatibleRelease/app-direct-compatibleRelease.apk artifacts/upgrade/candidate.apk
# Override only for an unpublished follow-up fixture; never package code3 for publication with code2 metadata.
code="$(python3 -c 'import sys; sys.path.insert(0,"tools"); from release_policy import read_version; print(read_version()[0]+1)')"
./gradlew --no-daemon --console=plain :app:assembleDirectCompatibleRelease "-PpsyzineVersionCode=$code" "$@" 2>&1 | tee artifacts/upgrade/next-build.log
cp app/build/outputs/apk/direct/compatibleRelease/app-direct-compatibleRelease.apk artifacts/upgrade/next-candidate.apk
# Restore the ordinary code from version.properties in the Gradle output tree.
./gradlew --no-daemon --console=plain :app:assembleDirectCompatibleRelease "$@" 2>&1 | tee artifacts/upgrade/restore-build.log
