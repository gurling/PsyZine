"""Keep the emulator harness expectations aligned with its selected JUnit tests."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]


class LargeTextHarnessContractTest(unittest.TestCase):
    def setUp(self):
        self.script = (ROOT / 'tools/run-large-text-checks.sh').read_text()
        self.test_class = re.search(r'^test_class=(\S+)$', self.script, re.MULTILINE).group(1)

    def methods(self, class_name):
        source = ROOT / 'app/src/androidTest/java' / (class_name.replace('.', '/') + '.kt')
        text = source.read_text()
        self.assertNotRegex(text, r'@Ignore\b|@RunWith\(Parameterized',
                            'Update the harness inventory for skipped or parameterized tests')
        return set(re.findall(r'@Test\s+fun\s+(\w+)\s*\(', text))

    def class_count(self, selectors):
        count = 0
        for selector in selectors.split(','):
            class_name, _, method = selector.partition('#')
            methods = self.methods(class_name)
            self.assertTrue(methods, f'No test methods found in {class_name}')
            if method:
                self.assertIn(method, methods)
                count += 1
            else:
                count += len(methods)
        return count

    def test_controlled_profile_expects_every_selected_test(self):
        selectors = re.search(r'^controlled_classes="([^"]+)"$', self.script, re.MULTILINE).group(1)
        selectors = selectors.replace('$test_class', self.test_class)
        expected = int(re.search(r'^controlled_expected=(\d+)$', self.script, re.MULTILINE).group(1))
        self.assertEqual(self.class_count(selectors), expected,
                         'The controlled profile must expect all selected JUnit tests')

    def test_platform_profile_expects_existing_selected_methods(self):
        selectors = re.search(r'^platform_classes=(\S+)$', self.script, re.MULTILINE).group(1)
        block = re.search(r'^methods=\(\n(.*?)^\)', self.script, re.MULTILINE | re.DOTALL).group(1)
        methods = block.split()
        self.assertEqual(len(methods), len(set(methods)), 'Duplicate platform method selection')
        selectors += ''.join(f',{self.test_class}#{method}' for method in methods)
        expected = int(re.search(r'^platform_expected=(\d+)$', self.script, re.MULTILINE).group(1))
        self.assertEqual(self.class_count(selectors), expected)


if __name__ == '__main__':
    unittest.main()
