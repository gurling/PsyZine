"""Downloaded-candidate validation boundaries. PsyZine, GPL-3.0-or-later."""
import hashlib
import json
from pathlib import Path
import runpy
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from release_policy import VARIANTS, read_version, expected_id, expected_name, artifact_name, pins

GATE = runpy.run_path(str(Path(__file__).resolve().parents[1] / 'verify-publication.py'))['verify_candidate']


class DownloadedCandidateTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.directory = Path(self.temporary.name)
        self.actual = {}
        code, base = read_version()
        records = []
        for flavor, build_type in VARIANTS:
            version = expected_name(base, code, build_type, 'compatibility')
            filename = artifact_name(flavor, build_type, version)
            (self.directory / filename).write_bytes(filename.encode())
            package = expected_id(flavor, build_type)
            record = {'flavor': flavor, 'buildType': build_type, 'filename': filename, 'applicationId': package,
                      'versionCode': code, 'versionName': version, 'debuggable': build_type == 'debug',
                      'signatureVerified': True, 'zipIntegrityVerified': True, 'alignmentVerified': True,
                      'sha256': hashlib.sha256(filename.encode()).hexdigest(), 'signerCertificateSha256': [pins()[package]]}
            self.actual[filename] = dict(record)
            records.append(record)
        self.report = {'commit': 'reviewed', 'workingTreeDirty': False, 'signingMode': 'compatibility',
                       'publicationAllowed': True, 'apks': records, 'gradleResult': 'BUILD SUCCESSFUL',
                       'unitTests': {s: {'tests': 44, 'failures': 0, 'errors': 0, 'skipped': 0} for s in ['DirectDebug', 'PlayDebug', 'DirectCompatibleRelease']},
                       'lint': {f + b[0].upper() + b[1:]: {'errors': 0} for f, b in VARIANTS}}

    def tearDown(self):
        self.temporary.cleanup()

    def verify(self):
        (self.directory / 'BUILD_RESULTS.json').write_text(json.dumps(self.report))
        files = sorted(p for p in self.directory.iterdir() if p.name != 'SHA256SUMS')
        (self.directory / 'SHA256SUMS').write_text(''.join(f'{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n' for p in files))
        with patch.dict(GATE.__globals__, inspect_apk=lambda p: self.actual[p.name]):
            with patch('subprocess.check_output', return_value='reviewed\n'):
                return GATE(self.directory, 'compatibility')

    def test_complete_clean_downloaded_set_passes(self):
        self.assertEqual(len(self.verify()['apks']), 5)

    def test_uncommitted_wrong_mode_ineligible_and_wrong_commit_fail(self):
        for field, value in [('workingTreeDirty', True), ('signingMode', 'unpublished'), ('publicationAllowed', False), ('commit', 'other')]:
            old = self.report[field]; self.report[field] = value
            with self.subTest(field=field), self.assertRaises(ValueError): self.verify()
            self.report[field] = old

    def test_missing_or_failed_automated_check_evidence_fails(self):
        for field, value in [('gradleResult', 'BUILD FAILED'), ('unitTests', {}), ('lint', {})]:
            old = self.report[field]; self.report[field] = value
            with self.subTest(field=field), self.assertRaises(ValueError): self.verify()
            self.report[field] = old
        self.report['unitTests']['DirectCompatibleRelease']['skipped'] = 1
        with self.assertRaises(ValueError): self.verify()

    def test_actual_package_version_and_certificate_disagreement_fail(self):
        filename = self.report['apks'][-1]['filename']
        for field, value in [('applicationId', 'app.psyzine'), ('versionCode', 1), ('debuggable', True), ('signerCertificateSha256', ['f' * 64])]:
            old = self.actual[filename][field]; self.actual[filename][field] = value
            with self.subTest(field=field), self.assertRaises(ValueError): self.verify()
            self.actual[filename][field] = old

    def test_missing_and_duplicate_variant_sets_fail(self):
        last = self.report['apks'].pop()
        with self.assertRaises(ValueError): self.verify()
        self.report['apks'].append(self.report['apks'][0])
        with self.assertRaises(ValueError): self.verify()
        self.report['apks'][-1] = last

    def test_unexpected_apk_asset_fails(self):
        (self.directory / 'other.apk').write_bytes(b'other')
        with self.assertRaises(ValueError): self.verify()


if __name__ == '__main__': unittest.main()
