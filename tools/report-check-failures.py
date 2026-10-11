#!/usr/bin/env python3
"""Expose failed Android checks as Actions annotations; retain full artifact reports."""
from pathlib import Path
import xml.etree.ElementTree as ET


def error(message):
    escaped = message[:12000].replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
    print(f'::error::{escaped}')


for directory in ['app/build/test-results', 'app/build/outputs/androidTest-results']:
    for report in Path(directory).rglob('*.xml'):
        try:
            root = ET.parse(report).getroot()
        except ET.ParseError:
            continue
        for case in root.iter('testcase'):
            for tag in ['failure', 'error']:
                for failure in case.findall(tag):
                    error(f"{case.get('classname')}.{case.get('name')}: "
                          f"{failure.get('message', '')}\n{failure.text or ''}")

for report in Path('app/build/reports').glob('lint-results-*.xml'):
    for issue in ET.parse(report).getroot().findall('issue'):
        if issue.get('severity') in ['Error', 'Fatal']:
            error(f"{report.name}: {issue.get('id')}: {issue.get('message')}")
