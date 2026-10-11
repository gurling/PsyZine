#!/usr/bin/env bash
# PsyZine, 2026. GPL-3.0-or-later.
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
keystore_path="${PSYZINE_DEVELOPMENT_KEYSTORE:-$project_dir/.local/signing/psyzine-development.jks}"
if [[ ! -f "$keystore_path" ]]; then
    if [[ "${1:-}" != --unpublished-ci || "${PSYZINE_SIGNING_MODE:-}" != unpublished || -z "${PSYZINE_DEVELOPMENT_KEYSTORE:-}" ]]; then
        echo 'Existing signing key required. Replacement generation is forbidden for publishable builds.' >&2
        exit 1
    fi
    [[ "$keystore_path" != "$project_dir/.local/signing/psyzine-development.jks" ]] || { echo 'Disposable key must use a separate path.' >&2; exit 1; }
    umask 077
    mkdir -p -- "$(dirname -- "$keystore_path")"
    "${JAVA_HOME:+$JAVA_HOME/bin/}keytool" -genkeypair -noprompt -storetype JKS -keystore "$keystore_path" \
        -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 \
        -validity 30 -dname 'CN=PsyZine Disposable Unpublished CI, O=PsyZine, C=CZ' >/dev/null 2>&1
fi
"${JAVA_HOME:+$JAVA_HOME/bin/}keytool" -list -keystore "$keystore_path" -storepass android -alias androiddebugkey >/dev/null 2>&1
printf 'Existing development signing container is readable. Mode: %s\n' "${PSYZINE_SIGNING_MODE:-compatibility}"
