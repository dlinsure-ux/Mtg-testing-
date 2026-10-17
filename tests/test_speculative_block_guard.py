import unittest
from scripts.patch_forge_speculative_block_profile import patch, ANCHOR

class WideBoardSpeculativeGuardTests(unittest.TestCase):
    def test_guard_is_opt_in_and_only_in_prediction(self):
        src = "public static int predictNextCombatsRemainingLife() {\n" + ANCHOR + "\n}\n    public static boolean isETBprevented("
        patched = patch(src)
        self.assertEqual(patched.count("forge.ai.speculativeCombatGuard"), 1)
        self.assertIn("forgeSpeculativeAttackers >= 12", patched)
        self.assertEqual(patched.count("block.assignBlockersForCombat(combat, excludedBlockers);"), 1)
        self.assertIn("FORGE_SPECULATIVE_BLOCK_SKIPPED", patched)
        self.assertIn("} else {\n                    block.assignBlockersForCombat", patched)

    def test_refuses_missing_anchor(self):
        with self.assertRaises(ValueError):
            patch("public static int predictNextCombatsRemainingLife() {}\n    public static boolean isETBprevented(")

    def test_refuses_double_patch(self):
        src = "public static int predictNextCombatsRemainingLife() {\n" + ANCHOR + "\n}\n    public static boolean isETBprevented("
        with self.assertRaises(ValueError):
            patch(patch(src))

if __name__ == "__main__":
    unittest.main()
