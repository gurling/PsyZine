#!/usr/bin/env bash
# PsyZine, 2026. GPL-3.0-or-later.
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd -- "$project_dir"
collect_visual_captures() {
    mkdir -p screenshots
    adb pull /sdcard/Android/data/app.psyzine.debug/files/parity-screenshots screenshots/ || true
}
# Capture before the emulator runner shuts down; preserve the Gradle exit status.
trap collect_visual_captures EXIT
./gradlew :app:connectedDirectDebugAndroidTest \
    -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true "$@"
