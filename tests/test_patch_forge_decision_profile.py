import unittest
from scripts.patch_forge_decision_profile import patch

SOURCE = """class AiController {
        FutureTask<SpellAbility> future = new FutureTask<>(() -> {
            if (true) return null;
            return null;
        });
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
              if (e instanceof TimeoutException) {
              }
        }
}
"""

class DecisionPatchTests(unittest.TestCase):
    def test_scopes_worker_and_preserves_result(self):
        output = patch(SOURCE)
        self.assertIn("ForgeDecisionProfile.begin(player.getName()", output)
        self.assertIn("ForgeDecisionProfile.finish(forgeDecisionScope)", output)
        self.assertIn("return null;", output)
        self.assertIn("} finally {", output)

    def test_rejects_double_patch_and_changed_anchor(self):
        with self.assertRaises(ValueError):
            patch(patch(SOURCE))
        with self.assertRaises(ValueError):
            patch(SOURCE.replace("FutureTask<SpellAbility>", "FutureTask<Object>"))

if __name__ == "__main__":
    unittest.main()
