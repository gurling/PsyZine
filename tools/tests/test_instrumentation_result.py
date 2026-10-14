"""Publication must not confuse skipped upgrade fixtures with successes. GPL-3.0-or-later."""
import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from instrumentation_result import require_passes


class InstrumentationResultTest(unittest.TestCase):
    def test_executed_successes_are_accepted(self):
        require_passes('INSTRUMENTATION_STATUS_CODE: 0\nOK (1 test)\n', 1)
        require_passes('INSTRUMENTATION_STATUS_CODE: 0\n' * 3 + 'OK (3 tests)\n', 3)

    def test_skips_and_failures_are_rejected_even_with_an_ok_summary(self):
        for code in [-1, -2, -3, -4, -5]:
            with self.subTest(code=code), self.assertRaises(ValueError):
                require_passes(f'INSTRUMENTATION_STATUS_CODE: {code}\nOK (1 test)\n', 1)

    def test_missing_execution_counts_and_aborted_runs_are_rejected(self):
        for result in ['OK (1 test)\n', 'INSTRUMENTATION_STATUS_CODE: 0\nOK (2 tests)\n',
                       'INSTRUMENTATION_STATUS_CODE: 0\nOK (1 test)\nINSTRUMENTATION_FAILED', '']:
            with self.subTest(result=result), self.assertRaises(ValueError):
                require_passes(result, 1)
