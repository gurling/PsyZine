#!/usr/bin/env bash
# Trusted environment only. PsyZine, GPL-3.0-or-later.
set -euo pipefail
for name in PSYZINE_COMPAT_KEYSTORE_BASE64 PSYZINE_COMPAT_KEY_ALIAS PSYZINE_COMPAT_STORE_PASSWORD PSYZINE_COMPAT_KEY_PASSWORD PSYZINE_COMPAT_KEYSTORE_FILE; do
    [[ -n "${!name:-}" ]] || { printf 'Missing mandatory compatibility signing input: %s\n' "$name" >&2; exit 1; }
done
[[ ! -e "$PSYZINE_COMPAT_KEYSTORE_FILE" ]] || { echo 'Refusing to replace an existing container.' >&2; exit 1; }
umask 077
printf '%s' "$PSYZINE_COMPAT_KEYSTORE_BASE64" | base64 --decode > "$PSYZINE_COMPAT_KEYSTORE_FILE"
# Validate certificate and PrivateKeyEntry before any build. Passwords are env references, never command arguments.
python3 tools/check-signing-container.py
