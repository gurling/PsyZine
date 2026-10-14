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
for pinned in 574831afb7ecd4c75ffab3f37b4c775f245e3596 0f1249d0d52868c8ef42776828598675474e03ea; do
    git cat-file -e "$pinned^{commit}" || { echo "Pinned upgrade baseline is missing. Checkout must fetch full history." >&2; exit 1; }
done
git archive 574831afb7ecd4c75ffab3f37b4c775f245e3596 | tar -x -C "$fixture_dir"
python3 - "$fixture_dir" <<'PY'
from pathlib import Path
import sys
p = Path(sys.argv[1]) / 'app/build.gradle.kts'
s = "import java.security.KeyStore\n" + p.read_text()
for old, new in [('storePassword = "android"', 'storePassword = System.getenv("PSYZINE_COMPAT_STORE_PASSWORD") ?: "android"'),
                 ('keyAlias = "androiddebugkey"', 'keyAlias = System.getenv("PSYZINE_COMPAT_KEY_ALIAS") ?: "androiddebugkey"'),
                 ('keyPassword = "android"', 'keyPassword = System.getenv("PSYZINE_COMPAT_KEY_PASSWORD") ?: "android"')]:
    if old not in s: raise SystemExit('Reviewed baseline signing configuration has drifted')
    s = s.replace(old, new)
s = s.replace('keyPassword = System.getenv("PSYZINE_COMPAT_KEY_PASSWORD") ?: "android"',
              'keyPassword = System.getenv("PSYZINE_COMPAT_KEY_PASSWORD") ?: "android"\n                storeType = KeyStore.getInstance(checkNotNull(storeFile), checkNotNull(storePassword).toCharArray()).type')
p.write_text(s)
PY
baseline_key="${PSYZINE_COMPAT_KEYSTORE_FILE:-${PSYZINE_DEVELOPMENT_KEYSTORE:-$project_dir/.local/signing/psyzine-development.jks}}"
(
    cd "$fixture_dir"
    PSYZINE_DEVELOPMENT_KEYSTORE="$baseline_key" ./gradlew --no-daemon --console=plain \
        :app:assembleDirectDebug "$@"
) 2>&1 | tee artifacts/upgrade/baseline-build.log
cp "$fixture_dir/app/build/outputs/apk/direct/debug/app-direct-debug.apk" artifacts/upgrade/baseline-code1.apk
bash tools/build-upgrade-harness.sh "$@"
# Additional reviewed minified code2 baseline (schema 8), built from its exact source.
code2_dir="$fixture_dir/code2"
mkdir -p "$code2_dir"
git archive 0f1249d0d52868c8ef42776828598675474e03ea | tar -x -C "$code2_dir"
(
    cd "$code2_dir"
    PSYZINE_DEVELOPMENT_KEYSTORE="$baseline_key" ./gradlew --no-daemon --console=plain :app:assembleDirectCompatibleRelease "$@"
) 2>&1 | tee artifacts/upgrade/baseline-code2-build.log
cp "$code2_dir/app/build/outputs/apk/direct/compatibleRelease/app-direct-compatibleRelease.apk" artifacts/upgrade/baseline-code2.apk
# Use the actual published minified schema-9 APK, pinned independently of a moving release.
code3_dir="$fixture_dir/code3"
mkdir -p "$code3_dir"
read -r code3_tag code3_file < <(python3 -c 'import json; p=json.load(open("config/upgrade-baselines.json"))["code3"]; print(p["tag"], p["filename"])')
gh release download "$code3_tag" --repo gurling/PsyZine --pattern "$code3_file" --pattern BUILD_RESULTS.json --pattern SHA256SUMS --dir "$code3_dir"
python3 - "$code3_dir" <<'PY'
from pathlib import Path
import json, sys
sys.path.insert(0, 'tools')
from apk_inspection import inspect_apk, verify_checksums
from release_policy import pins
p = json.load(open('config/upgrade-baselines.json'))['code3']
directory = Path(sys.argv[1])
verify_checksums(directory, allow_missing=True)
report = json.loads((directory / 'BUILD_RESULTS.json').read_text())
actual = inspect_apk(directory / p['filename'])
record = next(r for r in report['apks'] if r['filename'] == p['filename'])
if report['commit'] != p['commit'] or any(actual[k] != record[k] for k in actual):
    raise SystemExit('Published baseline source/report mismatch')
if (actual['sha256'] != p['sha256'] or actual['applicationId'] != 'app.psyzine.debug' or
        actual['versionCode'] != 3 or actual['versionName'] != '1.0.2-compat.3' or actual['debuggable'] or
        actual['signerCertificateSha256'] != [pins()['app.psyzine.debug']]):
    raise SystemExit('Published baseline identity mismatch')
PY
cp "$code3_dir/$code3_file" artifacts/upgrade/baseline-code3.apk
./gradlew --no-daemon --console=plain :app:assembleDirectCompatibleRelease "$@" 2>&1 | tee artifacts/upgrade/candidate-build.log
cp app/build/outputs/apk/direct/compatibleRelease/app-direct-compatibleRelease.apk artifacts/upgrade/candidate.apk
# Override only for an unpublished follow-up fixture; never publish it.
code="$(python3 -c 'import sys; sys.path.insert(0,"tools"); from release_policy import read_version; print(read_version()[0]+1)')"
./gradlew --no-daemon --console=plain :app:assembleDirectCompatibleRelease "-PpsyzineVersionCode=$code" "$@" 2>&1 | tee artifacts/upgrade/next-build.log
cp app/build/outputs/apk/direct/compatibleRelease/app-direct-compatibleRelease.apk artifacts/upgrade/next-candidate.apk
# Restore the ordinary code from version.properties in the Gradle output tree.
./gradlew --no-daemon --console=plain :app:assembleDirectCompatibleRelease "$@" 2>&1 | tee artifacts/upgrade/restore-build.log
