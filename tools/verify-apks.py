#!/usr/bin/env python3
"""Verify real Gradle APK outputs and publish sizes/checksums/manifest/signing evidence.

PsyZine, 2026. GPL-3.0-or-later. No APK is fabricated or re-signed here.
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


def run(*args):
    return subprocess.run([str(a) for a in args], check=True, text=True,
                          stdout=subprocess.PIPE, stderr=subprocess.STDOUT).stdout.strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--gradle-log', required=True, type=Path)
    parser.add_argument('--output-dir', default=Path('artifacts/apks'), type=Path)
    args = parser.parse_args()
    project = Path(__file__).resolve().parent.parent
    os.chdir(project)
    evidence = args.gradle_log.read_text()
    if not re.search(r'^BUILD SUCCESSFUL in ', evidence, re.MULTILINE):
        raise SystemExit('No successful Gradle execution found; refusing to publish APK results.')
    tasks = ['assembleDirectDebug', 'assembleDirectRelease', 'assemblePlayDebug', 'assemblePlayRelease',
             'testDirectDebugUnitTest', 'testPlayDebugUnitTest',
             'lintDirectDebug', 'lintDirectRelease', 'lintPlayDebug', 'lintPlayRelease']
    for task in tasks:
        if not re.search(rf'^> Task :app:{task}(?: UP-TO-DATE| FROM-CACHE)?\s*$', evidence, re.MULTILINE):
            raise SystemExit(f'No successful execution evidence for required Gradle task: {task}')
    sdk = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or '')
    build_tools = sdk / 'build-tools' / '35.0.0'
    apkanalyzer = sdk / 'cmdline-tools' / 'latest' / 'bin' / 'apkanalyzer'
    if not apkanalyzer.is_file():
        candidate = shutil.which('apkanalyzer')
        if not candidate:
            raise SystemExit('apkanalyzer is unavailable; install Android command-line tools.')
        apkanalyzer = Path(candidate)
    for tool in [build_tools / 'apksigner', build_tools / 'zipalign', apkanalyzer]:
        if not tool.is_file():
            raise SystemExit(f'Required SDK verification tool is missing: {tool}')
    commit = run('git', 'rev-parse', 'HEAD')
    dirty = bool(run('git', 'status', '--porcelain'))
    report = {'commit': commit, 'workingTreeDirty': dirty,
              'gradleResult': 'BUILD SUCCESSFUL',
              'gradleLogSha256': hashlib.sha256(args.gradle_log.read_bytes()).hexdigest(),
              'deviceInstallation': 'not-run', 'unitTests': {}, 'lint': {}, 'apks': []}
    for flavor in ['Direct', 'Play']:
        results = list((project / f'app/build/test-results/test{flavor}DebugUnitTest').glob('TEST-*.xml'))
        if not results:
            raise SystemExit(f'Unit-test result XML is missing for {flavor}.')
        counts = dict.fromkeys(['tests', 'failures', 'errors', 'skipped'], 0)
        for result in results:
            suite = ET.parse(result).getroot()
            for key in counts:
                counts[key] += int(suite.attrib.get(key, 0))
        if counts['tests'] == 0 or counts['failures'] or counts['errors']:
            raise SystemExit(f'Unit tests did not pass for {flavor}: {counts}')
        report['unitTests'][flavor.lower()] = counts
    for flavor in ['direct', 'play']:
        for build_type in ['Debug', 'Release']:
            variant = flavor + build_type
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
    # Verify all inputs before copying any into the downloadable artifact directory.
    verified = []
    for flavor in ['direct', 'play']:
        for build_type in ['debug', 'release']:
            apk = project / f'app/build/outputs/apk/{flavor}/{build_type}/app-{flavor}-{build_type}.apk'
            if not apk.is_file():
                raise SystemExit(f'Signed Gradle output is missing: {apk}')
            with zipfile.ZipFile(apk) as archive:
                bad = archive.testzip()
                if bad or 'AndroidManifest.xml' not in archive.namelist() or 'classes.dex' not in archive.namelist():
                    raise SystemExit(f'APK archive integrity failed: {apk}, {bad}')
            def manifest(field):
                return run(apkanalyzer, 'manifest', field, apk)
            application_id = manifest('application-id')
            expected = 'app.psyzine' + ('.play' if flavor == 'play' else '') + ('.debug' if build_type == 'debug' else '')
            if application_id != expected:
                raise SystemExit(f'Unexpected application ID: {application_id}, expected {expected}')
            version = manifest('version-name')
            if version not in (['1.0.0'] if build_type == 'debug' else ['1.0.0', '1.0.0-dev']):
                raise SystemExit(f'Unexpected APK version: {version}')
            version_code = int(manifest('version-code'))
            min_sdk, target_sdk = int(manifest('min-sdk')), int(manifest('target-sdk'))
            if (version_code, min_sdk, target_sdk) != (1, 26, 35):
                raise SystemExit(f'Unexpected version or SDK levels: {version_code}, {min_sdk}, {target_sdk}')
            permissions = set(manifest('permissions').splitlines())
            if permissions & forbidden:
                raise SystemExit(f'Forbidden APK permissions: {sorted(permissions & forbidden)}')
            signature = run(build_tools / 'apksigner', 'verify', '--verbose', '--print-certs', '--min-sdk-version', '26', apk)
            certificates = re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)$', signature, re.MULTILINE)
            if not certificates:
                raise SystemExit(f'No verified signer certificate in APK: {apk}')
            run(build_tools / 'zipalign', '-c', '-P', '16', '4', apk)
            label = f'PsyZine-{flavor.title()}-{build_type.title()}-v{version}.apk'
            digest = hashlib.sha256(apk.read_bytes()).hexdigest()
            record = {'filename': label, 'path': str((args.output_dir / label).resolve()),
                      'sourcePath': str(apk), 'bytes': apk.stat().st_size, 'sha256': digest,
                      'applicationId': application_id, 'versionName': version, 'versionCode': version_code,
                      'minSdk': min_sdk, 'targetSdk': target_sdk,
                      'signing': 'production' if build_type == 'release' and not version.endswith('-dev') else 'development',
                      'signerCertificateSha256': certificates, 'signatureVerified': True,
                      'zipIntegrityVerified': True, 'alignmentVerified': True,
                      'permissions': sorted(permissions)}
            verified.append((apk, record, signature))
    for build_type in ['debug', 'release']:
        signatures = [r['signerCertificateSha256'] for _, r, _ in verified if f'-{build_type.title()}-' in r['filename']]
        if signatures[0] != signatures[1]:
            raise SystemExit(f'Direct and Play {build_type} signer certificates differ.')
    args.output_dir.mkdir(parents=True, exist_ok=True)
    checksums, rows = [], []
    for apk, record, signature in verified:
        destination = args.output_dir / record['filename']
        shutil.copyfile(apk, destination)
        (args.output_dir / f"{record['filename']}.signature.txt").write_text(signature + '\n')
        checksums.append(f"{record['sha256']}  {record['filename']}\n")
        rows.append(f"| {record['filename']} | {record['applicationId']} | {record['bytes']} | `{record['sha256']}` |")
        report['apks'].append(record)
    (args.output_dir / 'SHA256SUMS').write_text(''.join(checksums))
    (args.output_dir / 'BUILD_RESULTS.json').write_text(json.dumps(report, indent=2) + '\n')
    (args.output_dir / 'BUILD_RESULTS.md').write_text(
        f"# PsyZine build results\n\nCommit: `{commit}`; working tree dirty: `{dirty}`.\n\n"
        "Actual Gradle build, both flavor unit suites and all four lint tasks succeeded. "
        "APK signatures, ZIP integrity, alignment, IDs, versions and SDK levels verified. "
        "Device installation has not been tested by this packaging command. "
        "Versions ending in `-dev` use development signing and are for testing.\n\n"
        "| APK | Application ID | Bytes | SHA-256 |\n| --- | --- | ---: | --- |\n" + '\n'.join(rows) + '\n')
    print((args.output_dir / 'BUILD_RESULTS.md').read_text())


if __name__ == '__main__':
    main()
