#!/usr/bin/env bash
# PsyZine, 2026. GPL-3.0-or-later.
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd -- "$project_dir"
serial="${1:?Pass the serial of a fresh Android 16+ emulator}"
case "$serial" in emulator-*) ;; *) echo 'This check requires a fresh emulator.' >&2; exit 2 ;; esac
mkdir -p build-logs
exec > >(tee build-logs/large-text-harness.log) 2>&1
trap 'echo "Large-text harness failed at line $LINENO" >&2' ERR
adb_device=(adb -s "$serial")
kernel_qemu="$("${adb_device[@]}" shell getprop ro.kernel.qemu | tr -d '\r')"
boot_qemu="$("${adb_device[@]}" shell getprop ro.boot.qemu | tr -d '\r')"
echo "Emulator markers: kernel=$kernel_qemu boot=$boot_qemu"
[[ "$kernel_qemu" = 1 || "$boot_qemu" = 1 ]]
sdk="$("${adb_device[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
echo "Android SDK: $sdk"
[[ "$sdk" =~ ^[0-9]+$ ]] && (( sdk >= 36 ))
# `pm path` returns a failure status for the expected absent package. Listing
# packages succeeds on a fresh emulator and still propagates ADB/service errors.
existing_packages="$("${adb_device[@]}" shell pm list packages app.psyzine.debug | tr -d '\r')"
if [[ "$existing_packages" == *package:app.psyzine.debug* ]]; then
    echo 'Use a fresh emulator: an existing PsyZine installation must be retained.' >&2
    exit 2
fi
mkdir -p build-logs screenshots/large-text
"${adb_device[@]}" install app/build/outputs/apk/direct/debug/app-direct-debug.apk
"${adb_device[@]}" install app/build/outputs/apk/androidTest/direct/debug/app-direct-debug-androidTest.apk
"${adb_device[@]}" shell settings put secure show_ime_with_hard_keyboard 1
"${adb_device[@]}" shell pm grant app.psyzine.debug android.permission.POST_NOTIFICATIONS
for setting in window_animation_scale transition_animation_scale animator_duration_scale; do
    "${adb_device[@]}" shell settings put global "$setting" 0
done
"${adb_device[@]}" shell wm size 480x1066
test_class=com.isaakhanimann.journal.psyzine.LargeTextLayoutTest
methods=(
    doseReferenceFitsNarrowScreensAtLargeFonts
    settingsSwitchesRemainVisibleBesideWrappingLabels
    journalEntryKeepsFavoriteAndLongTitleVisible
    ingestionKeepsDosageDotsAndWrappedTextVisible
    calendarDatesFitAtLargeFonts
    statisticsPeriodsRemainReadableAtLargeFonts
    customUnitFieldsWrapAndLastSectionClearsDoneButton
    customUnitChooserKeepsAmountsAndBottomActionsVisible
    focusedDoseAndDoneButtonRemainAboveKeyboard
    ordinaryDoseFieldsKeepLongUnitsOutsideTheAmount
    customSubstanceDoseFieldsKeepLongUnitsOutsideTheAmount
    ingestionEditorFieldsKeepLongUnitsAndNotesReadable
    ratingDescriptionsRemainReadableAndSelectable
)
platform_classes=com.isaakhanimann.journal.psyzine.MainActivityLargeTextTest
for method in "${methods[@]}"; do platform_classes+=",$test_class#$method"; done
controlled_classes="$test_class,com.isaakhanimann.journal.psyzine.CustomUnitRegressionTest,com.isaakhanimann.journal.psyzine.UiAccessibilityRegressionTest"
run_profile() {
    local profile="$1" font="$2" density="$3" mode="$4"
    local classes="$controlled_classes" expected=38
    local args=() folders=(large-text-captures custom-unit-captures)
    if [[ "$mode" = platform ]]; then
        classes="$platform_classes"; expected=14
        args=(-e platformFontScale true -e expectedFontScale "$font")
        folders=(large-text-captures main-large-text-captures)
    fi
    local output="screenshots/large-text/$profile" log="build-logs/large-text-$profile.log"
    mkdir -p "$output"
    "${adb_device[@]}" shell wm density "$density"
    "${adb_device[@]}" shell settings put system font_scale "$font"
    "${adb_device[@]}" shell am force-stop app.psyzine.debug
    "${adb_device[@]}" shell run-as app.psyzine.debug rm -rf cache/large-text-captures cache/main-large-text-captures cache/custom-unit-captures
    "${adb_device[@]}" shell am instrument -w -r -e class "$classes" "${args[@]}" app.psyzine.debug.test/androidx.test.runner.AndroidJUnitRunner > "$log" 2>&1
    "${adb_device[@]}" shell wm size > "$output/configuration.txt"
    "${adb_device[@]}" shell wm density >> "$output/configuration.txt"
    "${adb_device[@]}" shell settings get system font_scale >> "$output/configuration.txt"
    "${adb_device[@]}" exec-out run-as app.psyzine.debug tar -C cache -cf - "${folders[@]}" > "$output/captures.tar"
    python3 - "$log" "$expected" "$output" <<'PY'
import json, re, sys, tarfile
from pathlib import Path
log, expected, output = Path(sys.argv[1]), int(sys.argv[2]), Path(sys.argv[3])
text = log.read_text()
with tarfile.open(output / 'captures.tar') as archive:
    archive.extractall(output, filter='data')
passed = f'OK ({expected} tests)' in text and not re.search(r'INSTRUMENTATION_STATUS_CODE: -[1-4]\b|FAILURES!!!', text)
(output / 'result.json').write_text(json.dumps({'expectedTests': expected, 'passed': passed}, indent=2) + '\n')
if not passed:
    raise SystemExit(f'{log}: expected {expected} passing tests with no skipped cases.\n{text[-7000:]}')
print(f'{output.name}: {expected} tests passed')
PY
}
run_profile platform-max 2.0 240 platform
run_profile platform-medium 1.3 195 platform
run_profile platform-large 1.5 240 platform
run_profile controlled 1.0 160 controlled
python3 - "$serial" "$sdk" <<'PY'
import hashlib, json, subprocess, sys
from pathlib import Path
files = ['app/build/outputs/apk/direct/debug/app-direct-debug.apk',
         'app/build/outputs/apk/androidTest/direct/debug/app-direct-debug-androidTest.apk']
record = {'serial': sys.argv[1], 'sdk': int(sys.argv[2]), 'passingTests': 80,
          'commit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip(),
          'apks': {p: hashlib.sha256(Path(p).read_bytes()).hexdigest() for p in files}}
Path('screenshots/large-text/verification.json').write_text(json.dumps(record, indent=2) + '\n')
PY
