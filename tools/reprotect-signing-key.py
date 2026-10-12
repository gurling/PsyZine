#!/usr/bin/env python3
"""Owner-approved copy of a retained private key into PKCS12; never generates a key.
PsyZine, GPL-3.0-or-later. Not run automatically by CI or build scripts.
"""
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
from release_policy import pins


def certificate(keytool, file, alias, password_env):
    return subprocess.check_output([str(keytool), '-exportcert', '-keystore', str(file), '-alias', alias,
                                    '-storepass:env', password_env], stderr=subprocess.DEVNULL)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--approved-copy', action='store_true', help='Use only after explicit owner approval')
    parser.add_argument('--source', required=True, type=Path)
    parser.add_argument('--destination', required=True, type=Path)
    args = parser.parse_args()
    if not args.approved_copy:
        raise SystemExit('Explicit owner approval is required before key re-protection.')
    for name in ('PSYZINE_COMPAT_KEY_ALIAS', 'PSYZINE_COMPAT_STORE_PASSWORD', 'PSYZINE_COMPAT_KEY_PASSWORD'):
        if not os.environ.get(name): raise SystemExit(f'Missing protected-container input: {name}')
    if len(os.environ['PSYZINE_COMPAT_STORE_PASSWORD']) < 24 or os.environ['PSYZINE_COMPAT_KEY_PASSWORD'] != os.environ['PSYZINE_COMPAT_STORE_PASSWORD']:
        raise SystemExit('Use a randomly generated password of at least 24 characters; PKCS12 key/store passwords must match.')
    if args.destination.exists() or not args.source.is_file() or args.source.resolve() == args.destination.resolve():
        raise SystemExit('Source must exist and destination must be a new separate file.')
    keytool = Path(os.environ['JAVA_HOME']) / 'bin/keytool'
    os.environ.setdefault('PSYZINE_SOURCE_STORE_PASSWORD', 'android')
    os.environ.setdefault('PSYZINE_SOURCE_KEY_PASSWORD', 'android')
    source_alias = os.environ.get('PSYZINE_SOURCE_KEY_ALIAS', 'androiddebugkey')
    original_hash = hashlib.sha256(args.source.read_bytes()).hexdigest()
    original_certificate = certificate(keytool, args.source, source_alias, 'PSYZINE_SOURCE_STORE_PASSWORD')
    if set(pins().values()) != {hashlib.sha256(original_certificate).hexdigest()}:
        raise SystemExit('Source certificate is not the recovered identity.')
    args.destination.parent.mkdir(parents=True, exist_ok=True)
    os.umask(0o077)
    with tempfile.TemporaryDirectory(prefix='psyzine-protected-copy-', dir=args.destination.parent) as temporary:
        protected = Path(temporary) / 'retained.p12'
        subprocess.run([str(keytool), '-importkeystore', '-noprompt', '-srckeystore', str(args.source),
                        '-srcalias', source_alias, '-srcstorepass:env', 'PSYZINE_SOURCE_STORE_PASSWORD',
                        '-srckeypass:env', 'PSYZINE_SOURCE_KEY_PASSWORD', '-destkeystore', str(protected),
                        '-deststoretype', 'PKCS12', '-destalias', os.environ['PSYZINE_COMPAT_KEY_ALIAS'],
                        '-deststorepass:env', 'PSYZINE_COMPAT_STORE_PASSWORD', '-destkeypass:env', 'PSYZINE_COMPAT_KEY_PASSWORD'],
                       check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        if certificate(keytool, protected, os.environ['PSYZINE_COMPAT_KEY_ALIAS'], 'PSYZINE_COMPAT_STORE_PASSWORD') != original_certificate:
            raise SystemExit('Copied certificate changed; refusing output.')
        if hashlib.sha256(args.source.read_bytes()).hexdigest() != original_hash:
            raise SystemExit('Original container changed; stop and review.')
        # Exclusive destination prevents accidental overwrite even if another process creates it.
        with args.destination.open('xb') as destination, protected.open('rb') as source:
            shutil.copyfileobj(source, destination)
    print('Retained key copied with unchanged certificate; original container unchanged.')
    print('Owner action required: retain two encrypted offline backups before secret upload.')


if __name__ == '__main__': main()
