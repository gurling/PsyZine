"""Shared version, identity and release checks. PsyZine, GPL-3.0-or-later."""
import json
import re
from pathlib import Path

PROJECT = Path(__file__).resolve().parent.parent
VARIANTS = [('direct', 'debug'), ('direct', 'release'), ('play', 'debug'),
            ('play', 'release'), ('direct', 'compatibleRelease')]


def read_version(project=PROJECT, code=None):
    values = dict(line.strip().split('=', 1) for line in (project / 'version.properties').read_text().splitlines()
                  if line.strip() and not line.startswith('#'))
    number = int(values['versionCode']) if code is None else int(code)
    if not 1 <= number <= 2100000000 or not re.fullmatch(r'\d+\.\d+\.\d+', values['versionName']):
        raise ValueError('Invalid version.properties')
    return number, values['versionName']


def expected_id(flavor, build_type):
    return 'app.psyzine' + ('.play' if flavor == 'play' else '') + ('.debug' if build_type in ('debug', 'compatibleRelease') else '')


def expected_name(base, code, build_type, mode):
    if build_type == 'compatibleRelease':
        return f'{base}-compat.{code}'
    return base + ('-dev' if build_type == 'release' and mode != 'production' else '')


def artifact_name(flavor, build_type, version):
    label = 'Compatible-Release' if build_type == 'compatibleRelease' else build_type.title()
    return f'PsyZine-{flavor.title()}-{label}-v{version}.apk'


def pins(project=PROJECT):
    policy = json.loads((project / 'config/signing-policy.json').read_text())
    mapping = policy['applicationIds']
    if policy['schemaVersion'] != 1 or set(mapping) != {expected_id(f, b) for f, b in VARIANTS}:
        raise ValueError('Incomplete signing policy')
    if any(not re.fullmatch(r'[0-9a-f]{64}', value) for value in mapping.values()):
        raise ValueError('Invalid certificate pin')
    return mapping


def validate_apk_identity(record, flavor, build_type, mode, code, base):
    package = expected_id(flavor, build_type)
    if record['applicationId'] != package:
        raise ValueError(f'Wrong package ID: expected {package}')
    if record['versionCode'] != code or record['versionName'] != expected_name(base, code, build_type, mode):
        raise ValueError('Wrong APK version')
    if record['debuggable'] != (build_type == 'debug'):
        raise ValueError('Wrong debuggable flag')
    certificates = [c.lower() for c in record['signerCertificateSha256']]
    if len(certificates) != 1 or not re.fullmatch(r'[0-9a-f]{64}', certificates[0]):
        raise ValueError('Exactly one valid signer is required')
    if mode != 'unpublished' and certificates != [pins()[package]]:
        raise ValueError(f'Certificate continuity failed for {package}')


def check_history(candidate, historical):
    """Every historical APK must have inspected metadata, including obsolete dev releases."""
    if candidate.get('signingMode') not in ('compatibility', 'production') or not candidate.get('publicationAllowed'):
        raise ValueError('Unpublished/disposable builds cannot be published')
    maximum = {}
    for release in historical:
        if not release.get('tag') or not release.get('apks'):
            raise ValueError('Missing historical APK metadata; publication requires review')
        for apk in release['apks']:
            package, code = apk['applicationId'], apk['versionCode']
            if package not in pins() or type(code) is not int or code < 1:
                raise ValueError('Unknown historical package or invalid version code')
            maximum[package] = max(maximum.get(package, 0), code)
    for apk in candidate['apks']:
        if apk['versionCode'] <= maximum.get(apk['applicationId'], 0):
            raise ValueError(f'Stale version code for {apk["applicationId"]}: must exceed {maximum[apk["applicationId"]]}')
