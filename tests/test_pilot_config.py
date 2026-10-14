import unittest
from scripts.validate_pilot_config import validate

class PilotSpecTests(unittest.TestCase):
    def test_lathiel_spec_is_valid_but_not_integrated(self):
        d=validate("pilots/lathiel/v0.1.json")
        self.assertFalse(d["runtime_integrated"])
        self.assertEqual(d["status"], "specification_only")

if __name__ == "__main__":
    unittest.main()
