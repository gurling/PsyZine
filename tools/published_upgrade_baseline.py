"""Validate a pinned, downloaded compatibility baseline before emulator installation."""
import json
from pathlib import Path
import sys

from apk_inspection import inspect_apk, verify_checksums
from release_policy import pins


def validate_metadata(key, pin, actual, report, certificate):
    code = int(key.removeprefix('code'))
    record = next((r for r in report['apks'] if r['filename'] == pin['filename']), None)
    if report['commit'] != pin['commit'] or record is None or any(actual[k] != record.get(k) for k in actual):
        raise ValueError('Published baseline source/report mismatch')
    if (actual['filename'] != pin['filename'] or actual['sha256'] != pin['sha256'] or
            actual['applicationId'] != 'app.psyzine.debug' or actual['versionCode'] != code or
            actual['versionName'] != pin['tag'].removeprefix('v') or actual['debuggable'] or
            actual['signerCertificateSha256'] != [certificate]):
        raise ValueError('Published baseline identity mismatch')


def verify(key, directory):
    pin = json.loads(Path('config/upgrade-baselines.json').read_text())[key]
    verify_checksums(directory, allow_missing=True)
    actual = inspect_apk(directory / pin['filename'])
    report = json.loads((directory / 'BUILD_RESULTS.json').read_text())
    validate_metadata(key, pin, actual, report, pins()['app.psyzine.debug'])


if __name__ == '__main__':
    verify(sys.argv[1], Path(sys.argv[2]))
