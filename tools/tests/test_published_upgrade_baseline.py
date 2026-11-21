"""Reject changed download metadata before an installed upgrade test starts."""
import copy
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from published_upgrade_baseline import validate_metadata


class PublishedBaselineTest(unittest.TestCase):
    def setUp(self):
        self.pin = dict(filename='candidate.apk', tag='v1.0.3-compat.4', commit='source', sha256='digest')
        self.actual = dict(filename='candidate.apk', applicationId='app.psyzine.debug', versionCode=4,
                           versionName='1.0.3-compat.4', debuggable=False, sha256='digest',
                           signerCertificateSha256=['certificate'])
        self.report = dict(commit='source', apks=[self.actual.copy()])

    def test_exact_published_identity_is_accepted(self):
        validate_metadata('code4', self.pin, self.actual, self.report, 'certificate')

    def test_even_self_consistent_wrong_apks_are_rejected(self):
        for field, wrong in [('versionCode', 5), ('applicationId', 'other.package'),
                             ('debuggable', True), ('sha256', 'changed'),
                             ('versionName', '1.0.3'), ('signerCertificateSha256', ['other'])]:
            with self.subTest(field=field):
                actual = dict(self.actual, **{field: wrong})
                report = dict(commit='source', apks=[actual.copy()])
                with self.assertRaisesRegex(ValueError, 'identity mismatch'):
                    validate_metadata('code4', self.pin, actual, report, 'certificate')

    def test_source_or_report_drift_is_rejected(self):
        for change in ['commit', 'record', 'field']:
            with self.subTest(change=change):
                report = copy.deepcopy(self.report)
                if change == 'commit': report['commit'] = 'different'
                elif change == 'record': report['apks'] = []
                else: report['apks'][0]['sha256'] = 'different'
                with self.assertRaisesRegex(ValueError, 'source/report mismatch'):
                    validate_metadata('code4', self.pin, self.actual, report, 'certificate')
