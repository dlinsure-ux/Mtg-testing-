import unittest
from scripts.backport_forge_pr11933 import patch

class TypePropertyFastPathTests(unittest.TestCase):
    def test_type_fast_path_precedes_lki_but_follows_phasing(self):
        source = """import forge.card.CardDb;
class CardProperty {
    void check() {
        final Game game = card.getGame();
        final Combat combat = game.getCombat();
        // lki can't be null but it does return this
        final Card lki = game.getChangeZoneLKIInfo(card);
        final Player controller = lki.getController();
        if (card.isPhasedOut()) {
            if (property.startsWith("phasedOut")) {
                property = property.substring(9);
            } else {
                return false;
            }
        }
        if (property.equals("noName")) {
        }
    }
}"""
        result = patch(source)
        self.assertLess(result.index('card.isPhasedOut()'), result.index('CardType.isACardType(property)'))
        self.assertLess(result.index('CardType.isACardType(property)'), result.index('game.getChangeZoneLKIInfo(card)'))
        self.assertEqual(result.count('game.getChangeZoneLKIInfo(card)'), 1)
        self.assertIn('final Player controller = lki.getController();', result)
        with self.assertRaises(AssertionError):
            patch(result)

if __name__ == "__main__":
    unittest.main()
