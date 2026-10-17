#!/usr/bin/env python3
"""Backport Forge PR #11933 and avoid LKI/controller lookup for simple type tests.

This is a semantics-preserving ordering optimization: phased-out handling still
runs first, and all non-type property predicates retain the original LKI path.
"""
from pathlib import Path
import sys

def patch(s: str) -> str:
    imp = "import forge.card.CardDb;"
    assert s.count(imp) == 1, "CardDb import anchor changed"
    assert "import forge.card.CardType;" not in s, "Already patched"
    s = s.replace(imp, imp + "\nimport forge.card.CardType;", 1)
    eager = """        // lki can't be null but it does return this
        final Card lki = game.getChangeZoneLKIInfo(card);
        final Player controller = lki.getController();
"""
    assert s.count(eager) == 1, "LKI lookup anchor changed"
    s = s.replace(eager, "", 1)
    anchor = '        if (property.equals("noName")) {'
    assert s.count(anchor) == 1, "Property dispatch anchor changed"
    s = s.replace(anchor, """        // Upstream PR #11933, moved before LKI/controller retrieval.
        // This only tests the current card type and preserves the original
        // phased-out check above. Dynamic predicates still use LKI below.
        if (CardType.isACardType(property) || CardType.isASupertype(property)) {
            return card.getCurrentState().getTypeWithChanges().hasStringType(property);
        }

        // lki can't be null but it does return this
        final Card lki = game.getChangeZoneLKIInfo(card);
        final Player controller = lki.getController();

""" + anchor, 1)
    return s

if __name__ == "__main__":
    p = Path(sys.argv[1]) if len(sys.argv)>1 else Path("forge/forge-game/src/main/java/forge/game/card/CardProperty.java")
    p.write_text(patch(p.read_text()))
    print("Backported PR #11933 with type-test LKI fast path")
