"""Offline regression tests for Forge profile summarization."""
import unittest
from scripts.summarize_forge_profiles import summarize

class ProfileSummaryTests(unittest.TestCase):
    def test_aggregates_slow_calls_and_contexts(self):
        log = "\n".join([
            "FORGE_BLOCK_PROFILE method=makeGoodBlocks elapsed_ms=100 attackers=2 player=Ai(1)-A",
            "FORGE_BLOCK_PROFILE method=makeGoodBlocks elapsed_ms=300 attackers=4 player=Ai(1)-A",
            "FORGE_COMBAT_PROFILE method=doesSpecifiedCreatureAttackAI elapsed_ms=450 player=Ai(1)-A card=Birds of Paradise",
            "AI_DECISION_TIMEOUT player=Ai(3)-Shroofus turn=28 phase=COMBAT_BEGIN budget_seconds=5 candidates=10",
            "AI_TIMEOUT_STACK forge.ai.AiBlockController.makeGoodBlocks(AiBlockController.java:1)",
        ])
        result = summarize(log)
        block = result["methods"]["block:makeGoodBlocks"]
        self.assertEqual(block["slow_call_count"], 2)
        self.assertEqual(block["inclusive_slow_call_ms"], 400)
        self.assertEqual(block["max_attackers_in_logged_calls"], 4)
        self.assertEqual(result["timeout_by_phase"], {"COMBAT_BEGIN": 1})
        self.assertEqual(result["timeout_stack_frame_count"], 1)

    def test_empty_log_and_nonprofile_lines(self):
        result = summarize("Turn: Turn 1\nGame Result: Game 1 ended\n")
        self.assertEqual(result["methods"], {})
        self.assertEqual(result["timeout_count"], 0)

if __name__ == "__main__":
    unittest.main()
