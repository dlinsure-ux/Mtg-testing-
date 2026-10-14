import unittest
from scripts.game_progress_watchdog import progress_markers

class ProgressWatchdogTests(unittest.TestCase):
    def test_log_noise_is_not_progress(self):
        a=progress_markers("INFO startup\n")
        b=progress_markers("INFO startup\nDEBUG heartbeat\n")
        self.assertEqual(a,b)

    def test_turn_advancement_is_progress(self):
        a=progress_markers("Turn: Turn 1\n")
        b=progress_markers("Turn: Turn 1\nTurn: Turn 2\n")
        self.assertEqual(a['last_turn'],1)
        self.assertEqual(b['last_turn'],2)

    def test_terminal_result_is_detected(self):
        result=progress_markers("Game Result: Game 1 ended in 1234 ms. Ai(1)-Lathiel has won!\n")
        self.assertTrue(result['terminal_result'])

    def test_representative_forge_log_progression(self):
        log = ("Turn: Turn 1 - Player 1\\n"
               "Turn: Turn 2 - Player 2\\n"
               "Game Outcome: Ai(2)-Hakbal has won because opponents lost\\n"
               "Game Result: Game 1 ended in 1500 ms. Ai(2)-Hakbal has won!\\n")
        result = progress_markers(log)
        self.assertEqual(result['last_turn'],2)
        self.assertTrue(result['terminal_result'])

    def test_repeated_turn_header_counts_as_activity_but_not_advancement(self):
        log = "Turn: Turn 1\\nTurn: Turn 1\\n"
        result = progress_markers(log)
        self.assertEqual(result['last_turn'],1)
        self.assertEqual(result['turn_markers'],2)

    def test_empty_log_has_no_progress(self):
        result=progress_markers("")
        self.assertIsNone(result['last_turn'])
        self.assertFalse(result['terminal_result'])

if __name__=="__main__":
    unittest.main()
