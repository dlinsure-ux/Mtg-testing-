import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "scripts" / "validate_forge_game.py"
NAME = "Bello, Bard of the Brambles - archived complete Moxfield mirror"

def check(lab_winner="opponents/bello.dck", prefix="Turn: Turn 1\n", suffix=""):
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        (root / "smoke-test.log").write_text(f"[Game 1/1] Winner: {lab_winner} | Turns: 17 | Time: 99759ms\n")
        (root / "forge").mkdir()
        (root / "forge" / "forge-sim-debug.log").write_text(prefix + f"Game Outcome: Turn 17\nGame Outcome: Ai(3)-{NAME} has won because all opponents have lost\nGame Result: Game 1 ended in 99759 ms. Ai(3)-{NAME} has won!\n" + suffix)
        result = subprocess.run([sys.executable, str(SCRIPT)], cwd=root, capture_output=True, text=True)
        return result.returncode, result.stdout, result.stderr

class ValidatorTests(unittest.TestCase):
    def test_normal_win(self):
        code, out, err = check()
        self.assertEqual(code, 0, out + err)
    def test_lab_draw_rejected(self):
        code, out, err = check(lab_winner="DRAW")
        self.assertNotEqual(code, 0)
    def test_in_game_timeout_rejected(self):
        code, out, err = check(suffix="java.util.concurrent.TimeoutException\n")
        self.assertNotEqual(code, 0)
    def test_missing_first_turn_rejected(self):
        code, out, err = check(prefix="")
        self.assertNotEqual(code, 0)
    def test_startup_timeout_warning(self):
        code, out, err = check(prefix="java.util.concurrent.TimeoutException\n    at java.base/java.util.concurrent.FutureTask.get(FutureTask.java:204)\n    at forge.ai.AiController.chooseSpellAbilityToPlayFromList(AiController.java:100)\nTurn: Turn 1\n")
        self.assertIn("startup", out)
        self.assertEqual(code, 0, out + err)

    def test_felidar_sovereign_loss_messages_not_winners(self):
        for loser_seats in ((2,), (1, 3)):
            with self.subTest(loser_seats=loser_seats):
                loss_lines = "".join(
                    f"Game Outcome: Ai({seat})-Player {seat} has lost because an opponent has won by spell 'Felidar Sovereign'\n"
                    for seat in loser_seats
                )
                with tempfile.TemporaryDirectory() as tmp:
                    root = Path(tmp)
                    (root / "smoke-test.log").write_text(
                        "[Game 1/1] Winner: opponents/betor.dck | Turns: 19 | Time: 128573ms\n"
                    )
                    (root / "forge").mkdir()
                    (root / "forge" / "forge-sim-debug.log").write_text(
                        "Turn: Turn 1\nGame Outcome: Turn 19\n"
                        + loss_lines
                        + "Game Outcome: Ai(4)-Betor B3 Exact has won due to effect of 'Felidar Sovereign'\n"
                        + "Game Result: Game 1 ended in 128573 ms. Ai(4)-Betor B3 Exact has won!\n"
                    )
                    result = subprocess.run([sys.executable, str(SCRIPT)], cwd=root, capture_output=True, text=True)
                    self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
                    report = json.loads((root / "results" / "validation-report.json").read_text())
                    self.assertEqual(len(report["forge_winning_outcomes"]), 1)
                    self.assertEqual(report["forge_winning_outcomes"][0][0], "4")

    def test_clock_expiry_and_conflicting_winners_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "smoke-test.log").write_text(
                "[Game 1/1] Winner: opponents/winota_ultra_budget.dck | Turns: 13 | Time: 600080ms\\n"
                "Stopping slow match as draw\\n"
            )
            (root / "forge").mkdir()
            (root / "forge" / "forge-sim-debug.log").write_text(
                "Turn: Turn 1\\nGame Outcome: Turn 13\\n"
                "Game Outcome: Ai(3)-Shroofus has won because all opponents have lost\\n"
                "Game Outcome: Ai(4)-Winota has won because all opponents have lost\\n"
                "Game Result: Game 1 ended in 600080 ms. Ai(4)-Winota has won!\\n"
            )
            result = subprocess.run([sys.executable, str(SCRIPT)], cwd=root, capture_output=True, text=True)
            self.assertNotEqual(result.returncode, 0)
            report = json.loads((root / "results" / "validation-report.json").read_text())
            self.assertIn("whole-game timeout/interruption evidence", report["errors"])
            self.assertIn("Forge winners: 2 (expected 1)", report["errors"])

    def test_duplicate_genuine_winner_rejected(self):
        duplicate = f"Game Outcome: Ai(4)-Betor B3 Exact has won because all opponents have lost\n"
        code, out, err = check(suffix=duplicate)
        self.assertNotEqual(code, 0)
        self.assertIn("Forge winners: 2", out)

if __name__ == "__main__":
    unittest.main()
