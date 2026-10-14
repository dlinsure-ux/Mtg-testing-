import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "scripts" / "audit_series.py"

class SeriesAuditTests(unittest.TestCase):
    def make_report(self, root, game, classification="VALID", errors=None):
        folder = root / f"game-{game}"
        folder.mkdir()
        (folder / "validation-report.json").write_text(json.dumps({
            "classification": classification,
            "errors": errors or [],
            "lab_result": [[f"/tmp/decks/{'lathiel' if game == 1 else 'hakbal'}.dck", "21", "1000"]],
            "in_game_ai_decision_timeouts": 0,
            "whole_game_timeout": False,
            "unsupported_cards": []
        }))

    def run_audit(self, root):
        return subprocess.run([sys.executable, str(SCRIPT), str(root)], capture_output=True, text=True)

    def test_five_valid_games(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            for game in range(1,6): self.make_report(root,game)
            result=self.run_audit(root)
            self.assertEqual(result.returncode,0,result.stderr+result.stdout)
            self.assertEqual(json.loads((root/"series-audit.json").read_text())["lathiel_wins"],1)

    def test_missing_game_fails_closed(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            for game in range(1,5): self.make_report(root,game)
            self.assertNotEqual(self.run_audit(root).returncode,0)

    def test_invalid_game_fails_closed(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            for game in range(1,6): self.make_report(root,game,"INVALID" if game==3 else "VALID")
            self.assertNotEqual(self.run_audit(root).returncode,0)

    def test_in_game_timeout_fails_closed(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            for game in range(1,6): self.make_report(root,game)
            p=root/"game-2"/"validation-report.json"
            d=json.loads(p.read_text()); d["in_game_ai_decision_timeouts"]=1
            p.write_text(json.dumps(d))
            self.assertNotEqual(self.run_audit(root).returncode,0)

if __name__=="__main__":
    unittest.main()
