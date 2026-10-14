#!/usr/bin/env bash
# Fresh CI emulator only. PsyZine, GPL-3.0-or-later.
set -euo pipefail
name="${1:?Pass a unique CI AVD name}"
[[ "$name" =~ ^PsyZine[A-Za-z0-9]+$ ]]
mkdir -p build-logs "${ANDROID_AVD_HOME:?}" "${ANDROID_USER_HOME:?}"
exec > >(tee "build-logs/emulator-setup-$name.log") 2>&1
echo no | avdmanager create avd --name "$name" --path "$ANDROID_AVD_HOME/$name.avd" --package "system-images;android-37.0;google_apis_ps16k;x86_64"
emulator_bin="${ANDROID_HOME:?}/emulator/emulator"
adb_bin="$ANDROID_HOME/platform-tools/adb"
"$emulator_bin" -version
test -f "$ANDROID_AVD_HOME/$name.ini"
"$emulator_bin" -avd "$name" -port 5554 -no-window -no-audio -no-boot-anim -no-snapshot -accel on -gpu software -memory 4096 -cores 2 > "build-logs/emulator-$name.log" 2>&1 &
emulator_pid=$!
ready=false
for attempt in $(seq 1 96); do
    if ! kill -0 "$emulator_pid" 2>/dev/null; then tail -80 "build-logs/emulator-$name.log"; exit 1; fi
    if [[ "$("$adb_bin" -s emulator-5554 shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)" == 1 ]]; then ready=true; break; fi
    echo "Waiting for Android 17 boot ($attempt/96)"
    sleep 5
done
if [[ "$ready" != true ]]; then tail -80 "build-logs/emulator-$name.log"; exit 1; fi
test "$("$adb_bin" -s emulator-5554 shell getprop ro.build.version.sdk | tr -d '\r')" == 37
test "$("$adb_bin" -s emulator-5554 shell getconf PAGE_SIZE | tr -d '\r')" == 16384
test "$("$adb_bin" -s emulator-5554 shell getprop ro.kernel.qemu | tr -d '\r')" == 1
for setting in window_animation_scale transition_animation_scale animator_duration_scale; do
    "$adb_bin" -s emulator-5554 shell settings put global "$setting" 0
done
