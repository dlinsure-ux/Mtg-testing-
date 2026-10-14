import json
import unittest
from pathlib import Path

class GoldenPilotSpecTests(unittest.TestCase):
    def test_specification_is_well_formed(self):
        data=json.loads(Path('pilots/lathiel/golden_cases.json').read_text())
        policy=json.loads(Path('pilots/lathiel/v0.1.json').read_text())
        self.assertEqual(data['schema_version'],1)
        self.assertEqual(data['pilot'],policy['pilot'])
        self.assertFalse(data['runtime_assertions_enabled'])
        cases=data['cases']
        self.assertEqual(len({x['id'] for x in cases}),len(cases))
        for case in cases:
            self.assertIn(case['expected']['priority'],policy['priorities']['tutor'])
            self.assertIsInstance(case['state'],dict)

if __name__=='__main__':
    unittest.main()
