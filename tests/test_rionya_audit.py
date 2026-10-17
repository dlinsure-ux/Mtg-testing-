import unittest
from scripts.audit_rionya_gameplay import audit, parse_snapshots, card_exposure, analyze_gameplay, parse_authoritative_events, authoritative_summary

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
        self.assertIsNone(snapshots[0]["life"])
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

    def test_extended_state_and_dedup(self):
        line = "RIONYA_STATE turn=7 phase=MAIN1 hand=Expedite board=Mountain|Rionya, Fire Dancer life=32 creatures=1"
        result = parse_snapshots(line + "\\n" + line)
        self.assertEqual(len(result), 1)
        self.assertEqual(result[0]["life"], 32)
        self.assertEqual(result[0]["creatures"], 1)

    def test_authoritative_events(self):
        sample = "\n".join([
            "RIONYA_EVENT|type=telemetry_enabled|turn=0|phase=",
            "RIONYA_EVENT|type=zone|turn=0|phase=|player_id=0|player=Rionya|card_id=1|card=Mountain|from=Library|to=Hand",
            "RIONYA_EVENT|type=snapshot|turn=1|phase=MAIN1|boundary=turn_begin|player_id=0|player=Rionya|life=40|hand=Mountain|board=|hand_size=1|battlefield_size=0|creatures=0|lands=0",
            "RIONYA_EVENT|type=zone|turn=1|phase=DRAW|player_id=0|player=Rionya|card_id=2|card=Expedite|from=Library|to=Hand",
            "RIONYA_EVENT|type=player_damage|turn=2|phase=COMBAT_DAMAGE|player_id=0|player=Rionya|card_id=8|card=Goblin|amount=3|combat=true|infect=false",
        ])
        events = parse_authoritative_events(sample)
        summary = authoritative_summary(events)
        self.assertEqual(summary["primary_player_id"], 0)
        self.assertEqual(summary["opening_hand_cards"], ["Mountain"])
        self.assertEqual(summary["exact_draws"], [{"turn": 1, "card": "Expedite"}])
        self.assertEqual(summary["damage_events"][0]["amount"], 3)

if __name__ == "__main__":
    unittest.main()
