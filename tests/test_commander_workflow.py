import unittest
from pathlib import Path
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from check_commander_workflow import check

class WorkflowOrderingTests(unittest.TestCase):
    def setUp(self):
        self.workflow = (Path(__file__).resolve().parents[1] / ".github/workflows/commander-ai-lab-build.yml").read_text()
    def test_actual_workflow(self):
        check(self.workflow)
    def test_missing_game_setup_rejected(self):
        with self.assertRaises((AssertionError, ValueError)):
            check(self.workflow.replace('python3 scripts/prepare_commander_game.py --game "$game"', 'echo SKIPPED', 1))
    def test_setup_after_launch_rejected(self):
        marker='python3 scripts/prepare_commander_game.py --game "$game"'
        start=self.workflow.index('      - name: Run single diagnostic Forge game')
        head, body=self.workflow[:start],self.workflow[start:]
        body=body.replace(marker, 'echo SKIPPED',1)
        launch='timeout 900 xvfb-run'
        body=body.replace(launch, marker+'; '+launch,1)
        with self.assertRaises((AssertionError, ValueError)):
            check(head+body)
    def test_missing_seed_plumbing_rejected(self):
        with self.assertRaises(AssertionError):
            check(self.workflow.replace('export FORGE_LAB_REPLAY_SEED=', 'export REMOVED_SEED=', 1))
if __name__=="__main__":
    unittest.main()
