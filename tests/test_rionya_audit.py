import unittest
from scripts.audit_rionya_gameplay import audit, parse_snapshots, card_exposure, analyze_gameplay

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

    def test_hand_and_board_snapshot_exposure(self):
        sample = "RIONYA_STATE turn=5 phase=MAIN1 hand=Crimson Wisps|Scourge of Valkas board=Mountain|Rionya, Fire Dancer"
        snapshots = parse_snapshots(sample)
        self.assertEqual(snapshots[0]["hand"], ["Crimson Wisps", "Scourge of Valkas"])
        self.assertEqual(snapshots[0]["battlefield"], ["Mountain", "Rionya, Fire Dancer"])
        exposure = card_exposure(snapshots, {"Crimson Wisps": 1})
        self.assertEqual(exposure["observed_in_hand_not_cast"], ["Scourge of Valkas"])

    def test_gameplay_timeline_and_unplayed_cards(self):
        snapshots = parse_snapshots("\\n".join([
            "RIONYA_STATE turn=5 phase=MAIN1 hand=Scourge of Valkas|Crimson Wisps board=Mountain|Rionya, Fire Dancer",
            "RIONYA_STATE turn=5 phase=MAIN2 hand=Scourge of Valkas board=Mountain|Rionya, Fire Dancer|Solemn Simulacrum",
        ]))
        events = [{"action": "cast", "card": "Crimson Wisps"}]
        report = analyze_gameplay(snapshots, events)
        self.assertEqual(len(report["board_timeline"]), 2)
        self.assertEqual(report["observed_but_not_played"], ["Scourge of Valkas"])
        self.assertTrue(report["board_timeline"][0]["rionya_on_battlefield"])

if __name__ == "__main__":
    unittest.main()
