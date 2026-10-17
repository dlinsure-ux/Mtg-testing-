#!/usr/bin/env python3
"""Backport Card-Forge/forge PR #11964, preserving upstream fast-path guards."""
from pathlib import Path
import sys
p=Path(sys.argv[1]) if len(sys.argv)>1 else Path("forge/forge-game/src/main/java/forge/game/card/CardState.java")
s=p.read_text()
for typ,field in [("Trigger","triggers"),("StaticAbility","staticAbilities")]:
    anchor=f"    public final FCollectionView<{typ}> get{typ if typ == 'Trigger' else 'StaticAbilitie'}s() {{\n"
    # Explicit method names to avoid fragile inflection
    anchor=("    public final FCollectionView<Trigger> getTriggers() {\n" if typ=="Trigger" else "    public final FCollectionView<StaticAbility> getStaticAbilities() {\n")
    assert s.count(anchor)==1, f"Missing/ambiguous {typ} getter"
    guard=(f"        if ({field}.isEmpty() && cachedKeywords.isEmpty() && card.getChangedCardTraits().isEmpty()\n"
           "                && card.getChangedCardTraitsByText().isEmpty() && !card.hasState(CardStateName.LeftSplit)) {\n"
           "            return FCollection.getEmpty();\n"
           "        }\n")
    s=s.replace(anchor,anchor+guard,1)
p.write_text(s)
print("Backported upstream PR #11964")
