import unittest
from scripts.validate_forge_game import re

class ValidatorRegexTests(unittest.TestCase):
    def test_first_turn(self):
        pattern = r"^(?:Turn: Turn 1\b|Turn: 1\b|Turn 1 \()"
        self.assertIsNotNone(re.search(pattern, "Turn: Turn 1\n", re.M))
    def test_java_timeout(self):
        self.assertIsNotNone(re.search(r"^java\.util\.concurrent\.TimeoutException\s*$", "java.util.concurrent.TimeoutException\n", re.M))
    def test_terminal_turn(self):
        self.assertEqual(re.findall(r"^Game Outcome: Turn (\d+)", "Game Outcome: Turn 17", re.M), ["17"])

if __name__ == "__main__":
    unittest.main()
