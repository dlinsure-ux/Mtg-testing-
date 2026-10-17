import sys
import tempfile
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from prepare_commander_game import prepare

class PrepareGameTests(unittest.TestCase):
    def test_fixed_replay_and_directories(self):
        with tempfile.TemporaryDirectory() as root:
            p=Path(root)
            a=prepare(3,p/"results",p/"diagnostics","2026101600")
            b=prepare(3,p/"results",p/"diagnostics","2026101600")
            self.assertEqual(a,2026101603)
            self.assertEqual(a,b)
            self.assertTrue((p/"diagnostics/game-3").is_dir())
            self.assertEqual((p/"results/game-3/replay-seed.txt").read_text(),"2026101603\n")
    def test_random_default_and_metadata(self):
        with tempfile.TemporaryDirectory() as root:
            p=Path(root)
            a=prepare(1,p/"results",p/"diagnostics")
            self.assertTrue(0<=a<2**63)
            self.assertIn('"mode": "random"',(p/"results/game-1/replay-metadata.json").read_text())
    def test_invalid_game_fails_closed(self):
        with tempfile.TemporaryDirectory() as root:
            with self.assertRaises(ValueError):
                prepare(0,Path(root)/"results",Path(root)/"diagnostics")
    def test_invalid_seed_fails_closed(self):
        with tempfile.TemporaryDirectory() as root:
            with self.assertRaises(ValueError):
                prepare(1,Path(root)/"results",Path(root)/"diagnostics","bad")
if __name__=="__main__":
    unittest.main()
