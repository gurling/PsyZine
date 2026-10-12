#!/usr/bin/env python3
"""Decode the actual README link and test exact Obtainium selection. GPL-3.0-or-later."""
import argparse
import json
from pathlib import Path
import re
from urllib.parse import parse_qs, unquote, urlparse
from release_policy import VARIANTS, read_version, expected_name, artifact_name, PROJECT


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--assets-dir', type=Path)
    parser.add_argument('--releases-json', type=Path, help='Optional live GitHub release response to test all historical assets')
    args = parser.parse_args()
    config = json.loads((PROJECT / 'config/obtainium.json').read_text())
    link = re.search(r'\[Add PsyZine to Obtainium\]\(([^)]+)\)', (PROJECT / 'README.md').read_text()).group(1)
    decoded = parse_qs(urlparse(link).query)['r'][0]
    if not decoded.startswith('obtainium://app/'):
        raise ValueError('Invalid Obtainium deep link')
    payload = json.loads(unquote(decoded[len('obtainium://app/'):]))
    if payload != config:
        raise ValueError('README link and canonical Obtainium configuration disagree')
    settings = json.loads(payload['additionalSettings'])
    if payload['id'] != 'app.psyzine.debug' or settings['includePrereleases'] is not True or settings['versionDetection'] is not False or settings['releaseTitleAsVersion'] is not False:
        raise ValueError('Wrong package or release tracking settings')
    apk_filter = re.compile(settings['apkFilterRegEx'])
    title_filter = re.compile(settings['filterReleaseTitlesByRegEx'])
    code, base = read_version()
    name = f'{base}-compat.{code}'
    expected = artifact_name('direct', 'compatibleRelease', name)
    assets = [p.name for p in args.assets_dir.iterdir()] if args.assets_dir else [artifact_name(f, b, expected_name(base, code, b, 'compatibility')) for f, b in VARIANTS]
    assets += ['SHA256SUMS', 'BUILD_RESULTS.json', expected + '.signature.txt', 'PsyZine-source.tar.gz']
    if [a for a in assets if apk_filter.search(a)] != [expected]:
        raise ValueError('Obtainium must select exactly the canonical compatible APK')
    if not title_filter.search(f'PsyZine Compatibility {name}') or any(title_filter.search(title) for title in ['PsyZine 1.0.0 development test build 9', f'PsyZine {base}']):
        raise ValueError('Wrong channel title filtering')
    # Prove next code is tracked and wrong testing/production assets cannot be selected.
    if not apk_filter.search(artifact_name('direct', 'compatibleRelease', f'{base}-compat.{code+1}')):
        raise ValueError('Consecutive compatibility releases cannot be selected')
    if args.releases_json:
        for release in json.loads(args.releases_json.read_text()):
            selected = [a['name'] for a in release['assets'] if apk_filter.search(a['name'])]
            matches_channel = bool(title_filter.search(release['name'] or ''))
            if not matches_channel and selected:
                raise ValueError('Compatibility APK leaked into another release channel')
            if matches_channel and (not release['prerelease'] or len(selected) != 1):
                raise ValueError('Published compatibility release must select exactly one pre-release APK')
    print(f'Decoded Obtainium link selects exactly one APK: {expected}')


if __name__ == '__main__':
    main()
