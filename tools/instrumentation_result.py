"""Require executed Android test successes, including raw runner status. GPL-3.0-or-later."""
import re


def require_passes(result, expected):
    summary = f'OK ({expected} test' + ('' if expected == 1 else 's') + ')'
    if (expected <= 0 or not re.search(r'^' + re.escape(summary) + r'\s*$', result, re.MULTILINE) or
            re.search(r'INSTRUMENTATION_STATUS_CODE: -\d+\b|FAILURES!!!|INSTRUMENTATION_FAILED', result) or
            len(re.findall(r'INSTRUMENTATION_STATUS_CODE: 0\b', result)) != expected):
        raise ValueError(f'Expected {expected} executed passing tests, without failures or skipped cases')
