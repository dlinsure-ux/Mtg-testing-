#!/usr/bin/env python3
"""Opt-in Rionya-aware Forge spell selection; fail closed on pinned source drift."""
from pathlib import Path
import sys

ANCHOR = '            for (final SpellAbility sa : ComputerUtilAbility.getOriginalAndAltCostAbilities(all, player)) {'
PATCH = '''            // Rionya-specific decisions. Legality and payment remain Forge-owned.
            if (Boolean.getBoolean("forge.ai.rionyaCommanderFirst")
                    && player.getCardsIn(ZoneType.Command).stream()
                        .anyMatch(c -> "Rionya, Fire Dancer".equals(c.getName()))
                    && game.getStack().isEmpty()
                    && (game.getPhaseHandler().is(PhaseType.MAIN1)
                        || game.getPhaseHandler().is(PhaseType.MAIN2))) {
                for (final SpellAbility candidate : ComputerUtilAbility.getOriginalAndAltCostAbilities(all, player)) {
                    if (!"Rionya, Fire Dancer".equals(candidate.getHostCard().getName())
                            || !candidate.isSpell()
                            || candidate.getHostCard().getZone() == null
                            || candidate.getHostCard().getZone().getZoneType() != ZoneType.Command) continue;
                    candidate.setActivatingPlayer(player);
                    if (canPlayAndPayFor(candidate) == AiPlayDecision.WillPlay) {
                        System.err.println("RIONYA_PILOT commander_cast_priority=true phase="
                                + game.getPhaseHandler().getPhase());
                        return candidate;
                    }
                }
            }
            // Once Rionya is in play, prioritize affordable cantrips and mana
            // rituals in main phase 1, before the beginning-of-combat trigger.
            // Do not burn defensive removal/counters merely for spell count.
            if (Boolean.getBoolean("forge.ai.rionyaCommanderFirst")
                    && player.isCardInPlay("Rionya, Fire Dancer")
                    && game.getPhaseHandler().is(PhaseType.MAIN1)
                    && game.getStack().isEmpty()) {
                final java.util.Set<String> precombat = new java.util.HashSet<>(java.util.Arrays.asList(
                    "Expedite", "Crimson Wisps", "Renegade Tactics", "Warlord's Fury",
                    "Ancestral Anger", "Crash Through", "Rite of Flame",
                    "Pyretic Ritual", "Desperate Ritual", "Seething Song",
                    "Reckless Impulse", "Wrenn's Resolve", "Faithless Looting"));
                for (final SpellAbility candidate : ComputerUtilAbility.getOriginalAndAltCostAbilities(all, player)) {
                    if (!candidate.isSpell() || !precombat.contains(candidate.getHostCard().getName())) continue;
                    candidate.setActivatingPlayer(player);
                    if (canPlayAndPayFor(candidate) == AiPlayDecision.WillPlay) {
                        System.err.println("RIONYA_PILOT precombat_spell=" + candidate.getHostCard().getName());
                        return candidate;
                    }
                }
            }
'''
def patch(src: str) -> str:
    if "RIONYA_PILOT" in src:
        raise ValueError("Already patched")
    if src.count(ANCHOR) != 1:
        raise ValueError("Pinned decision anchor missing or ambiguous")
    return src.replace(ANCHOR, PATCH + ANCHOR, 1)

if __name__ == "__main__":
    p = Path(sys.argv[1])
    p.write_text(patch(p.read_text()))
