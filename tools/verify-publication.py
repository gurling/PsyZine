#!/usr/bin/env python3
"""Fail-closed publication gate using downloaded APKs, not unverified release names.
PsyZine, GPL-3.0-or-later. Read-only GitHub requests; never creates a release.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile
from release_policy import check_history, validate_apk_identity, read_version, VARIANTS
from apk_inspection import inspect_apk, verify_checksums


def gh(*args):
    return subprocess.check_output(['gh', *args], text=True)


def verify_candidate(directory, mode):
    verify_checksums(directory)
    report = json.loads((directory / 'BUILD_RESULTS.json').read_text())
    code, base = read_version()
    if report['commit'] != subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip():
        raise ValueError('Candidate source commit does not match the reviewed checkout')
    variants = [v for v in VARIANTS if v[1] == 'release'] if mode == 'production' else VARIANTS
    if report.get('signingMode') != mode or not report.get('publicationAllowed') or report.get('workingTreeDirty'):
        raise ValueError('Publication requires a clean, pinned build of the selected signing mode')
    suites = ['DirectDebug', 'PlayDebug'] + ([] if mode == 'production' else ['DirectCompatibleRelease'])
    if report.get('gradleResult') != 'BUILD SUCCESSFUL':
        raise ValueError('Missing successful Gradle evidence')
    for suite in suites:
        counts = report.get('unitTests', {}).get(suite, {})
        if counts.get('tests', 0) <= 0 or any(counts.get(k, -1) != 0 for k in ('failures', 'errors', 'skipped')):
            raise ValueError(f'Missing passing unit evidence: {suite}')
    for flavor, build_type in variants:
        variant = flavor + build_type[0].upper() + build_type[1:]
        if report.get('lint', {}).get(variant, {}).get('errors') != 0:
            raise ValueError(f'Missing passing lint evidence: {variant}')
    if len(report['apks']) != len(variants):
        raise ValueError('Incomplete candidate artifact set')
    actual_variants = []
    for record in report['apks']:
        variant = (record['flavor'], record['buildType'])
        actual_variants.append(variant)
        actual = inspect_apk(directory / record['filename'])
        if not all(record.get(key) is True for key in ('signatureVerified', 'zipIntegrityVerified', 'alignmentVerified')):
            raise ValueError('Missing verified APK integrity evidence')
        for key in ('sha256', 'applicationId', 'versionCode', 'versionName', 'debuggable', 'signerCertificateSha256'):
            if actual[key] != record[key]:
                raise ValueError(f'Candidate report disagrees with downloaded APK: {key}')
        validate_apk_identity(actual, *variant, mode, code, base)
    if sorted(actual_variants) != sorted(variants):
        raise ValueError('Duplicate or wrong candidate variants')
    if set(p.name for p in directory.glob('*.apk')) != {r['filename'] for r in report['apks']}:
        raise ValueError('Unexpected APK assets')
    return report


def historical_metadata(repository, evidence_dir):
    # A network/auth error propagates and stops publication. Never treat it as empty history.
    pages = json.loads(gh('api', '--paginate', '--slurp', f'repos/{repository}/releases?per_page=100'))
    history = []
    for release in (r for page in pages for r in page if not r['draft']):
        assets = [a for a in release['assets'] if a['name'].endswith('.apk')]
        if not assets:
            raise ValueError(f'Missing historical APK metadata: {release["tag_name"]}; review required')
        with tempfile.TemporaryDirectory(prefix='psyzine-release-history-') as temporary:
            directory = Path(temporary)
            tag = release['tag_name']
            for pattern in ('*.apk', 'BUILD_RESULTS.json', 'SHA256SUMS'):
                subprocess.run(['gh', 'release', 'download', tag, '--repo', repository, '--pattern', pattern,
                                '--dir', str(directory)], check=True)
            report = json.loads((directory / 'BUILD_RESULTS.json').read_text())
            by_name = {a['filename']: a for a in report['apks']}
            sums = verify_checksums(directory, allow_missing=True, legacy_metadata=True)
            inspected = []
            for asset in assets:
                apk = directory / asset['name']
                if asset['name'] not in by_name or asset['name'] not in sums:
                    raise ValueError(f'Missing metadata/checksum for historical asset: {tag}/{asset["name"]}')
                actual = inspect_apk(apk)
                for key in ('sha256', 'applicationId', 'versionCode', 'versionName', 'signerCertificateSha256'):
                    if actual[key] != by_name[asset['name']][key]:
                        raise ValueError(f'Historical metadata mismatch: {tag}/{asset["name"]}/{key}')
                inspected.append(actual)
            history.append({'tag': tag, 'apks': inspected})
    evidence_dir.mkdir(parents=True, exist_ok=True)
    (evidence_dir / 'HISTORICAL_APKS.json').write_text(json.dumps(history, indent=2) + '\n')
    return history


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--candidate', required=True, type=Path)
    parser.add_argument('--second-build', type=Path)
    parser.add_argument('--repository', required=True)
    parser.add_argument('--mode', choices=['compatibility', 'production'], default='compatibility')
    parser.add_argument('--evidence-dir', type=Path, default=Path('artifacts/publication'))
    args = parser.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', args.repository):
        raise ValueError('Invalid GitHub repository')
    candidate = verify_candidate(args.candidate, args.mode)
    if args.mode == 'compatibility' and args.second_build is None:
        raise ValueError('Two independent clean builds are required')
    if args.second_build:
        second = verify_candidate(args.second_build, args.mode)
        if second['commit'] != candidate['commit']:
            raise ValueError('Independent builds use different commits')
        def identities(report):
            return sorted((a['filename'], a['applicationId'], a['versionCode'], a['versionName'], a['signerCertificateSha256']) for a in report['apks'])
        if identities(second) != identities(candidate):
            raise ValueError('Independent build identities differ')
    check_history(candidate, historical_metadata(args.repository, args.evidence_dir))
    code, base = read_version()
    tag = f'v{base}-compat.{code}' if args.mode == 'compatibility' else f'v{base}'
    # Existing tag/release is a refusal, even if its APK code looks reusable.
    tags = json.loads(gh('api', '--paginate', '--slurp', f'repos/{args.repository}/tags?per_page=100'))
    if any(t['name'] == tag for page in tags for t in page):
        raise ValueError('Candidate tag already exists; only one publication per version is allowed')
    print(f'Publication preflight passed: {tag}')


if __name__ == '__main__':
    main()
