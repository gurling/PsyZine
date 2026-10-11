#!/usr/bin/env python3
"""Check the public identity without revealing credentials. GPL-3.0-or-later."""
import hashlib
import os
import subprocess
from release_policy import pins

# keytool supports -storepass:env. Gradle additionally checks private-key accessibility.
keytool = os.path.join(os.environ['JAVA_HOME'], 'bin', 'keytool')
base = [keytool, '-keystore', os.environ['PSYZINE_COMPAT_KEYSTORE_FILE'], '-alias', os.environ['PSYZINE_COMPAT_KEY_ALIAS'],
        '-storepass:env', 'PSYZINE_COMPAT_STORE_PASSWORD']
certificate = subprocess.check_output(base + ['-exportcert'], stderr=subprocess.DEVNULL)
digest = hashlib.sha256(certificate).hexdigest()
if set(pins().values()) != {digest}:
    raise SystemExit('Compatibility container certificate does not match the retained identities.')
result = subprocess.check_output(base + ['-list', '-v'], stderr=subprocess.DEVNULL, text=True, env=dict(os.environ, LANG='C'))
if 'PrivateKeyEntry' not in result:
    raise SystemExit('Compatibility container has no signing private key.')
print(f'Retained certificate verified: {digest}')
