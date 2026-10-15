"""Fail-closed validator regression cases for buffered timeout ordering."""
import unittest
from tests.test_validator_patterns import check


class TimeoutOrderingTests(unittest.TestCase):
    def test_context_before_first_turn_still_invalid(self):
        code, out, err = check(prefix=(
            "AI_DECISION_TIMEOUT player=Ai(2)-Winota turn=27 phase=MAIN1 budget_seconds=5 candidates=10\n"
            "Turn: Turn 1\n"
        ))
        self.assertNotEqual(code, 0, out + err)
        self.assertIn("in-game AI decision timeouts", out)

    def test_unclassified_startup_timeout_rejected(self):
        code, out, err = check(prefix="java.util.concurrent.TimeoutException\nTurn: Turn 1\n")
        self.assertNotEqual(code, 0, out + err)
        self.assertIn("unclassified startup timeouts", out)


if __name__ == "__main__":
    unittest.main()
