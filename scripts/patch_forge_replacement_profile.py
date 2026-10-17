#!/usr/bin/env python3
"""Profile combat damage prevention checks in pinned Forge AI without changing outcomes."""
from pathlib import Path
import sys
p=Path(sys.argv[1] if len(sys.argv)>1 else "forge/forge-ai/src/main/java/forge/ai/ComputerUtilCombat.java")
s=p.read_text()
old="""        List<ReplacementEffect> list = game.getReplacementHandler().getReplacementList(
                ReplacementType.DamageDone, repParams, ReplacementLayer.Other);

        for (final ReplacementEffect re : list) {"""
new="""        final long replacementStarted = System.nanoTime();
        List<ReplacementEffect> list = game.getReplacementHandler().getReplacementList(
                ReplacementType.DamageDone, repParams, ReplacementLayer.Other);
        final long replacementElapsed = (System.nanoTime() - replacementStarted) / 1000000L;
        if (replacementElapsed >= 50L) {
            System.err.println("FORGE_REPLACEMENT_PROFILE method=isCombatDamagePrevented elapsed_ms="
                    + replacementElapsed + " effects=" + list.size()
                    + " attacker=" + attacker.getName());
        }

        for (final ReplacementEffect re : list) {"""
assert s.count(old)==1, "Pinned Forge damage replacement code changed"
p.write_text(s.replace(old,new,1))
print("Instrumented damage replacement query (50ms threshold; no decision changes)")
