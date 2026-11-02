"""Keep installed-upgrade qualification aligned with Room's exported contract."""
import json
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]


class UpgradeSchemaContractTest(unittest.TestCase):
    def test_candidate_schema_matches_room_and_exported_history(self):
        source = ROOT / 'app/src/main/java/com/isaakhanimann/journal/data/room/AppDatabase.kt'
        version = int(re.search(r'version\s*=\s*(\d+)', source.read_text()).group(1))
        script = (ROOT / 'tools/verify-in-place-upgrade.sh').read_text()
        tested_version = int(re.search(r'^candidate_schema=(\d+)$', script, re.MULTILINE).group(1))
        self.assertEqual(version, tested_version, 'Protected upgrades must test the actual candidate schema')
        schemas = ROOT / 'app/schemas/com.isaakhanimann.journal.data.room.AppDatabase'
        for prior in range(1, version + 1):
            schema = json.loads((schemas / f'{prior}.json').read_text())
            self.assertEqual(prior, schema['database']['version'])


if __name__ == '__main__':
    unittest.main()
