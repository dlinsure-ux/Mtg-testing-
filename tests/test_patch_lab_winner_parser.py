"""Offline regression tests for the extracted Lab parser patch."""
import unittest
from scripts.patch_lab_winner_parser import OLD, NEW, apply


class LabWinnerParserPatchTests(unittest.TestCase):
    def test_exactly_one_replacement(self):
        result, status = apply("prefix " + OLD + " suffix")
        self.assertEqual(result, "prefix " + NEW + " suffix")
        self.assertEqual(status, "patched")

    def test_idempotent(self):
        result, status = apply("prefix " + NEW + " suffix")
        self.assertEqual(result, "prefix " + NEW + " suffix")
        self.assertEqual(status, "already patched")

    def test_rejects_missing_or_ambiguous_anchor(self):
        for source in ("unrelated", OLD + OLD, NEW + NEW, OLD + NEW):
            with self.subTest(source=source):
                with self.assertRaises(ValueError):
                    apply(source)


if __name__ == "__main__":
    unittest.main()
