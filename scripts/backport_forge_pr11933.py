#!/usr/bin/env python3
"""Backport upstream Forge PR #11933 (CardProperty fast path), pinned and guarded."""
from pathlib import Path
import sys
p=Path(sys.argv[1]) if len(sys.argv)>1 else Path("forge/forge-game/src/main/java/forge/game/card/CardProperty.java")
s=p.read_text()
old="import forge.card.CardDb;"
assert s.count(old)==1, "CardDb import anchor changed"
s=s.replace(old,old+"\nimport forge.card.CardType;",1)
anchor='        if (property.equals("noName")) {'
assert s.count(anchor)==1, "property dispatch anchor changed"
s=s.replace(anchor,'''        // Upstream Card-Forge/forge PR #11933: short-circuit bare core types.
        if (CardType.isACardType(property) || CardType.isASupertype(property)) {
            return card.getCurrentState().getTypeWithChanges().hasStringType(property);
        }

'''+anchor,1)
p.write_text(s)
print("Backported upstream PR #11933")
