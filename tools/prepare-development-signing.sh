#!/usr/bin/env bash
# PsyZine, 2026. GPL-3.0-or-later.
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
keystore_path="${PSYZINE_DEVELOPMENT_KEYSTORE:-$project_dir/.local/signing/psyzine-development.jks}"
keytool_binary="${JAVA_HOME:+$JAVA_HOME/bin/}keytool"
mkdir -p -- "$(dirname -- "$keystore_path")"
if [[ ! -f "$keystore_path" ]]; then
    umask 077
    "$keytool_binary" -genkeypair -noprompt -storetype JKS -keystore "$keystore_path" \
        -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 \
        -validity 10000 -dname 'CN=PsyZine Development, OU=Testing, O=PsyZine, C=CZ'
fi
"$keytool_binary" -list -keystore "$keystore_path" -storepass android -alias androiddebugkey >/dev/null
chmod 600 "$keystore_path"
printf 'Development signing key ready: %s\n' "$keystore_path"
printf 'This certificate is for testing. Keep production signing separate.\n'
