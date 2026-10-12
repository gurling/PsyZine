#!/usr/bin/env bash
# PsyZine, 2026. GPL-3.0-or-later.
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd -- "$project_dir"
export PSYZINE_SIGNING_MODE="${PSYZINE_SIGNING_MODE:-compatibility}"
if [[ "$PSYZINE_SIGNING_MODE" != production && -z "${PSYZINE_COMPAT_KEYSTORE_FILE:-}" ]]; then
    export PSYZINE_DEVELOPMENT_KEYSTORE="${PSYZINE_DEVELOPMENT_KEYSTORE:-$project_dir/.local/signing/psyzine-development.jks}"
    if [[ "$PSYZINE_SIGNING_MODE" == unpublished ]]; then
        bash tools/prepare-development-signing.sh --unpublished-ci
    else
        bash tools/prepare-development-signing.sh
    fi
fi
variants=(DirectRelease PlayRelease)
suites=(DirectDebug PlayDebug)
if [[ "$PSYZINE_SIGNING_MODE" != production ]]; then
    variants+=(DirectDebug PlayDebug DirectCompatibleRelease)
    suites+=(DirectCompatibleRelease)
fi
tasks=()
for variant in "${variants[@]}"; do tasks+=(":app:assemble$variant" ":app:lint$variant"); done
for suite in "${suites[@]}"; do tasks+=(":app:test${suite}UnitTest"); done
# Remove old packaging results so a failed invocation cannot expose a stale successful set.
rm -rf artifacts/apks
mkdir -p build-logs
./gradlew --no-daemon --console=plain "${tasks[@]}" "$@" 2>&1 | tee build-logs/gradle-build.log
python3 tools/verify-apks.py --gradle-log build-logs/gradle-build.log --output-dir artifacts/apks --mode "$PSYZINE_SIGNING_MODE"
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
    cat artifacts/apks/BUILD_RESULTS.md >> "$GITHUB_STEP_SUMMARY"
fi
