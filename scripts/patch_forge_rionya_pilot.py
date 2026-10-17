#!/usr/bin/env python3
"""Opt-in Rionya commander-first decision hook for pinned Forge AiController."""
from pathlib import Path
import sys

ANCHOR = '            for (final SpellAbility sa : ComputerUtilAbility.getOriginalAndAltCostAbilities(all, player)) {'
PATCH = '''            // Rionya pilot: attempt a legal commander cast before generic spell ranking.
            // Never bypass Forge legality, timing, or cost checks.
            if (Boolean.getBoolean("forge.ai.rionyaCommanderFirst")
                    && player.getCommanders().stream().anyMatch(c -> "Rionya, Fire Dancer".equals(c.getName()))) {
                for (final SpellAbility candidate : ComputerUtilAbility.getOriginalAndAltCostAbilities(all, player)) {
                    if (!"Rionya, Fire Dancer".equals(candidate.getHostCard().getName())
                            || !candidate.isSpell()) continue;
                    candidate.setActivatingPlayer(player);
                    if (canPlayAndPayFor(candidate) == AiPlayDecision.WillPlay) {
                        System.err.println("RIONYA_PILOT commander_cast_priority=true");
                        return candidate;
                    }
                }
            }
'''
def patch(src):
    if "RIONYA_PILOT" in src:
        raise ValueError("Already patched")
    if src.count(ANCHOR) != 1:
        raise ValueError("Pinned decision anchor missing or ambiguous")
    return src.replace(ANCHOR, PATCH + ANCHOR, 1)

if __name__ == "__main__":
    p = Path(sys.argv[1])
    p.write_text(patch(p.read_text()))
