#!/usr/bin/env bash
# Non-destructive, emulator-only installed-update checks. PsyZine, GPL-3.0-or-later.
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"
[[ $# == 5 ]] || { echo "Usage: $0 EMULATOR_SERIAL BASELINE_APK TEST_APK CANDIDATE_APK NEXT_APK" >&2; exit 1; }
serial="$1"; baseline="$2"; test_apk="$3"; candidate="$4"; next="$5"
[[ "$serial" =~ ^emulator-[0-9]+$ ]] || { echo 'Physical devices are forbidden.' >&2; exit 1; }
adb_cmd=("${ANDROID_HOME:?}/platform-tools/adb" -s "$serial")
[[ "$("${adb_cmd[@]}" shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || { echo 'Not an emulator.' >&2; exit 1; }
installed="$("${adb_cmd[@]}" shell pm list packages app.psyzine.debug | tr -d '\r')"
[[ -z "$installed" ]] || { echo 'Use a fresh emulator. Existing installation left intact.' >&2; exit 1; }
# Check packages, signatures, code progression and non-debuggable candidates BEFORE installing or seeding.
python3 - "$baseline" "$test_apk" "$candidate" "$next" <<'PY'
import json, sys
from pathlib import Path
sys.path.insert(0, 'tools')
from apk_inspection import inspect_apk
from release_policy import pins, read_version
baseline, test, candidate, following = [inspect_apk(Path(p)) for p in sys.argv[1:]]
code, base = read_version()
for apk in (baseline, candidate, following):
    if apk['applicationId'] != 'app.psyzine.debug' or apk['signerCertificateSha256'] != [pins()['app.psyzine.debug']]:
        raise SystemExit('Wrong fixture package or certificate')
if not ((baseline['versionCode'] == 1 and baseline['debuggable']) or (baseline['versionCode'] == 2 and not baseline['debuggable'])):
    raise SystemExit('Baseline must be reviewed code1 Direct Debug or minified code2 compatibility')
if candidate['versionCode'] != code or following['versionCode'] != code + 1 or code <= baseline['versionCode']:
    raise SystemExit('Candidate codes do not prove two increasing updates')
if candidate['debuggable'] or following['debuggable']:
    raise SystemExit('Compatibility updates must be non-debuggable')
if (candidate['versionName'], following['versionName']) != (f'{base}-compat.{code}', f'{base}-compat.{code+1}'):
    raise SystemExit('Wrong compatibility version names')
if test['applicationId'] != 'app.psyzine.debug.test' or test['signerCertificateSha256'] != baseline['signerCertificateSha256']:
    raise SystemExit('Upgrade instrumentation must share the retained identity')
Path('artifacts/upgrade').mkdir(parents=True, exist_ok=True)
Path('artifacts/upgrade/FIXTURE_APKS.json').write_text(json.dumps([baseline, test, candidate, following], indent=2) + '\n')
print('Verified retained certificate, package IDs and increasing fixture codes before emulator writes.')
PY
mkdir -p artifacts/upgrade
capture_failure() {
    local upgrade_result="$1"
    if [[ "$upgrade_result" != 0 ]]; then
        # This script has already verified a fresh synthetic emulator. Keep failure
        # evidence before emulator-runner shuts it down; never read a physical journal.
        mkdir -p artifacts/upgrade/diagnostics
        timeout 20 "${adb_cmd[@]}" shell uiautomator dump /sdcard/psyzine-upgrade-failure.xml >/dev/null 2>&1 || true
        timeout 10 "${adb_cmd[@]}" pull /sdcard/psyzine-upgrade-failure.xml artifacts/upgrade/diagnostics/window.xml >/dev/null 2>&1 || true
        timeout 10 "${adb_cmd[@]}" exec-out screencap -p > artifacts/upgrade/diagnostics/screen.png || true
        timeout 10 "${adb_cmd[@]}" logcat -d -t 2000 > artifacts/upgrade/diagnostics/logcat.log || true
    fi
    exit "$upgrade_result"
}
trap 'capture_failure "$?"' EXIT
"${adb_cmd[@]}" install "$baseline"
"${adb_cmd[@]}" install "$test_apk"
run_test() {
    local method="$1" code="$2" schema="$3" output_file="$4"
    "${adb_cmd[@]}" shell am instrument -w --no-window-animation \
        -e class "com.isaakhanimann.journal.psyzine.InPlaceUpgradeTest#$method" \
        -e psyzineUpgradeFixture true -e expectedVersionCode "$code" -e expectedSchemaVersion "$schema" \
        app.psyzine.debug.test/androidx.test.runner.AndroidJUnitRunner | tee "$output_file"
    # am instrument may exit zero for a failing test, so require its explicit result.
    python3 - "$output_file" <<'PY'
from pathlib import Path
import re, sys
result = Path(sys.argv[1]).read_text()
if not re.search(r'^OK \(1 test\)', result, re.MULTILINE) or 'FAILURES!!!' in result or 'INSTRUMENTATION_FAILED' in result:
    raise SystemExit('Upgrade instrumentation did not pass exactly one test')
PY
}
baseline_code="$(python3 -c 'import json; print(json.load(open("artifacts/upgrade/FIXTURE_APKS.json"))[0]["versionCode"])')"
run_test seedVersionOneFixture "$baseline_code" 8 artifacts/upgrade/seed.log
run_test verifyRetainedFixtureAndKeystore "$baseline_code" 8 artifacts/upgrade/baseline-verify.log
if [[ "${PSYZINE_BENCHMARK_STARTUP:-false}" == true ]]; then
    python3 tools/benchmark-startup.py "$serial" --fixture-confirmed --output "artifacts/upgrade/startup-code${baseline_code}.json"
fi
code="$(python3 -c 'import sys; sys.path.insert(0,"tools"); from release_policy import read_version; print(read_version()[0])')"
for apk in "$candidate" "$next"; do
    "${adb_cmd[@]}" shell am force-stop app.psyzine.debug
    # No uninstall, data clearing, downgrade flag or signature workaround.
    "${adb_cmd[@]}" install -r "$apk"
    run_test verifyRetainedFixtureAndKeystore "$code" 9 "artifacts/upgrade/code${code}-verify.log"
    run_test upgradedJournalPhotosAndBackupWork "$code" 9 "artifacts/upgrade/code${code}-smoke.log"
    # Include the post-UI state in the retention check too.
    "${adb_cmd[@]}" shell am force-stop app.psyzine.debug
    run_test verifyRetainedFixtureAndKeystore "$code" 9 "artifacts/upgrade/code${code}-post-ui.log"
    if [[ "${PSYZINE_BENCHMARK_STARTUP:-false}" == true ]]; then
        python3 tools/benchmark-startup.py "$serial" --fixture-confirmed --output "artifacts/upgrade/startup-code${code}.json"
    fi
    code=$((code + 1))
done
printf 'Both emulator upgrades preserved data, photos, preferences, UID and Keystore decryption.\n'
