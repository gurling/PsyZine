#!/usr/bin/env bash
# PsyZine, 2026. GPL-3.0-or-later.
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd -- "$project_dir"
export PSYZINE_DEVELOPMENT_KEYSTORE="${PSYZINE_DEVELOPMENT_KEYSTORE:-$project_dir/.local/signing/psyzine-development.jks}"
bash tools/prepare-development-signing.sh
mkdir -p build-logs
./gradlew --no-daemon --console=plain \
    :app:assembleDirectDebug :app:assembleDirectRelease \
    :app:assemblePlayDebug :app:assemblePlayRelease \
    :app:testDirectDebugUnitTest :app:testPlayDebugUnitTest \
    :app:lintDirectDebug :app:lintDirectRelease :app:lintPlayDebug :app:lintPlayRelease \
    "$@" 2>&1 | tee build-logs/gradle-build.log
# pipefail prevents publishing partial outputs after a failed Gradle execution.
python3 tools/verify-apks.py --gradle-log build-logs/gradle-build.log --output-dir artifacts/apks
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
    cat artifacts/apks/BUILD_RESULTS.md >> "$GITHUB_STEP_SUMMARY"
fi
