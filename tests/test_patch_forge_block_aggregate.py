import unittest
from scripts.patch_forge_block_aggregate import patch

SOURCE = """public class AiBlockController {
    private void makeGoodBlocks(final Combat combat) {
        if (combat == null) { return; }
        while (false) { break; }
    }
}
"""

class BlockAggregatePatchTests(unittest.TestCase):
    def test_preserves_body_and_adds_bounded_aggregate(self):
        patched = patch(SOURCE)
        self.assertIn("if (combat == null) { return; }", patched)
        self.assertIn("FORGE_BLOCK_AGGREGATE method=makeGoodBlocks", patched)
        self.assertIn("(forgeBlockCallCount & 31L) == 0L", patched)
        self.assertIn("FORGE_BLOCK_PROFILE method=makeGoodBlocks", patched)
        self.assertIn("java.util.concurrent.atomic.AtomicLong", patched)

    def test_fails_closed_when_missing_or_already_patched(self):
        with self.assertRaises(ValueError):
            patch("public class AiBlockController {}")
        with self.assertRaises(ValueError):
            patch(patch(SOURCE))

if __name__ == "__main__":
    unittest.main()
