import unittest
from scripts.audit_rionya_gameplay import audit

class RionyaAuditTests(unittest.TestCase):
    def test_four_player_turns_and_actions(self):
        log = "\n".join([
            "Turn: Turn 1 (Ai(1)-Rionya)",
            "Phase: Ai(1)-Rionya's precombat main",
            "Land: Ai(1)-Rionya played Mountain (123)",
            "Add To Stack: Ai(1)-Rionya cast Crimson Wisps",
            "Turn: Turn 2 (Ai(2)-Aesi)",
            "Turn: Turn 3 (Ai(3)-Betor)",
            "Turn: Turn 4 (Ai(4)-Bello)",
            "Turn: Turn 5 (Ai(1)-Rionya)",
            "Add To Stack: Ai(1)-Rionya triggered Rionya, Fire Dancer targeting [Scourge of Valkas (55)]",
            "Ai(1)-Rionya draws a card",
        ])
        result = audit(log)
        self.assertEqual(result["rionya_turns_seen"], 2)
        self.assertEqual(result["global_turn_last_seen"], 5)
        self.assertEqual(result["unidentified_draw_events"], 1)
        self.assertEqual(result["rionya_copy_target_counts"], {"Scourge of Valkas": 1})
        self.assertEqual(result["events"][0]["rionya_turn"], 1)
        self.assertEqual(result["events"][-1]["rionya_turn"], 2)

if __name__ == "__main__":
    unittest.main()
