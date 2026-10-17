import tempfile
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
    def test_regression_seed_written_before_directory(self):
        start = self.workflow.index('      - name: Run single diagnostic Forge game')
        prefix, section = self.workflow[:start], self.workflow[start:]
        directory = '            mkdir -p "results/game-$game" "diagnostics/game-$game"\n'
        self.assertEqual(section.count(directory), 1)
        section = section.replace(directory, '', 1)
        marker = '            printf \'%s\\n\' "$FORGE_LAB_REPLAY_SEED" > "results/game-$game/replay-seed.txt"'
        self.assertIn(marker, section)
        section = section.replace(marker, marker + '\n' + directory.rstrip(), 1)
        with self.assertRaises(AssertionError):
            check(prefix + section)
    def test_missing_seed_plumbing_rejected(self):
        with self.assertRaises(AssertionError):
            check(self.workflow.replace('export FORGE_LAB_REPLAY_SEED=', 'export REMOVED_SEED=', 1))

if __name__ == "__main__":
    unittest.main()
