#!/usr/bin/env bash
# Independent, retained-key emulator harness; never depends on unminified app classes.
# PsyZine, GPL-3.0-or-later.
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"
[[ "${PSYZINE_SIGNING_MODE:-compatibility}" == compatibility ]] || { echo 'Retained signing is required.' >&2; exit 1; }
harness_dir="$(mktemp -d "${TMPDIR:-/tmp}/psyzine-upgrade-harness.XXXXXX")"
trap 'rm -rf "$harness_dir"' EXIT
mkdir -p "$harness_dir/gradle" "$harness_dir/src/main/java/com/isaakhanimann/journal/psyzine" artifacts/upgrade
cp gradlew gradlew.bat gradle.properties "$harness_dir/"
cp -r gradle/wrapper "$harness_dir/gradle/"
cp gradle/libs.versions.toml "$harness_dir/gradle/"
cp tools/upgrade-harness/settings.gradle tools/upgrade-harness/build.gradle.kts "$harness_dir/"
cp tools/upgrade-harness/AndroidManifest.xml "$harness_dir/src/main/"
cp app/src/androidTest/java/com/isaakhanimann/journal/psyzine/InPlaceUpgradeTest.kt "$harness_dir/src/main/java/com/isaakhanimann/journal/psyzine/"
export PSYZINE_DEVELOPMENT_KEYSTORE="${PSYZINE_DEVELOPMENT_KEYSTORE:-$project_dir/.local/signing/psyzine-development.jks}"
(
    cd "$harness_dir"
    ./gradlew --no-daemon --console=plain assembleDebug "$@"
) 2>&1 | tee artifacts/upgrade/harness-build.log
python3 - "$harness_dir" <<'PY'
from pathlib import Path
import shutil, sys
files = list((Path(sys.argv[1]) / 'build/outputs/apk/debug').glob('*.apk'))
if len(files) != 1: raise SystemExit('Expected exactly one independent test APK')
shutil.copyfile(files[0], 'artifacts/upgrade/upgrade-test.apk')
PY
