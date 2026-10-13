#!/usr/bin/env python3
"""Record a two-minute stopped-activity Perfetto trace on a seeded synthetic emulator.
PsyZine, GPL-3.0-or-later. Requires presentation-disabled fixture acceptance.
"""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('serial')
    parser.add_argument('--fixture-confirmed', action='store_true')
    parser.add_argument('--presentation-disabled', action='store_true')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if not re.fullmatch(r'emulator-[0-9]+', args.serial) or not args.fixture_confirmed or not args.presentation_disabled:
        parser.error('A verified synthetic emulator with presentation disabled is required; physical devices are forbidden.')
    adb = [str(Path(os.environ['ANDROID_HOME']) / 'platform-tools/adb'), '-s', args.serial]

    def shell(*command):
        return subprocess.check_output(adb + ['shell', *command], text=True)

    if shell('getprop', 'ro.kernel.qemu').strip() != '1' or int(shell('getprop', 'ro.build.version.sdk')) < 29:
        parser.error('An API29+ emulator is required.')
    args.output.parent.mkdir(parents=True, exist_ok=True)
    package = 'app.psyzine.debug'
    info = shell('dumpsys', 'package', package)
    code = re.search(r'versionCode=(\d+)', info)
    if not code:
        parser.error('The expected seeded package is missing.')
    shell('am', 'start', '-W', '-n', package + '/com.isaakhanimann.journal.MainActivity')

    def hierarchy():
        shell('uiautomator', 'dump', '--compressed', '/sdcard/psyzine-benchmark-ui.xml')
        return ET.fromstring(shell('cat', '/sdcard/psyzine-benchmark-ui.xml'))

    root = hierarchy()
    target = next((node for node in root.iter('node') if node.get('text') == 'Upgrade fixture'), None)
    if target is None:
        parser.error('Synthetic fixture row was not visible; no private journal is allowed.')
    bounds = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', target.get('bounds', ''))
    if not bounds:
        parser.error('Missing observed fixture bounds.')
    x1, y1, x2, y2 = map(int, bounds.groups())
    shell('input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
    root = hierarchy()
    if not any(node.get('content-desc') == 'Edit options' for node in root.iter('node')):
        parser.error('Synthetic detail did not open.')
    xml_file = args.output.with_suffix('.ui.xml')
    xml_file.write_text(ET.tostring(root, encoding='unicode'))
    args.output.with_suffix('.foreground-frames.txt').write_text(shell('dumpsys', 'gfxinfo', package, 'framestats'))
    remote = '/data/misc/perfetto-traces/psyzine-background-' + str(time.time_ns()) + '.pftrace'
    config = '''buffers { size_kb: 32768 fill_policy: RING_BUFFER }
data_sources { config { name: "linux.ftrace" ftrace_config {
  ftrace_events: "sched_switch" ftrace_events: "sched_waking"
  ftrace_events: "task_newtask" ftrace_events: "task_rename"
  atrace_categories: "am" atrace_categories: "wm" atrace_categories: "gfx" atrace_categories: "view"
  atrace_apps: "app.psyzine.debug"
} } }
data_sources { config { name: "linux.process_stats" process_stats_config { scan_all_processes_on_start: true } } }
duration_ms: 125000
write_into_file: true
file_write_period_ms: 5000
'''
    args.output.with_suffix('.pbtxt').write_text(config)
    with args.output.with_suffix('.recording.log').open('w') as log:
        process = subprocess.Popen(adb + ['shell', 'perfetto', '-c', '-', '--txt', '-o', remote],
                                   stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT, text=True)
        process.stdin.write(config)
        process.stdin.close()
        shell('input', 'keyevent', 'KEYCODE_HOME')
        result = process.wait(timeout=155)
        if result:
            raise RuntimeError('Perfetto recording failed; inspect the recording log.')
    subprocess.run(adb + ['pull', remote, str(args.output)], check=True)
    args.output.with_suffix('.stopped-frames.txt').write_text(shell('dumpsys', 'gfxinfo', package, 'framestats'))
    args.output.with_suffix('.metadata.json').write_text(json.dumps({'serial': args.serial,
        'versionCode': int(code[1]), 'presentationDisabled': True, 'durationMilliseconds': 125000,
        'analysisWindow': 'Exclude first 5 seconds; remaining 120 seconds are stopped activity.'}, indent=2) + '\n')
    print('Recorded synthetic two-minute stopped activity: ' + str(args.output))


if __name__ == '__main__':
    main()
