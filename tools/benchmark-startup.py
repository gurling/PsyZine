#!/usr/bin/env python3
"""Comparative am start -W launches on an explicitly seeded synthetic emulator only.
PsyZine, GPL-3.0-or-later. No installs, data clearing or database writes.
"""
import argparse
import json
import math
import os
from pathlib import Path
import re
import statistics
import subprocess
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('serial')
    parser.add_argument('--fixture-confirmed', action='store_true', help='Caller has verified the synthetic upgrade fixture')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if not re.fullmatch(r'emulator-[0-9]+', args.serial) or not args.fixture_confirmed:
        parser.error('A seeded synthetic emulator and explicit fixture confirmation are required; physical devices are forbidden.')
    adb = [str(Path(os.environ['ANDROID_HOME']) / 'platform-tools/adb'), '-s', args.serial]

    def shell(*command):
        return subprocess.check_output(adb + ['shell', *command], text=True)

    if shell('getprop', 'ro.kernel.qemu').strip() != '1':
        parser.error('Not an emulator.')
    package = 'app.psyzine.debug'
    component = package + '/com.isaakhanimann.journal.MainActivity'
    package_info = shell('dumpsys', 'package', package)
    code = re.search(r'versionCode=(\d+)', package_info)
    if not code:
        parser.error('The expected fixture is not installed.')

    def launch(mode='cold'):
        before_pid = shell('pidof', package).strip() if mode == 'warm' else None
        flags = ['-f', '0x10008000'] if mode == 'warm' else []
        # NEW_TASK|CLEAR_TASK recreates the activity without clearing any app data.
        output = shell('am', 'start', '-W', *flags, '-n', component)
        if 'Status: ok' not in output:
            raise RuntimeError('Launch failed: ' + output)
        values = {key: int(value) for key, value in re.findall(r'(ThisTime|TotalTime|WaitTime): (\d+)', output)}
        if mode == 'warm':
            if not before_pid or shell('pidof', package).strip() != before_pid:
                raise RuntimeError('Warm launch did not retain the process: ' + output)
            state = re.search(r'LaunchState: (\w+)', output)
            if state and state[1] != 'WARM':
                raise RuntimeError('Expected actual warm activity recreation: ' + output)
        metric = 'WaitTime' if mode == 'hot' else 'TotalTime'
        if metric not in values:
            raise RuntimeError('Missing actual launch timing: ' + output)
        return dict(values, measurementMilliseconds=values[metric])

    # Exclude migration/first-install work from repeated warm/cold measurements; it has its own upgrade checks.
    for _ in range(3):
        shell('am', 'force-stop', package)
        launch()
    samples = {}
    for mode in ('cold', 'warm', 'hot'):
        records = []
        for sample in range(23):
            if mode == 'cold':
                shell('am', 'force-stop', package)
            elif mode == 'hot':
                shell('input', 'keyevent', 'KEYCODE_HOME')
            record = launch(mode)
            if sample >= 3:
                records.append(record)
            time.sleep(.3)
        totals = sorted(record['measurementMilliseconds'] for record in records)
        samples[mode] = {'metric': 'WaitTime' if mode == 'hot' else 'TotalTime', 'samples': records, 'medianMilliseconds': statistics.median(totals),
                         'p95Milliseconds': totals[math.ceil(len(totals) * .95) - 1]}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps({'serial': args.serial, 'versionCode': int(code[1]),
        'api': shell('getprop', 'ro.build.version.sdk').strip(), 'measurement': 'am start -W; cold/warm activity-recreation TotalTime and hot task-to-front WaitTime, three excluded warmups per mode and 300ms settling between launches; does not establish time to full UI readiness',
        'launches': samples}, indent=2) + '\n')
    print(json.dumps({mode: {key: value for key, value in result.items() if key != 'samples'}
                      for mode, result in samples.items()}))


if __name__ == '__main__':
    main()
