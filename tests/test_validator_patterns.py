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
        (root / "lab.log").write_text(f"[Game 1/1] Winner: {lab_winner} | Turns: 17 | Time: 99759ms\n")
        (root / "forge.log").write_text(prefix + f"Game Outcome: Turn 17\nGame Outcome: Ai(3)-{NAME} has won because all opponents have lost\nGame Result: Game 1 ended in 99759 ms. Ai(3)-{NAME} has won!\n" + suffix)
        result = subprocess.run([sys.executable, str(SCRIPT), "--lab", str(root / "lab.log"), "--forge", str(root / "forge.log"), "--report", str(root / "report.json")], capture_output=True, text=True)
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
        code, out, err = check(prefix="java.util.concurrent.TimeoutException\nTurn: Turn 1\n")
        self.assertIn("startup", out)
        self.assertEqual(code, 0, out + err)

if __name__ == "__main__":
    unittest.main()
