"""Negative release policy coverage. PsyZine, GPL-3.0-or-later."""
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from release_policy import (VARIANTS, artifact_name, check_history, expected_id, expected_name,
                            pins, read_version, validate_apk_identity)
from apk_inspection import verify_checksums

ROOT = Path(__file__).resolve().parents[2]


class ReleasePolicyTest(unittest.TestCase):
    def record(self, flavor='direct', build_type='compatibleRelease', mode='compatibility'):
        code, base = read_version()
        package = expected_id(flavor, build_type)
        return {'applicationId': package, 'versionCode': code,
                'versionName': expected_name(base, code, build_type, mode),
                'debuggable': build_type == 'debug', 'signerCertificateSha256': [pins()[package]]}

    def candidate(self):
        return {'signingMode': 'compatibility', 'publicationAllowed': True,
                'apks': [self.record(f, b) for f, b in VARIANTS]}

    def test_all_five_variants_have_expected_pinned_identity(self):
        for flavor, build_type in VARIANTS:
            validate_apk_identity(self.record(flavor, build_type), flavor, build_type, 'compatibility', *read_version())
        self.assertEqual(len(pins()), 4)

    def test_wrong_package_certificate_version_and_debug_flag_are_rejected(self):
        for key, value in [('applicationId', 'app.psyzine'), ('signerCertificateSha256', ['f' * 64]),
                           ('versionCode', 1), ('versionCode', None), ('versionName', '1.0.0'), ('debuggable', True)]:
            with self.subTest(key=key):
                record = self.record(); record[key] = value
                with self.assertRaises(ValueError):
                    validate_apk_identity(record, 'direct', 'compatibleRelease', 'compatibility', *read_version())

    def test_multiple_signers_are_rejected(self):
        record = self.record(); record['signerCertificateSha256'] *= 2
        with self.assertRaises(ValueError):
            validate_apk_identity(record, 'direct', 'compatibleRelease', 'compatibility', *read_version())

    def test_only_unpublished_checks_can_use_disposable_certificate(self):
        record = self.record(); record['signerCertificateSha256'] = ['f' * 64]
        validate_apk_identity(record, 'direct', 'compatibleRelease', 'unpublished', *read_version())
        candidate = self.candidate(); candidate['signingMode'] = 'unpublished'
        with self.assertRaises(ValueError): check_history(candidate, [])

    def test_false_publication_eligibility_is_rejected(self):
        candidate = self.candidate(); candidate['publicationAllowed'] = False
        with self.assertRaises(ValueError): check_history(candidate, [])

    def test_code_two_can_follow_legacy_code_one_with_different_certificate(self):
        legacy = self.record(); legacy['versionCode'] = 1; legacy['signerCertificateSha256'] = ['f' * 64]
        check_history(self.candidate(), [{'tag': 'v1.0.0-dev.9', 'apks': [legacy]}])

    def test_stale_code_on_any_package_is_rejected(self):
        for flavor, build_type in VARIANTS:
            history = [{'tag': 'earlier', 'apks': [self.record(flavor, build_type)]}]
            with self.subTest(package=expected_id(flavor, build_type)), self.assertRaises(ValueError):
                check_history(self.candidate(), history)

    def test_highest_historical_code_is_used_across_releases(self):
        low = self.record(); low['versionCode'] = 1
        high = self.record(); high['versionCode'] = 100
        with self.assertRaises(ValueError):
            check_history(self.candidate(), [{'tag': 'old', 'apks': [high]}, {'tag': 'recent', 'apks': [low]}])

    def test_missing_unknown_and_invalid_historical_metadata_are_rejected(self):
        for history in [[{'tag': 'old', 'apks': []}], [{'apks': [self.record()]}],
                        [{'tag': 'old', 'apks': [{'applicationId': 'unexpected', 'versionCode': 1}]}],
                        [{'tag': 'old', 'apks': [{'applicationId': 'app.psyzine.debug', 'versionCode': '1'}]}]]:
            with self.subTest(history=history), self.assertRaises(ValueError):
                check_history(self.candidate(), history)

    def test_compatibility_artifact_uses_version_source_and_code(self):
        code, base = read_version()
        self.assertEqual(artifact_name('direct', 'compatibleRelease', expected_name(base, code, 'compatibleRelease', 'compatibility')),
                         f'PsyZine-Direct-Compatible-Release-v{base}-compat.{code}.apk')

    def test_bad_version_sources_are_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            for text in ['versionCode=0\nversionName=1.0.1\n', 'versionCode=2\nversionName=bad\n']:
                (root / 'version.properties').write_text(text)
                with self.assertRaises(ValueError): read_version(root)

    def test_missing_key_never_generates_replacement_by_default(self):
        with tempfile.TemporaryDirectory() as d:
            key = Path(d) / 'missing.jks'
            env = dict(os.environ, PSYZINE_DEVELOPMENT_KEYSTORE=str(key), PSYZINE_SIGNING_MODE='compatibility')
            result = subprocess.run(['bash', str(ROOT / 'tools/prepare-development-signing.sh')], env=env, capture_output=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertFalse(key.exists())

    def test_disposable_generation_requires_both_explicit_opt_ins(self):
        with tempfile.TemporaryDirectory() as d:
            key = Path(d) / 'missing.jks'
            env = dict(os.environ, PSYZINE_DEVELOPMENT_KEYSTORE=str(key), PSYZINE_SIGNING_MODE='compatibility')
            result = subprocess.run(['bash', str(ROOT / 'tools/prepare-development-signing.sh'), '--unpublished-ci'], env=env, capture_output=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertFalse(key.exists())

    def test_missing_secrets_stop_restore_before_writing_key(self):
        with tempfile.TemporaryDirectory() as d:
            key = Path(d) / 'missing.jks'
            env = {k: v for k, v in os.environ.items() if not k.startswith('PSYZINE_COMPAT_')}
            env['PSYZINE_COMPAT_KEYSTORE_FILE'] = str(key)
            result = subprocess.run(['bash', str(ROOT / 'tools/restore-compat-signing.sh')], env=env, capture_output=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertFalse(key.exists())

    def test_physical_device_is_refused_before_adb_or_file_reads(self):
        result = subprocess.run(['bash', str(ROOT / 'tools/verify-in-place-upgrade.sh'), '24261JEGR00625',
                                 'missing', 'missing', 'missing', 'missing'], capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('Physical devices are forbidden', result.stderr)

    def test_startup_benchmark_refuses_physical_devices_before_adb(self):
        result = subprocess.run([sys.executable, str(ROOT / 'tools/benchmark-startup.py'), '24261JEGR00625',
                                 '--fixture-confirmed', '--output', '/tmp/never-physical-startup.json'], capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('physical devices are forbidden', result.stderr)

    def test_background_trace_refuses_physical_devices_before_adb(self):
        result = subprocess.run([sys.executable, str(ROOT / 'tools/benchmark-background.py'), '24261JEGR00625',
                                 '--fixture-confirmed', '--presentation-disabled', '--output', '/tmp/never-physical-trace.pftrace'], capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('physical devices are forbidden', result.stderr)

    def test_decoded_readme_obtainium_link_selects_exactly_one_variant(self):
        subprocess.run([sys.executable, str(ROOT / 'tools/verify-obtainium.py')], check=True, capture_output=True)

    def test_download_checksum_rejects_modified_unchecked_and_unsafe_files(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d); file = root / 'candidate.apk'; file.write_bytes(b'candidate')
            import hashlib
            digest = hashlib.sha256(file.read_bytes()).hexdigest()
            (root / 'SHA256SUMS').write_text(f'{digest}  candidate.apk\n')
            verify_checksums(root)
            file.write_bytes(b'tampered')
            with self.assertRaises(ValueError): verify_checksums(root)
            (root / 'SHA256SUMS').write_text(f'{digest}  ../candidate.apk\n')
            with self.assertRaises(ValueError): verify_checksums(root)
            (root / 'SHA256SUMS').write_text('')
            with self.assertRaises(ValueError): verify_checksums(root)


if __name__ == '__main__': unittest.main()
