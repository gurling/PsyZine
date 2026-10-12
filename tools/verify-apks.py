#!/usr/bin/env python3
"""Verify actual Gradle outputs, manifest identity, pinned signing and checksums.
PsyZine, 2026. GPL-3.0-or-later. Never fabricates or re-signs APKs.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import zipfile
import xml.etree.ElementTree as ET
from release_policy import VARIANTS, read_version, validate_apk_identity, artifact_name


def run(*args):
    return subprocess.run([str(a) for a in args], check=True, text=True,
                          stdout=subprocess.PIPE, stderr=subprocess.STDOUT).stdout.strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--gradle-log', required=True, type=Path)
    parser.add_argument('--output-dir', default=Path('artifacts/apks'), type=Path)
    parser.add_argument('--mode', choices=['compatibility', 'production', 'unpublished'], default='compatibility')
    args = parser.parse_args()
    project = Path(__file__).resolve().parent.parent
    os.chdir(project)
    code, base_version = read_version()
    variants = [v for v in VARIANTS if v[1] == 'release'] if args.mode == 'production' else VARIANTS
    suites = ['DirectDebug', 'PlayDebug'] + ([] if args.mode == 'production' else ['DirectCompatibleRelease'])
    evidence = args.gradle_log.read_text()
    if not re.search(r'^BUILD SUCCESSFUL in ', evidence, re.MULTILINE):
        raise SystemExit('No successful Gradle execution found; refusing to package APK results.')
    tasks = [prefix + f.title() + b[0].upper() + b[1:] for f, b in variants for prefix in ['assemble', 'lint']]
    tasks += ['test' + suite + 'UnitTest' for suite in suites]
    for task in tasks:
        if not re.search(rf'^> Task :app:{task}(?: UP-TO-DATE| FROM-CACHE)?\s*$', evidence, re.MULTILINE):
            raise SystemExit(f'No successful execution evidence for required Gradle task: {task}')
    sdk = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or '')
    build_tools = sdk / 'build-tools' / '35.0.0'
    apkanalyzer = sdk / 'cmdline-tools' / 'latest' / 'bin' / 'apkanalyzer'
    if not apkanalyzer.is_file():
        apkanalyzer = Path(shutil.which('apkanalyzer') or '')
    for tool in [build_tools / 'apksigner', build_tools / 'zipalign', build_tools / 'aapt2', apkanalyzer]:
        if not tool.is_file():
            raise SystemExit(f'Required SDK verification tool is missing: {tool}')
    commit = run('git', 'rev-parse', 'HEAD')
    dirty = bool(run('git', 'status', '--porcelain'))
    report = {'commit': commit, 'workingTreeDirty': dirty, 'signingMode': args.mode,
              'publicationAllowed': args.mode != 'unpublished' and not dirty, 'gradleResult': 'BUILD SUCCESSFUL',
              'gradleLogSha256': hashlib.sha256(args.gradle_log.read_bytes()).hexdigest(),
              'deviceInstallation': 'not-run', 'unitTests': {}, 'lint': {}, 'apks': []}
    for suite in suites:
        results = list((project / f'app/build/test-results/test{suite}UnitTest').glob('TEST-*.xml'))
        if not results:
            raise SystemExit(f'Unit-test result XML is missing for {suite}.')
        counts = dict.fromkeys(['tests', 'failures', 'errors', 'skipped'], 0)
        for result in results:
            root = ET.parse(result).getroot()
            for key in counts:
                counts[key] += int(root.attrib.get(key, 0))
        if counts['tests'] == 0 or counts['failures'] or counts['errors'] or counts['skipped']:
            raise SystemExit(f'Unit tests did not pass completely for {suite}: {counts}')
        report['unitTests'][suite] = counts
    for flavor, build_type in variants:
        variant = flavor + build_type[0].upper() + build_type[1:]
        result = project / f'app/build/reports/lint-results-{variant}.xml'
        if not result.is_file():
            raise SystemExit(f'Lint result XML is missing for {variant}.')
        issues = ET.parse(result).getroot().findall('issue')
        errors = sum(i.attrib.get('severity', '').lower() in ['error', 'fatal'] for i in issues)
        warnings = sum(i.attrib.get('severity', '').lower() == 'warning' for i in issues)
        if errors:
            raise SystemExit(f'Lint has {errors} errors for {variant}.')
        report['lint'][variant] = {'errors': errors, 'warnings': warnings}
    forbidden = {'android.permission.CAMERA', 'android.permission.READ_EXTERNAL_STORAGE',
                 'android.permission.WRITE_EXTERNAL_STORAGE', 'android.permission.READ_MEDIA_IMAGES',
                 'android.permission.ACCESS_FINE_LOCATION', 'android.permission.ACCESS_COARSE_LOCATION',
                 'android.permission.INTERNET'}
    verified = []
    for flavor, build_type in variants:
        apk = project / f'app/build/outputs/apk/{flavor}/{build_type}/app-{flavor}-{build_type}.apk'
        if not apk.is_file():
            raise SystemExit(f'Signed Gradle output is missing: {apk}')
        with zipfile.ZipFile(apk) as archive:
            bad = archive.testzip()
            if bad or not {'AndroidManifest.xml', 'classes.dex'} <= set(archive.namelist()):
                raise SystemExit(f'APK archive integrity failed: {apk}, {bad}')
        def manifest(field):
            return run(apkanalyzer, 'manifest', field, apk)
        badging = run(build_tools / 'aapt2', 'dump', 'badging', apk)
        labels = re.findall(r"^application-label(?:-[^:]+)?:'([^']*)'$", badging, re.MULTILINE)
        launchers = re.findall(r"^launchable-activity:.*? label='([^']*)'", badging, re.MULTILINE)
        if not labels or set(labels) != {'PsyZine'} or launchers != ['PsyZine']:
            raise SystemExit(f'Application and launcher labels must resolve to PsyZine: {apk}')
        version = manifest('version-name')
        permissions = set(manifest('permissions').splitlines())
        if permissions & forbidden:
            raise SystemExit(f'Forbidden APK permissions: {sorted(permissions & forbidden)}')
        signature = run(build_tools / 'apksigner', 'verify', '--verbose', '--print-certs', '--min-sdk-version', '26', apk)
        certificates = re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)$', signature, re.MULTILINE)
        run(build_tools / 'zipalign', '-c', '-P', '16', '4', apk)
        label = artifact_name(flavor, build_type, version)
        record = {'filename': label, 'bytes': apk.stat().st_size, 'sha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
                  'applicationId': manifest('application-id'), 'applicationLabel': labels[0], 'launcherLabel': launchers[0],
                  'versionName': version, 'versionCode': int(manifest('version-code')),
                  'minSdk': int(manifest('min-sdk')), 'targetSdk': int(manifest('target-sdk')),
                  'debuggable': manifest('debuggable') == 'true', 'signing': args.mode,
                  'buildType': build_type, 'flavor': flavor, 'signerCertificateSha256': certificates,
                  'signatureVerified': True, 'zipIntegrityVerified': True, 'alignmentVerified': True,
                  'permissions': sorted(permissions)}
        if (record['minSdk'], record['targetSdk']) != (26, 35):
            raise SystemExit('Unexpected SDK levels')
        validate_apk_identity(record, flavor, build_type, args.mode, code, base_version)
        verified.append((apk, record, signature))
    if len({tuple(r['signerCertificateSha256']) for _, r, _ in verified}) != 1:
        raise SystemExit('All APKs must use one retained identity (or one explicitly disposable identity).')
    # Copy only after the entire artifact set passes.
    args.output_dir.mkdir(parents=True, exist_ok=True)
    rows = []
    for apk, record, signature in verified:
        shutil.copyfile(apk, args.output_dir / record['filename'])
        (args.output_dir / f"{record['filename']}.signature.txt").write_text(signature + '\n')
        rows.append(f"| {record['filename']} | {record['applicationId']} | {record['bytes']} | `{record['sha256']}` |")
        report['apks'].append(record)
    # Cover metadata/signature reports too, since later gates consume downloaded reports.
    (args.output_dir / 'BUILD_RESULTS.json').write_text(json.dumps(report, indent=2) + '\n')
    summary = (f'# PsyZine build results\n\nCommit: `{commit}`; working tree dirty: `{dirty}`.\n\n'
               f'Signing mode: **{args.mode}**; publication allowed: **{report["publicationAllowed"]}**.\n\n'
               'Actual Gradle build, required unit suites and lint tasks succeeded. Signatures, pinned identities '
               '(except explicitly unpublished builds), ZIP integrity, alignment, package IDs, labels, versions, '
               'debuggable flags, permissions and SDK levels verified. Device installation is a separate check.\n\n'
               '| APK | Application ID | Bytes | SHA-256 |\n| --- | --- | ---: | --- |\n' + '\n'.join(rows) + '\n')
    (args.output_dir / 'BUILD_RESULTS.md').write_text(summary)
    files = sorted(p for p in args.output_dir.iterdir() if p.is_file() and p.name != 'SHA256SUMS')
    (args.output_dir / 'SHA256SUMS').write_text(''.join(f'{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n' for p in files))
    print(summary)


if __name__ == '__main__':
    main()
