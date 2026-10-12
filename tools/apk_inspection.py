"""SDK inspection shared by publication and upgrade scripts. GPL-3.0-or-later."""
import hashlib
import os
from pathlib import Path
import re
import subprocess


def sdk_tool(name):
    sdk = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or '')
    return sdk / ('cmdline-tools/latest/bin' if name == 'apkanalyzer' else 'build-tools/35.0.0') / name


def output(*args):
    return subprocess.check_output([str(a) for a in args], text=True).strip()


def inspect_apk(apk):
    def manifest(field):
        return output(sdk_tool('apkanalyzer'), 'manifest', field, apk)
    signature = output(sdk_tool('apksigner'), 'verify', '--verbose', '--print-certs', '--min-sdk-version', '26', apk)
    output(sdk_tool('zipalign'), '-c', '-P', '16', '4', apk)
    certificates = re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)$', signature, re.MULTILINE)
    if len(certificates) != 1:
        raise ValueError('One verified APK signer required')
    version_code = manifest('version-code')
    return {'filename': apk.name, 'applicationId': manifest('application-id'),
            'versionCode': int(version_code) if version_code.isdecimal() else None,
            'versionName': manifest('version-name'), 'debuggable': manifest('debuggable') == 'true',
            'sha256': hashlib.sha256(apk.read_bytes()).hexdigest(), 'signerCertificateSha256': [c.lower() for c in certificates]}


def verify_checksums(directory, allow_missing=False, legacy_metadata=False):
    sums = {}
    for line in (directory / 'SHA256SUMS').read_text().splitlines():
        match = re.fullmatch(r'([0-9a-f]{64})  ([A-Za-z0-9_.-]+)', line)
        if not match or match[2] in sums:
            raise ValueError('Invalid or duplicate checksum entry')
        digest, name = match.groups()
        sums[name] = digest
        path = directory / name
        if allow_missing and not path.exists():
            continue
        if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            raise ValueError(f'Checksum mismatch: {name}')
    for path in directory.iterdir():
        if path.is_file() and path.name != 'SHA256SUMS' and path.name not in sums:
            if legacy_metadata and path.name == 'BUILD_RESULTS.json':
                continue  # Old reports are checked field-for-field against actual APKs.
            raise ValueError(f'Unchecked downloaded file: {path.name}')
    return sums
